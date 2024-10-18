package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;
import edu.uic.cs.jmvx.runtime.*;
import edu.uic.cs.jmvx.vectorclock.VectorClock;
import org.apache.log4j.Logger;

import sun.misc.Signal;

import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

public class Recorder extends Leader {

    //used to identify which jars come from JDK
    public static final String JAVA_HOME = System.getenv("JAVA_HOME");
    private static AtomicInteger clockIndex = new AtomicInteger(0);

    private Stack classNames = new Stack();
    private Stack classStreams = new Stack();
    private static ObjectOutputStream classes;
    private static ObjectOutputStream manifests;
    private static Set<String> manifestsRecorded = new HashSet<>();


    //private static ObjectOutputStream clinits;

    private static final String recordingDir = System.getProperty("recordingDir", ".");

    private ObjectOutputStream objects;
    private static ConcurrentHashMap<String, Flushable> streamsToFlush = new ConcurrentHashMap<>();
    /*map of path to jar -> path of jar on replay. Logs where we expect to find code
    JDK classes: path -> path
    Program classes: path -> $REC/path
    All entries with $REC/path will be copied into the recordingDir at the end of the Recording, so they will be
    available for replay. Note the Replayer will resolve redirected pathnames as needed
     */
    private static ConcurrentHashMap<String, String> jarsManifest = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Long, String> openedZips = new ConcurrentHashMap<>();
    private static volatile boolean handlerInit = false;

    private static final int bufferSize = 10 * 1024 * 1024; //10 mb

    static {
	Leader.checksumCommunicationEnabled = false;
        Leader.readsFromDisk = false;
        Leader.circularBufferCommunicationEnabled = false;
        String role = System.getProperty(JMVXRuntime.ROLE_PROP_NAME);
        if(role != null && role.equals(Recorder.class.getSimpleName())) {
            //want to record, safe to alter the filesystem
            //prevents us from truncating these files if Recorder is loaded by another class
            try {
                clinitOut = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(Paths.get(recordingDir, "recording.clinits.dat").toFile()), bufferSize));
                classes = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(Paths.get(recordingDir, "recording.classes.dat").toFile()), bufferSize));
                manifests = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(Paths.get(recordingDir, "recording.manifests.dat").toFile()), bufferSize));
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }

    public Recorder(){
        if(!handlerInit){
            handlerInit = true; //no one else shall run the following code
            Signal.handle(new Signal("USR2"), signal -> {
                flushData();
            });
        }
    }

    @Override
    public void main() {
        int tid = clockIndex.getAndIncrement();
        JMVXRuntime.clock.registerNewThread(tid);

        try {
            this.objects = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(Paths.get(recordingDir, "recording." + Thread.currentThread().getName() + ".dat").toFile()), bufferSize));
            this.objects.writeInt(tid);
            this.objects.flush();
            this.toCoordinator = this.objects;
            synchronized (Recorder.class) {
                streamsToFlush.put(Thread.currentThread().getName(), this.objects);
            }
        } catch (IOException e) {
            throw new Error(e);
        }

        try {
            this.fromCoordinator = new ObjectInputStream(new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("NOT SUPPORTED");
                }
            }) {
                @Override
                protected void readStreamHeader() throws IOException, StreamCorruptedException {
                    return;
                }
            };
        } catch (IOException e) {
            throw new Error(e);
        }

        super.log = Logger.getLogger(Recorder.class);
    }

    @Override
    public boolean delete(JMVXFile f) {
        boolean ret = f.$JMVX$delete();
        sendObjectToCoordinator(new FileDelete(ret));
        objectCounter++;
        return ret;
    }

    @Override
    public void clinitStart(String cls) {
        /*
        Modified to enable record replay to go faster--the locking operations have been removed.
        Leader/Follower strategies needed the locking because the
        events were handled online--the Follower would need to know if an operation belong to a clinit so it could
        shuffle around events. When Replaying, all events exist on disk so we are free to grab them at will.

        This relies on an assumption, namely that threads will not be doing locking
        operations in a clinit, so the vector clock will not be corrupted.
        An assertion has been added to monitorenter to crash the program if ever that
        happens. If the assumption holds, we can skip a lot of locking.
         */
        inClinit++;

        /**
         * Zip files are weird.
         * We may log zip file ops in a clinit or class load, even if we don't care
         * about that class/clinit. E.g., loading a java.* class
         * We don't need to log that, but the zip operations always use the strategy
         * so we can check if they are safe. But we may not need to log anything...
         * (this was the intent of the care stack...)
         */

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JMVXRuntime.enter();
        try {
            clinitsByteStreams.push(bos);
            clinitsStreams.push(toCoordinator);
            try {
                toCoordinator = new ObjectOutputStream(bos);
                toCoordinator.writeObject(null); // This is needed to avoid an "IOException stream active" when calling ObjectOutputStream.reset()
            } catch (IOException e) {
                throw new Error(e);
            }
        } finally {
            JMVXRuntime.exit();
        }
    }

    @Override
    public void clinitEnd(String cls) {
        JMVXRuntime.enter();
        try {
            try {
                synchronized (clinitOut) {
                    clinitOut.writeObject(cls);
                    clinitOut.writeObject(clinitsByteStreams.pop().toByteArray());
                    clinitOut.flush();
                    clinitOut.reset();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } finally {
            JMVXRuntime.exit();
        }
        toCoordinator = clinitsStreams.pop();
        inClinit--;
    }

    @Override
    public void exitLogic() {
        if (!Thread.currentThread().getName().equals(JMVXRuntime.mainThread.getName()))
            return;

        flushData();
    }

    /**
     * Flushes streams and copies jar files
     */
    private static void flushData() {
        for (Flushable f : streamsToFlush.values()) {
            try {
                f.flush();
            } catch (IOException e) {
                continue;
            }
        }

        try (ObjectOutputStream jarPaths = new ObjectOutputStream(new BufferedOutputStream(
                new FileOutputStream(Paths.get(recordingDir, "jarPaths.dat").toString())))){
            jarPaths.writeObject(jarsManifest);
        } catch (IOException e) {
            throw new Error("Failed to create jarPaths.dat", e);
        }

        /*convert marked jars, e.g., "$REC/path/to/jarname.jar to $REC/jarname.jar
          we do this so the recording directory can be changed between record and replay
          A replay can be moved to a new directory without breaking it*/

        jarsManifest.forEach((rawPath, alteredPath) -> {
            if(alteredPath.startsWith("$REC")) { //a jar that we want to copy, not included in jvm
                Path jarPath = Paths.get(rawPath);
                Path recJar = Paths.get(recordingDir).resolve(Paths.get(alteredPath).getFileName());
                try {
                    Files.copy(jarPath, recJar, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new Error("Failed to copy " + jarPath + " to " + recJar, e);
                }
            }
        });
    }

    private int loading = 0;

    @Override
    public Class<?> loadClass(JMVXClassLoader loader, String name) throws ClassNotFoundException {
        return this.loadClass(loader, name, true);
    }

    private final static ArrayList<String> dontLogLoad = new ArrayList<>();

    public static boolean dontLogLoad(String name){
        return dontLogLoad.stream().anyMatch((denied) -> name.startsWith(denied));
    }

    static {
        dontLogLoad.add("edu.uic.cs.jmvx.");
        dontLogLoad.add("java.");
        dontLogLoad.add("sun.");
        dontLogLoad.add("com.sun.");
        dontLogLoad.add("javax.");
    }

    // Cache of loaded classes per each classloader, or exceptions if the class is not found
    // This cache is not needed on the replayer as the classloaders themselves automatically cache results without calling any instrumented methods
    private static ConcurrentHashMap<JMVXClassLoader, ConcurrentHashMap<String, Object>> cache = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Object> empty = new ConcurrentHashMap<>();

    private static final JMVXClassLoader extLoader = (JMVXClassLoader) ClassLoader.getSystemClassLoader().getParent();

    @Override
    public Class<?> loadClass(JMVXClassLoader loader, String name, boolean resolve) throws ClassNotFoundException {
        /*calling a class loader specifically can lead to issues. For instance in Xalan with DacapoClassLoader
        Xalan has an ObjectFactory that manually calls loadClass with a DacapoClassLoaderObject
        DacapoClassLoader.loadClass is synchronized--unlike most others
        The call chain creates a deadlock
        loadClass -> Recorder loadClass -> Dacapo loadClass (blocks)
        Another thread run at the same time can grab the DacapoClassLoader lock and lead to deadlock
        loadClass -> Recorder (blocks)
        */
        synchronized (loader) {

                Object cached = cache.getOrDefault(loader, empty).get(name);
                if (cached != null) {
                    if (cached instanceof Class<?>)
                        return (Class<?>)cached;
                    else
                        throw (ClassNotFoundException) cached;
                }

                this.loading++;
                classNames.push(name);

                try {
                    if(name.startsWith("edu.uic.cs.jmvx") && ((ClassLoader)loader).getParent() != null){
                        //skip JMVX Classes
                        try{
                            JMVXRuntime.enter();
                            return extLoader.$JMVX$loadClass(name, resolve);
                        }finally{
                            JMVXRuntime.exit();
                        }
                    }

                    JMVXRuntime.enter();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    //bos should still be in stack frame and have a correct pointer

                    try {
                        classStreams.push(toCoordinator); //need to preserve this
                        toCoordinator = new ObjectOutputStream(bos);
                        toCoordinator.writeObject(null); // This is needed to avoid an "IOException stream active" when calling ObjectOutputStream.reset()
                    } catch (IOException e) {
                        throw new Error(e);
                    } finally {
                        JMVXRuntime.exit();
                    }

                    Class<?> ret = null;
                    ConcurrentHashMap<String, Object> m = getClassLoaderCache(loader);
                    try {
                        ret = loader.$JMVX$loadClass(name, resolve);
                        m.put(name, ret);
                        return ret;
                    } catch (ClassNotFoundException e) {
                        m.put(name, e);
                        throw e;
                    } finally {

                        JMVXRuntime.enter();
                        try {
                            synchronized (Recorder.class) {
                                toCoordinator.close();
                                toCoordinator = (ObjectOutputStream) classStreams.pop();
                                //classNames.pop(); //done loading
                                //assert s.equals(name); //if this is false I've messed up somewhere
                                this.classes.writeObject(loader.getClass().getName());
                                this.classes.writeObject(name);
                                this.classes.writeObject(bos.toByteArray());
                                this.classes.flush();
                                this.classes.reset();
                            }
                        } catch (IOException e) {
                            throw new Error(e);
                        } finally {
                            JMVXRuntime.exit();
                        }

                    }
                } finally {
                    classNames.pop(); //done loading
                    loading--;
                    //this.loading = !classNames.empty();//false;
                    //super.toCoordinator = this.objects;
                }
        }
    }

    private ConcurrentHashMap<String, Object> getClassLoaderCache(JMVXClassLoader loader) {
        synchronized (cache) {
            ConcurrentHashMap<String, Object> m = cache.get(loader);
            if (m == null) {
                m = new ConcurrentHashMap<>();
                cache.put(loader, m);
            }
            return m;
        }
    }

    @Override
    public void seek(JMVXRandomAccessFile f, long pos) throws IOException {
        if (JMVXRuntime.avoidObjectSerialization) {
            f.$JMVX$seek(pos);
            return;
        }

        super.seek(f, pos);
    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] bytes, int off, int len) throws IOException {
        if (JMVXRuntime.avoidObjectSerialization) {
            int ret = f.$JMVX$read(bytes, off, len);
            writeFastInt(ret);
            if (ret == 0) {
                throw new Error();
            }
            if (ret > 0) {
                byte[] arr = new byte[ret];
                System.arraycopy(bytes, off, arr, 0, ret);
                this.objects.writeObject(arr);
                this.objects.reset();
            }
            return ret;
        }

        return super.read(f, bytes, off, len);
    }

    private long[] clockCopy = new long[0];
    private byte[] bytes = new byte[0];

    @Override
    public void monitorenter(Object o) {
        assert inClinit == 0;

        if (inClinit > 0) {
            // Don't log monitors inside class initializers
            // These should be able to be run by any thread
            // Just get the lock
            JMVXRuntime.unsafe.monitorEnter(o);
            return;
        }

        if (JMVXRuntime.avoidObjectSerialization) {
            VectorClock clock = JMVXRuntime.clock;

            JMVXRuntime.unsafe.monitorEnter(o);

            long[] newClockCopy = clock.increment(clockCopy);
            if (newClockCopy != clockCopy) {
                clockCopy = newClockCopy;
                bytes = new byte[clockCopy.length * 8];
            }

            JMVXRuntime.unsafe.copyMemory(clockCopy, CircularBuffer.longOffset, bytes, CircularBuffer.byteOffset, bytes.length);

            try {
                this.objects.writeByte(clockCopy.length);
                this.objects.write(bytes);
                this.objects.reset();
            } catch (IOException e) {
                throw new Error(e);
            }
        } else {
            super.monitorenter(o);
        }
    }

    @Override
    public void wait(Object o, long timeout, int nanos) throws InterruptedException {
        if(avoidObjectSerialization) {
            VectorClock clock = JMVXRuntime.clock;
            o.wait(timeout, nanos);
            //size of the clockCopy and bytes must change in tandem!
            long[] newClockCopy = clock.increment(clockCopy);
            if (newClockCopy != clockCopy) {
                clockCopy = newClockCopy;
                bytes = new byte[clockCopy.length * 8];
            }

            JMVXRuntime.unsafe.copyMemory(clockCopy, CircularBuffer.longOffset, bytes, CircularBuffer.byteOffset, bytes.length);

            try {
                this.objects.writeByte(clockCopy.length);
                this.objects.write(bytes);
                this.objects.reset();
            } catch (IOException e) {
                throw new Error(e);
            }
        } else {
            super.wait(o, timeout, nanos);
        }
    }

    private void writeFastInt(int i) {
        byte[] bb = new byte[] {
                (byte)(i >>> 24),
                (byte)(i >>> 16),
                (byte)(i >>> 8),
                (byte)i};

        try {
            this.objects.writeObject(bb);
            this.objects.reset();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    @Override
    public void write(JMVXOutputStream os, int b) throws IOException {
        if (avoidObjectSerialization) {
            try {
                os.$JMVX$write(b);
                writeFastInt(b);
            } catch(IOException e){
                //placeholder to signal to look for an exception
                writeFastInt(-Integer.MAX_VALUE);
                sendObjectToCoordinator(e);
                throw e;
            }
        } else {
            super.write(os, b);
        }
    }

    @Override
    public long currentTimeMillis() {

        if (avoidObjectSerialization) {
            long ret = System.currentTimeMillis();
            byte[] b = new byte[] {
                    (byte)  ret,
                    (byte) (ret >> 8),
                    (byte) (ret >> 16),
                    (byte) (ret >> 24),
                    (byte) (ret >> 32),
                    (byte) (ret >> 40),
                    (byte) (ret >> 48),
                    (byte) (ret >> 56)};
            try {
                this.objects.writeObject(b);
                this.objects.reset();
            } catch (IOException e) {
                throw new Error(e);
            }
            return ret;
        } else {
            return super.currentTimeMillis();
        }
    }

    @Override
    public void write(JMVXOutputStream os, byte[] bytes, int off, int len) throws IOException {
        if (avoidObjectSerialization) {
            try {
                os.$JMVX$write(bytes, off, len);
                if (off != 0 || len != bytes.length) {
                    byte[] arr = new byte[len];
                    System.arraycopy(bytes, off, arr, 0, len);
                    this.objects.writeObject(arr);
                } else {
                    this.objects.writeObject(bytes);
                }
                this.objects.reset();
            } catch(IOException e){
                //placeholder to signal to look for an exception
                objects.writeObject(new byte[0]);
                sendObjectToCoordinator(e);
                throw e;
            }
        } else {
            super.write(os, bytes, off, len);
        }
    }


    @Override
    public int available(JMVXInputStream is) throws IOException {
        if (JMVXRuntime.avoidObjectSerialization) {
            try {
                int ret = is.$JMVX$available();
                writeFastInt(ret);
                return ret;
            } catch (IOException e) {
                this.objects.writeObject(e);
                this.objects.reset();
                throw e;
            }
        } else {
            return super.available(is);
        }
    }

    /*
    Socket related methods have logic to forward file descriptors
    Which cannot be done in RR.
    To make up for this, we override those methods and remove some code
     */
    @Override
    public void connect(JMVXSocket sock, SocketAddress endpoint, int timeout) throws IOException {
        //Leader connects normally
        try {
            sock.$JMVX$connect(endpoint, timeout);
            //may need to refactor getFD to prevent exceptions from needing to be added...
            sendObjectToCoordinator(new Connect(endpoint, timeout));
        } catch (IOException e) {
            //TODO catch other exceptions thrown by connect
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void bind(JMVXServerSocket serv, SocketAddress endpoint, int backlog) throws IOException {
        try {
            //bind to the endpoint
            serv.$JMVX$bind(endpoint, backlog);
            sendObjectToCoordinator(new Bind(endpoint, backlog));
        }catch(IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public Socket accept(JMVXServerSocket serv) throws IOException {
        try {
            //get the new client socket
            sendObjectToCoordinator(new AcceptBegin());
            Socket sock = serv.$JMVX$accept();
            sendObjectToCoordinator(new AcceptEnd());
            return sock;
        } catch (IOException e) {
            //TODO catch other exceptions thrown by connect
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    public static boolean loggableJar(String jarName){
        return !(jarName.startsWith(JAVA_HOME) || jarName.contains("JavaMVX"));
    }

    @Override
    public void initIDs() {
        JMVXRuntime.zipfile.$JMVX$initIDs();
    }

    @Override
    public long getEntry(long jzfile, byte[] name, boolean addSlash) {
        long ret = JMVXRuntime.zipfile.$JMVX$getEntry(jzfile, name, addSlash);
        String rawPath = openedZips.get(jzfile);
        if(loading == 0){
            if(rawPath != null)
                jarsManifest.putIfAbsent(rawPath, rawPath);
            sendObjectToCoordinator(new Recorder.GetEntry(jzfile, name, addSlash, -ret));
        }else{ //got an entry during loading, prep it for logging
            if(rawPath != null){
                if(loggableJar(rawPath)) //Program jar, copied to recordingDir later in flushData
                    jarsManifest.put(rawPath, Paths.get("$REC", rawPath).toString()); //$REC/path marks this for copying
                else //JDK jar, path is valid unchanged
                    jarsManifest.put(rawPath, rawPath);
            } //if rawPath is null, then the Jar was opened before JMVX was init'ed and will be okay during replay
            sendObjectToCoordinator(new Recorder.GetEntry(jzfile, name, addSlash, ret));
        }
        return ret;
    }

    @Override
    public void freeEntry(long jzfile, long jzentry) {
        JMVXRuntime.zipfile.$JMVX$freeEntry(jzfile, jzentry);
    }

    @Override
    public long getNextEntry(long jzfile, int i) {
        long ret = JMVXRuntime.zipfile.$JMVX$getNextEntry(jzfile, i);
        if(loading == 0){
            sendObjectToCoordinator(new Recorder.GetNextEntry(jzfile, i, ret));
        }
        return ret;
    }

    @Override
    public void close(long jzfile) {
        JMVXRuntime.zipfile.$JMVX$close(jzfile);
    }

    @Override
    public long open(String name, int mode, long lastModified, boolean usemmap) throws IOException {
        long ret = -1L;
        //TODO check mode and lastModified.
        if(loading > 0){ //use the jar normally for loading
            try{
                ret = JMVXRuntime.zipfile.$JMVX$open(name, mode, lastModified, usemmap);
            }catch(IOException e){
                throw e;
            }
        }else{ //not loading, need to make an event
            try{
                ret = JMVXRuntime.zipfile.$JMVX$open(name, mode, lastModified, usemmap);
                //-ret is used to taint the pointer! Now we can easily track pointers from logfiles in the Replayer
                sendObjectToCoordinator(new Open(name, mode, lastModified, usemmap, -ret));
            }catch(IOException e){
                sendObjectToCoordinator(e);
                throw e;
            }
        }
        /*This could be a jar. We don't know if we will need to copy this to the recording yet.
        Save info the reference the path later when we can tell (getEntry).
         */
        openedZips.put(ret, name);
        return ret;
    }

    @Override
    public int getTotal(long jzfile) {
        int ret = JMVXRuntime.zipfile.$JMVX$getTotal(jzfile);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetTotal(jzfile, ret));
        return ret;
    }

    @Override
    public boolean startsWithLOC(long jzfile) {
        boolean ret = JMVXRuntime.zipfile.$JMVX$startsWithLOC(jzfile);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.StartsWithLOC(jzfile, ret));
        return ret;
    }

    @Override
    public int read(long jzfile, long jzentry, long pos, byte[] b, int off, int len) {
        int ret = JMVXRuntime.zipfile.$JMVX$read(jzfile, jzentry, pos, b, off, len);
        byte[] dat = new byte[ret];
        System.arraycopy(b, off, dat, 0, ret);
        //handled by Streams
        //if(loading == 0)
            //sendObjectToCoordinator(dat);//new ZipRead(jzfile, jzentry, pos, b, off, len, ret));
        //will be wrapped by a call to a ZipFileInputStream
        return ret;
    }

    @Override
    public long getEntryTime(long jzentry) {
        long ret = JMVXRuntime.zipfile.$JMVX$getEntryTime(jzentry);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetEntryTime(jzentry, ret));
        return ret;
    }

    @Override
    public long getEntryCrc(long jzentry) {
        long ret = JMVXRuntime.zipfile.$JMVX$getEntryCrc(jzentry);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetEntryCrc(jzentry, ret));
        return ret;
    }

    @Override
    public long getEntryCSize(long jzentry) {
        long ret = JMVXRuntime.zipfile.$JMVX$getEntryCSize(jzentry);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetEntryCSize(jzentry, ret));
        return ret;
    }

    @Override
    public long getEntrySize(long jzentry) {
        long ret = JMVXRuntime.zipfile.$JMVX$getEntrySize(jzentry);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetEntrySize(jzentry, ret));
        return ret;
    }

    @Override
    public int getEntryMethod(long jzentry) {
        int ret = JMVXRuntime.zipfile.$JMVX$getEntryMethod(jzentry);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetEntryMethod(jzentry, ret));
        return ret;
    }

    @Override
    public int getEntryFlag(long jzentry) {
        int ret = JMVXRuntime.zipfile.$JMVX$getEntryFlag(jzentry);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetEntryFlag(jzentry, ret));
        return ret;
    }

    @Override
    public byte[] getCommentBytes(long jzfile) {
        byte[] ret = JMVXRuntime.zipfile.$JMVX$getCommentBytes(jzfile);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetCommentBytes(jzfile, ret));
        return ret;
    }

    @Override
    public byte[] getEntryBytes(long jzentry, int type) {
        byte[] ret = JMVXRuntime.zipfile.$JMVX$getEntryBytes(jzentry, type);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetEntryBytes(jzentry, type, ret));
        return ret;
    }

    @Override
    public String getZipMessage(long jzfile) {
        String ret = JMVXRuntime.zipfile.$JMVX$getZipMessage(jzfile);
        if(loading == 0)
            sendObjectToCoordinator(new Recorder.GetZipMessage(jzfile, ret));
        return ret;
    }

    @Override
    public String[] getMetaInfEntryNames(JMVXJarFile jarfile) {
        /*
        When we load a class from a jar file for the first time, we may read manifest
        (or what in dacapo is usually a manifest list)
        We store these in a separate file,to be parsed at the start of replay
        because we are not guaranteed that the first class loaded (for a given jar)
        will be the same.
        If we logged this with the load class data, we may miss it during the replay and diverge!
        By storing it separate, we can play around this ordering issue.
         */
        JarFile jf = (JarFile)jarfile;
        if(loading > 0){
            return jarfile.$JMVX$getMetaInfEntryNames();
        }
        synchronized (manifests) {
            String[] ret = jarfile.$JMVX$getMetaInfEntryNames();
            String jarName = jf.getName();
            if(!manifestsRecorded.contains(jarName)){
                try {
                    manifests.writeObject(jarName);
                    manifests.writeObject(ret);
                    manifests.flush();
                    manifests.reset();
                }catch (IOException e){}
                manifestsRecorded.add(jarName);
            }
            //sendObjectToCoordinator(new Recorder.GetMetaInfEntryNames(ret));
            return ret;
        }
    }

    public static class GetEntry implements Serializable {
        private static final long serialVersionUID = 7839962330668504474L;
        public long ret;
        public long jzfile;
        public byte[] name;
        public boolean addSlash;
        public GetEntry(long jzfile, byte[] name, boolean addSlash, long ret) {
            this.ret = ret;
            this.jzfile = jzfile;
            this.name = name;
            this.addSlash = addSlash;
        }
    }

    public static class FreeEntry implements Serializable {
        private static final long serialVersionUID = -7808813808465508112L;
        public long jzfile;
        public long jzentry;
        public FreeEntry(long jzfile, long jzentry) {
            this.jzentry = jzfile;
            this.jzentry = jzentry;
        }
    }

    public static class GetNextEntry implements Serializable {
        private static final long serialVersionUID = 7120384860841771824L;
        public long jzfile;
        public int i;
        public long ret;

        public GetNextEntry(long jzfile, int i, long ret) {
            this.jzfile = jzfile;
            this.i = i;
            this.ret = ret;
        }
    }

    public static class Close implements Serializable {
        private static final long serialVersionUID = 6278188691004185593L;
        public long jzfile;

        public Close(long jzfile) {
            this.jzfile = jzfile;
        }
    }

    public static class Open implements Serializable {
        private static final long serialVersionUID = -4659045869787516973L;
        public String name;
        public int mode;
        public long lastModified;
        public boolean usemmap;
        public long ret;

        public Open(String name, int mode, long lastModified, boolean usemmap, long ret) {
            this.name = name;
            this.mode = mode;
            this.lastModified = lastModified;
            this.usemmap = usemmap;
            this.ret = ret;
        }
    }

    public static class GetTotal implements Serializable {
        private static final long serialVersionUID = 8151887101361260707L;
        public long jzfile;
        public int ret;

        public GetTotal(long jzfile, int ret) {
            this.jzfile = jzfile;
            this.ret = ret;
        }
    }

    public static class StartsWithLOC implements Serializable {
        private static final long serialVersionUID = 4858444449169345403L;
        public long jzfile;
        public boolean ret;

        public StartsWithLOC(long jzfile, boolean ret) {
            this.jzfile = jzfile;
            this.ret = ret;
        }
    }

    public static class ZipRead implements Serializable {
        private static final long serialVersionUID = -1629833004100983667L;
        public long jzfile;
        public long jzentry;
        public long pos;
        public byte[] b;
        public int off;
        public int len;
        public int ret;

        public ZipRead(long jzfile, long jzentry, long pos, byte[] b, int off, int len, int ret) {
            this.jzfile = jzfile;
            this.jzentry = jzentry;
            this.pos = pos;
            this.b = b;
            this.off = off;
            this.len = len;
            this.ret = ret;
        }
    }

    public static class GetEntryTime implements Serializable {
        private static final long serialVersionUID = -7998478397528888949L;
        public long jzentry;
        public long ret;

        public GetEntryTime(long jzentry, long ret) {
            this.jzentry = jzentry;
            this.ret = ret;
        }
    }

    public static class GetEntryCrc implements Serializable {
        private static final long serialVersionUID = 7642058854053133814L;
        public long jzentry;
        public long ret;

        public GetEntryCrc(long jzentry, long ret) {
            this.jzentry = jzentry;
            this.ret = ret;
        }
    }

    public static class GetEntryCSize implements Serializable {
        private static final long serialVersionUID = 1623618058666419499L;
        public long jzentry;
        public long ret;

        public GetEntryCSize(long jzentry, long ret) {
            this.jzentry = jzentry;
            this.ret = ret;
        }
    }

    public static class GetEntrySize implements Serializable {
        private static final long serialVersionUID = -1673714960141495208L;
        public long jzentry;
        public long ret;

        public GetEntrySize(long jzentry, long ret) {
            this.jzentry = jzentry;
            this.ret = ret;
        }
    }

    public static class GetEntryMethod implements Serializable {
        private static final long serialVersionUID = 5407894925840783656L;
        public long jzentry;
        public int ret;

        public GetEntryMethod(long jzentry, int ret) {
            this.jzentry = jzentry;
            this.ret = ret;
        }
    }

    public static class GetEntryFlag implements Serializable {
        private static final long serialVersionUID = -5967129126348540454L;
        public long jzentry;
        public int ret;

        public GetEntryFlag(long jzentry, int ret) {
            this.jzentry = jzentry;
            this.ret = ret;
        }
    }

    public static class GetCommentBytes implements Serializable {
        private static final long serialVersionUID = -557434812706667343L;
        public long jzfile;
        public byte[] ret;

        public GetCommentBytes(long jzfile, byte[] ret) {
            this.jzfile = jzfile;
            this.ret = ret;
        }
    }

    public static class GetEntryBytes implements Serializable {
        private static final long serialVersionUID = 3073033508482597328L;
        public long jzentry;
        public int type;
        public byte[] ret;

        public GetEntryBytes(long jzentry, int type, byte[] ret) {
            this.jzentry = jzentry;
            this.type = type;
            this.ret = ret;
        }
    }

    public static class GetZipMessage implements Serializable {
        private static final long serialVersionUID = -8967876306364293268L;
        public long jzfile;
        public String ret;

        public GetZipMessage(long jzfile, String ret) {
            this.jzfile = jzfile;
            this.ret = ret;
        }
    }

    public static class GetMetaInfEntryNames implements Serializable {
        private static final long serialVersionUID = -3620585514632163682L;
        public final String[] ret;
        public GetMetaInfEntryNames(String[] ret) {
            this.ret = ret;
        }
    }
}
