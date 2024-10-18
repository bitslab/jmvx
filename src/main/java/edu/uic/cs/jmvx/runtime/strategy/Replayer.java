package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;
import edu.uic.cs.jmvx.runtime.*;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

public class Replayer extends Follower{

    // Classloader name -> Class name -> data
    private static HashMap<String, HashMap<String, List<byte[]>>> classesLoaded = new HashMap<>();
    private ObjectInputStream objects;

    private Stack classNames = new Stack();
    private Stack classStreams = new Stack();

    private static HashMap<String, byte[]> clinits = new HashMap<>();
    private static HashMap<String, String[]> manifests = new HashMap<>();
    private static volatile int threadsAlive = 1; //main thread is 1

    private static final String recordingDir = System.getProperty("recordingDir", ".");
    private static ConcurrentHashMap<String, String> jarRedirects = new ConcurrentHashMap<>();

    static {
        Follower.checksumCommunicationEnabled = false;
        Follower.readsFromDisk = false;
        Follower.circularBufferCommunicationEnabled = false;
        populateClassloaderMap(classesLoaded, "recording.classes.dat");
        populateMap(clinits, "recording.clinits.dat");
        populateManifestMap(manifests, "recording.manifests.dat");
        readJarRedirects();
        File recDir = new File(recordingDir);
        try {
            //JMVXRuntime.enter();
            threadsAlive = recDir.list((dir, name) -> !(name.equals("recording.classes.dat") ||
                    name.equals("recording.manifests.dat") ||
                    name.equals("recording.clinits.dat") ||
                    name.endsWith(".jar"))).length;
        }finally {
            //JMVXRuntime.exit();
        }
    }

    private static void readJarRedirects(){
        Path jarPaths = Paths.get(recordingDir, "jarPaths.dat");
        try(ObjectInputStream oIn = new ObjectInputStream(new BufferedInputStream(new FileInputStream(jarPaths.toString())))){
            jarRedirects = (ConcurrentHashMap<String, String>) oIn.readObject();
        } catch (FileNotFoundException e) {
            throw new Error("Can't find " + jarPaths, e);
        } catch (ClassNotFoundException | IOException e) {
            throw new Error("Can't read " + jarPaths, e);
        }
    }

    public static void populateClassloaderMap(HashMap<String, HashMap<String , List<byte[]>>> m, String datFilename) {
        try (FileInputStream fis = new FileInputStream(Paths.get(recordingDir, datFilename).toFile())) {
            BufferedInputStream bis = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream(bis);
            while (bis.available() > 0) {
                String classloaderName = (String) ois.readObject();
                String name = (String) ois.readObject();
                byte[] bytes = (byte[]) ois.readObject();
                HashMap<String, List<byte[]>> clBytes = m.get(classloaderName);
                if (clBytes == null) {
                    clBytes = new HashMap<>();
                    m.put(classloaderName, clBytes);
                }

                List<byte[]> lst = clBytes.get(name);
                if (lst == null) {
                    lst = new LinkedList<>();
                    clBytes.put(name, lst);
                }
                lst.add(bytes);
            }
        } catch (EOFException e){
            //fall through... This occurs when clinits.dat is empty or incomplete. Triggered by the creation of ois.
        } catch (IOException | ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    public static void populateManifestMap(HashMap<String, String[]> m, String datFilename){
        try {
            FileInputStream fis = new FileInputStream(Paths.get(recordingDir, datFilename).toFile());
            BufferedInputStream bis = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream(bis);
            while (bis.available() > 0) {
                String name = (String) ois.readObject();
                String[] items = (String[]) ois.readObject();
                m.putIfAbsent(name, items);
            }
        } catch (EOFException e){
            //fall through... This occurs when clinits.dat is empty or incomplete. Triggered by the creation of ois.
        } catch (IOException | ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    public static void populateMap(HashMap<String, byte[]> m, String datFilename){
        try {
            FileInputStream fis = new FileInputStream(Paths.get(recordingDir, datFilename).toFile());
            BufferedInputStream bis = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream(bis);
            while (bis.available() > 0) {
                String name = (String) ois.readObject();
                byte[] bytes = (byte[]) ois.readObject();
                m.putIfAbsent(name, bytes);
            }
        } catch (EOFException e){
            //fall through... This occurs when clinits.dat is empty or incomplete. Triggered by the creation of ois.
        } catch (IOException | ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    @Override
    public void main() {
        try {
            this.objects = new ObjectInputStream(new BufferedInputStream(new FileInputStream(Paths.get(recordingDir,"recording." + Thread.currentThread().getName() + ".dat").toFile())));
            int tid = this.objects.readInt();
            JMVXRuntime.clock.registerNewThread(tid);

            super.fromCoordinator = this.objects;
        } catch (IOException e) {
            throw new Error(e);
        }
        OutputStream ops;
        AtomicBoolean flag = new AtomicBoolean(false);
        try {
            ops = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    if (flag.get()) {
                        throw new IOException("NOT SUPPORTED");
                    }
                }
            };
            this.toCoordinator = new ObjectOutputStream(ops);
        } catch (IOException e) {
            throw new Error(e);
        }
        flag.set(true);
        super.log = Logger.getLogger(Replayer.class);
    }

    @Override
    public boolean delete(JMVXFile f) {
        boolean ret = true; // = f.$JMVX$delete();
        try {
            Object file = fromCoordinator.readObject();
            if (file instanceof Leader.FileDelete) {
                ret = ((Leader.FileDelete) file).ret;
            } else {
                divergence(Leader.FileDelete.class.getSimpleName(), file.getClass().getSimpleName());
            }
        } catch (ClassNotFoundException | IOException e) {
            throw new Error(e);
        }
        objectCounter++;
        return ret;
    }

    @Override
    public void open(JMVXFileInputStream fis, String name) throws FileNotFoundException {
        Object fileIS;
        try {
            fileIS = fromCoordinator.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new Error(e);
        }

        if (fileIS instanceof Leader.FileInputStreamOpen) {
            Leader.FileInputStreamOpen fiso = (Leader.FileInputStreamOpen) fileIS;
            String n = fiso.name;
            if (!n.equals(name)) {
                log.warn("Potential mismatch! Difference in files opened");
                log.warn("The leader tried to open: " + n);
                log.warn("The follower is trying to open: " + name);
            }
//            fis.$JMVX$open(fiso.name);
        } else if (fileIS instanceof FileNotFoundException) {
            throw ((FileNotFoundException) fileIS);
        } else {
            divergence(Leader.FileInputStreamOpen.class.getSimpleName(), fileIS.getClass().getSimpleName());
        }
        objectCounter++;
    }

    private int loading = 0;

    @Override
    public Class<?> loadClass(JMVXClassLoader loader, String name) throws ClassNotFoundException {
        return this.loadClass(loader, name, true);
    }

    private static ConcurrentHashMap<JMVXClassLoader, ConcurrentHashMap<String, Object>> cache = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Object> empty = new ConcurrentHashMap<>();
    //private int dontLogZip = 0;

    private static final JMVXClassLoader extLoader = (JMVXClassLoader) ClassLoader.getSystemClassLoader().getParent();

    @Override
    public Class<?> loadClass(JMVXClassLoader loader, String name, boolean resolve) throws ClassNotFoundException {
        //matching lock order of Recorder
        synchronized (loader) {
                Object cached = cache.getOrDefault(loader, empty).get(name);
                if (cached != null) {
                    if (cached instanceof Class<?>)
                        return (Class<?>)cached;
                    else
                        throw (ClassNotFoundException) cached;
                }

                loading++;
                classNames.push(name);

                try{
                    if(name.startsWith("edu.uic.cs.jmvx") && ((ClassLoader)loader).getParent() != null){
                        //skip JMVX Classes
                        try{
                            JMVXRuntime.enter();
                            return extLoader.$JMVX$loadClass(name, resolve);
                        }finally{
                            JMVXRuntime.exit();
                        }
                    }

                    ByteArrayInputStream bis;
                    synchronized (Replayer.class) {
                        HashMap<String, List<byte[]>> loadedByLoader = classesLoaded.get(loader.getClass().getName());
                        if (loadedByLoader == null) {
                            JMVXRuntime.enter();
                            try {
                                return loader.$JMVX$loadClass(name, resolve);
                            } finally {
                                JMVXRuntime.exit();
                            }
                        }
                        List<byte[]> lst = loadedByLoader.get(name);
                        if (lst == null) { //handles loading JMVX classes usually
                            JMVXRuntime.enter();
                            try {
                                // Class was not loaded during the recording, attempt to load it now
                                return loader.$JMVX$loadClass(name, resolve);
                            } finally {
                                JMVXRuntime.exit();
                            }
                        }

                        if (lst.size() == 0) //somehow, we've run out of bytes..?!
                            throw new ClassNotFoundException();

                        byte[] bytes = lst.remove(0);

                        JMVXRuntime.enter();
                        bis = new ByteArrayInputStream(bytes);
                    }

                    try {
                        classStreams.push(fromCoordinator);
                        fromCoordinator = new ObjectInputStream(bis);
                        fromCoordinator.readObject();  // Discards the null we wrote first thing to each byte[]
                    } catch (IOException e) {
                        throw new Error(e);
                    } finally {
                        JMVXRuntime.exit();
                    }

                    ConcurrentHashMap<String, Object> m = getClassLoaderCache(loader);

                    try {
                        Class<?> ret = loader.$JMVX$loadClass(name, resolve);
                        m.put(name, ret);
                        return ret;
                    } catch (ClassNotFoundException e) {
                        m.put(name, e);
                        throw e;
                    } finally {
                        JMVXRuntime.enter();
                        try {
                            //classNames.pop();
                            //this.fromCoordinator = this.objects;
                            fromCoordinator = (ObjectInputStream) classStreams.pop();
                        } finally {
                            JMVXRuntime.exit();
                        }
                    }

                } finally {
                    classNames.pop();
                    loading--;
                    //loading = !classNames.empty();//false;
                }
            }
    }

    private ConcurrentHashMap<String, Object> getClassLoaderCache(JMVXClassLoader loader) {
        ConcurrentHashMap<String, Object> m = cache.get(loader);
        if (m == null) {
            m = new ConcurrentHashMap<>();
            cache.put(loader, m);
        }
        return m;
    }

    @Override
    public void clinitStart(String cls) {
        inClinit++;
        ByteArrayInputStream bis;
        byte[] bytes;
        JMVXRuntime.enter();
        try {
            bytes = getClinit(cls);

            //push just in case bytes = null
            //if this happens, we still hit clinitExit, which will pop from this stack
            clinitsStreams.push(fromCoordinator);
            //if(bytes == null){ throw new Error("No clinit..."); } //no bytes, can't alter fromCoordinator
            bis = new ByteArrayInputStream(bytes);
            try {
                fromCoordinator = new ObjectInputStream(bis);
                fromCoordinator.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new Error(e);
            }
        } finally {
            JMVXRuntime.exit();
        }
    }

    @Override
    public void clinitEnd(String cls) {
        fromCoordinator = clinitsStreams.pop();
        inClinit--;
    }

    @Override
    protected byte[] getClinit(String cls) {
        return clinits.get(cls);
    }

    @Override
    public void exitLogic() {
        //This code is in exit logic and not threadExit
        //because threadExit misses main's exit.
        //exitLogic is called for main and threads.
        markDead();
    }

    @Override
    public void systemExit(int status) {
	//exitMain to create same sequence of calls the Recorder does
        JMVXRuntime.exitMain(); //halt(0);
    }

    @Override
    public void seek(JMVXRandomAccessFile f, long pos) throws IOException {
        if (JMVXRuntime.avoidObjectSerialization)
            return;

        super.seek(f, pos);
    }
    @Override
    public int read(JMVXRandomAccessFile f, byte[] bytes, int off, int len) throws IOException {
        if (JMVXRuntime.avoidObjectSerialization) {
            try {
                int ret = readFastInt();
                if (ret == 0) {
                    throw new Error();
                }
                if (ret > 0) {
                    byte[] arr = (byte[]) this.objects.readObject();
                    System.arraycopy(arr, 0, bytes, off, ret);
                }
                return ret;
            } catch (ClassNotFoundException e) {
                throw new Error(e);
            }
        }

        return super.read(f, bytes, off, len);
    }

    private long[] clockCopy = new long[0];
    private byte[] bytes = new byte[0];

    @Override
    public void monitorenter(Object o) {
        assert inClinit == 0;

        if (inClinit > 0) {
            JMVXRuntime.unsafe.monitorEnter(o);
            return;
        }

        if (avoidObjectSerialization) {
            try {
                int length = this.objects.readByte();

                if (length > clockCopy.length) {
                    clockCopy = new long[length];
                    bytes = new byte[length * 8];
                }

                this.objects.read(bytes);
                JMVXRuntime.unsafe.copyMemory(bytes, CircularBuffer.byteOffset, clockCopy, CircularBuffer.longOffset, bytes.length);

                JMVXRuntime.clock.sync(clockCopy, true);
                JMVXRuntime.unsafe.monitorEnter(o);
                JMVXRuntime.clock.increment(null);
            } catch (IOException | ClassCastException e) {
                throw new Error(e);
            }
        } else {
            super.monitorenter(o);
        }
    }

    @Override
    public void wait(Object o, long timeout, int nanos) throws InterruptedException {
        if (avoidObjectSerialization) {
            try {
                // Release the lock while waiting, just like Object.wait does
                int i = 0;
                while(Thread.holdsLock(o)) {
                    i += 1;
                    JMVXRuntime.unsafe.monitorExit(o);
                }

                int length = this.objects.readByte();

                if (length > clockCopy.length) {
                    clockCopy = new long[length];
                    bytes = new byte[length * 8];
                }

                this.objects.read(bytes);
                JMVXRuntime.unsafe.copyMemory(bytes, CircularBuffer.byteOffset, clockCopy, CircularBuffer.longOffset, bytes.length);
                JMVXRuntime.clock.sync(clockCopy, true);
                while (i != 0) {
                    JMVXRuntime.unsafe.monitorEnter(o);
                    i -= 1;
                }
                JMVXRuntime.clock.increment(null);
                return;
            } catch (IOException e) {
                throw new Error(e);
            }
        } else{
            super.wait(o, timeout, nanos);
        }
    }

    private int readFastInt() {
        try {
            return this.readFastInt((byte[]) this.objects.readObject());
        } catch (ClassNotFoundException | IOException e) {
            throw new Error(e);
        }
    }

    private int readFastInt(byte[] bb) {
        return  ((bb[0] & 0xFF) << 24) |
                ((bb[1] & 0xFF) << 16) |
                ((bb[2] & 0xFF) << 8 ) |
                ((bb[3] & 0xFF) << 0 );
    }

    @Override
    public void write(JMVXOutputStream os, int b) throws IOException {
        if (avoidObjectSerialization) {
            // TODO check for divergences
            int written = readFastInt();
            if(written == -Integer.MAX_VALUE && b != -Integer.MAX_VALUE) {
                try {
                    Object e = objects.readObject();
                    if (e instanceof IOException) {
                        throw (IOException) e;
                    } else {
                        divergence("An integer or IOException", e.getClass().getName());
                        throw new Error("Don't know what this object is");
                    }
                } catch (ClassNotFoundException e) {
                    throw new Error(e);
                }
            }

            if (os instanceof FileOutputStream) {
                FileOutputStream fos = (FileOutputStream) os;
                FileDescriptor fd = fos.getFD();
                if (consoleLogsEnabled && (fd == FileDescriptor.out || fd == FileDescriptor.err))
                    os.$JMVX$write(b);
            }
        } else {
            super.write(os, b);
        }
    }

    @Override
    public void write(JMVXOutputStream os, byte[] bytes, int off, int len) throws IOException {
        if (avoidObjectSerialization) {
            try {
                // TODO check for divergences
                byte[] arr = (byte[]) this.objects.readObject();
                if(arr.length == 0 && len > 0){
                    Object e = objects.readObject();
                    if(e instanceof IOException){
                        throw (IOException) e;
                    }else{
                        divergence("An array of bytes or IOException", e.getClass().getName());
                        throw new Error("Don't know what this object is");
                    }
                }
                verifyWriteOb(os, bytes, arr, off, 0, len, arr.length, null);
            } catch (ClassNotFoundException e) {
                throw new Error(e);
            } catch (ClassCastException e) {
                divergence("WriteBII", "Something else [" + e.getMessage() + "]");
            }
        } else {
            super.write(os, bytes, off, len);
        }
    }

    public void connect(JMVXSocket sock, SocketAddress endpoint, int timeout) throws IOException {
        Object obj = receiveObjectFromCoordinator();
        if (obj instanceof Leader.Connect) {
            Leader.Connect cnt = (Leader.Connect) obj;
            //TODO, check if we are trying to connect to the junk socket and then skip some ops?
            //check for divergences
            if (!cnt.endpoint.equals(endpoint) || cnt.timeout != timeout) {
                log.warn("Potential mismatch! Difference in socket connect operation");
                log.warn("Leader wants to connect to " + cnt.endpoint.toString() + " with timeout of " + Integer.toString(cnt.timeout));
                log.warn("Follower wants to connect to " + endpoint.toString() + " with timeout of " + Integer.toString(timeout));
            }

            try {
                //simulate the connection via a loopback!
                synchronized (junkServer) {
                    sock.$JMVX$connect(junkAddress, 0);
                    //sets up internal state of the socket
                    junkServer.accept();
                }
            } catch (Exception e) {
                log.error("Error connecting to junk server");
                e.printStackTrace();
            }
        } else if (obj instanceof IOException) { //throw an exception if we are given one
            //TODO implement more exceptions (connect can throw more than IOException)
            throw (IOException) obj;
        }else{
            Callable<Object> function = () -> {
                JMVXRuntime.connect((Socket)sock, endpoint, timeout);
                return true;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Connect.class.getSimpleName(), obj.getClass().getSimpleName(), obj, function);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
        }
    }

    @Override
    public void bind(JMVXServerSocket serv, SocketAddress endpoint, int backlog) throws IOException {
        Object obj = receiveObjectFromCoordinator();
        if(obj instanceof Leader.Bind){
            Leader.Bind bind = (Leader.Bind)obj;

            //check for divergences
            if(!bind.endpoint.equals(endpoint) || bind.backlog != backlog){
                log.warn("Potential mismatch! Difference in ServerSocket bind operation");
                log.warn("Leader wants to bind to " + bind.endpoint.toString() + " with backlog of " + Integer.toString(bind.backlog));
                log.warn("Follower wants to bind to " + endpoint.toString() + " with backlog of " + Integer.toString(backlog));
            }
            //null means any open port
            serv.$JMVX$bind(null, backlog);

            //switch the file descriptor the ServerSocket uses
            try {
                FileDescriptorUtils.setPort((ServerSocket) serv, ((InetSocketAddress) endpoint).getPort());
            }catch (Exception e){
                e.printStackTrace();
            }
        }else if(obj instanceof IOException){ //throw an exception if we are given one
            //TODO implement more exceptions (connect can throw more than IOException)
            throw (IOException) obj;
        }else{
            Callable<Object> function = () -> {
                JMVXRuntime.bind((ServerSocket)serv, endpoint, backlog);
                return true;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Bind.class.getSimpleName(), obj.getClass().getSimpleName(), obj, function);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
        }
    }

    @Override
    public Socket accept(JMVXServerSocket serv) throws IOException {
        Object obj = receiveObjectFromCoordinator();
        /*
        2 possibilities:
        1: We've diverged and are executing code that should run
        2: The call to accept never terminated! Stay blocked forever

        Case 2 actually happens in H2 client server!
        So we are going with that for now. A better strategy may be to write events
        for AcceptBegin and AcceptEnd. AcceptBegin would read the next item,
        if it throws an error -> block forever.
        If it is something else, diverge (not possible b/c accept must return)
        if it is AcceptEnd, we create a new connection and return it.

        We never have to worry about this in leader follower because the leader's call to accept
        never returns, so the follower blocks, albeit while waiting for an object from the coordinator.
         */
        if(obj instanceof Leader.AcceptBegin){
            obj = receiveObjectFromCoordinator();
            if(obj == null) {
                //hack for now. Recorder may record shutdown hooks
                //so we are going to kill threads that would suspend
                markDead();
                Thread.currentThread().stop();
            }else if(obj instanceof Leader.AcceptEnd){
                //accepted a new connection
                synchronized (junkServer){
                    new Socket(junkAddress.getHostName(), junkAddress.getPort());
                    return junkServer.accept();
                }
            }if(obj instanceof IOException){ //throw an exception if we are given one
                throw (IOException) obj;
            }//else fallthrough to diverge
        }
        //else diverge
        Callable<Object> function = () -> {
            return JMVXRuntime.accept((ServerSocket) serv);
        };

        DivergenceStatus<Object> divergenceStatus = divergence(Leader.Accept.class.getSimpleName(), obj.getClass().getSimpleName(), obj, function);

        if (divergenceStatus.containsException) {
            throw (SecurityException) divergenceStatus.returnValue;
        }

        return (Socket) divergenceStatus.returnValue;
    }

    @Override
    public long currentTimeMillis() {
        if (avoidObjectSerialization) {
            try {
                byte[] b = (byte[]) this.objects.readObject();
                long ret = ((long) b[7] << 56)
                        | ((long) b[6] & 0xff) << 48
                        | ((long) b[5] & 0xff) << 40
                        | ((long) b[4] & 0xff) << 32
                        | ((long) b[3] & 0xff) << 24
                        | ((long) b[2] & 0xff) << 16
                        | ((long) b[1] & 0xff) << 8
                        | ((long) b[0] & 0xff);
                return ret;
            } catch (ClassNotFoundException | IOException e) {
                throw new Error(e);
            }
        } else {
            return super.currentTimeMillis();
        }
    }

    private void halt(int status){
        JMVXRuntime.enter();
        //we don't want to run shutdown hooks
        //so we trigger this here instead
        if(JMVXRuntime.coreDumpAtEnd){
            JMVXRuntime.coreDump();
        }
        /*
        Halt exits the JVM without running shutdown hooks
        Shutdown hooks end up being run with SingleLeaderWithoutCoordinator when recording,
        so we don't have proper logs to replay them. It's dangerous to replay them with
        SingleLeaderWithoutCoordinator in case they operate on files that don't exist.
        So we use halt to bypass it. If we have made it here, we've already replayed what we
        were interested in.
        Note: Jython may have strange behavior with the mechanism because it uses a shutdown hook
        to reset terminal properties (e.g., it runs "stty echo" to turn character echoing BACK ON)
        In my experiments, my terminal was fine (I was still able to see typed characters)
         */
        Runtime.getRuntime().halt(status);
    }

    private void markDead(){
        threadsAlive--;
        if(threadsAlive <= 0){
            halt(0);
        }
    }

    @Override
    public int available(JMVXInputStream is) throws IOException {
        if (JMVXRuntime.avoidObjectSerialization) {
            int ret = readFastInt();
            return ret;
        } else {
            return super.available(is);
        }
    }

    protected Object receiveObjectFromCoordinator() {
        Object o = super.receiveObjectFromCoordinator();
        if(o == null){
            markDead();
        }
        return o;
    }

    @Override
    public void initIDs() {
        //nop, we intercept with other methods
        JMVXRuntime.zipfile.$JMVX$initIDs();
    }

    @Override
    public long getEntry(long jzfile, byte[] name, boolean addSlash) {
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetEntry) {
            Recorder.GetEntry entry = (Recorder.GetEntry) read;
            assert Arrays.equals(entry.name, name);

            if(loading == 0) {
                assert entry.ret < 0;
                return entry.ret;
            }

            assert jzfile > 0;
            long jzentry = JMVXRuntime.zipfile.$JMVX$getEntry(jzfile, name, addSlash);
            return jzentry;
        }else{
            throw new DivergenceError();
        }
    }

    @Override
    public void freeEntry(long jzfile, long jzentry) {
        /*
         * This could actually get called as part of a read!
         * So read is called, intercepted by JMVX and then freeEntry is called,
         * but goes through because the depth > 1. To counteract this, the reentrent Strategy
         * has changed to always call this method when replaying. In otherwords, we also assume
         * we have a freeEntry object to read and always treat it as a nop.
         */
        if(loading > 0 && jzfile > 0 && jzentry > 0){
            JMVXRuntime.zipfile.$JMVX$freeEntry(jzfile, jzentry);
        }
    }

    @Override
    public long getNextEntry(long jzfile, int i) {
        if(loading > 0){
            long jzentry = JMVXRuntime.zipfile.$JMVX$getNextEntry(jzfile, i);
            return jzentry;
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetNextEntry) {
            //TODO error check
            long jzentry = ((Recorder.GetNextEntry) read).ret;
            return jzentry;
        }else{
            throw new DivergenceError();
        }
    }

    @Override
    public void close(long jzfile) {
        if(loading > 0 && jzfile > 0){
            JMVXRuntime.zipfile.$JMVX$close(jzfile);
        }
    }

    @Override
    public long open(String name, int mode, long lastModified, boolean usemmap) throws IOException {
        long ret = -1L;
        //open something we know we have a redirect for
        String p = jarRedirects.get(name);
        if(p != null){
            if(p.startsWith("$REC")) { //redirect to recordingDir
                String toOpen = Paths.get(recordingDir, Paths.get(p).getFileName().toString()).toString();
                ret = JMVXRuntime.zipfile.$JMVX$open(toOpen, mode, lastModified, usemmap);
            } else if(p.equals(name)) //no redirect necessary, e.g., jar belongs to jdk
                ret = JMVXRuntime.zipfile.$JMVX$open(name, mode, lastModified, usemmap);
        }

        if(loading == 0){ //not loading, we need to consume an event
            Object read = receiveObjectFromCoordinator();
            if(read instanceof Recorder.Open){
                Recorder.Open op = (Recorder.Open) read;
                /*
                set ret pointer if we haven't yet
                This handles the case where we haven't opened anything
                e.g., op opens a zipfile and we want to return a "fake" pointer (op.ret)
                 */
                ret = ret > 0? ret: op.ret;
            }else if(read instanceof IOException){
                throw (IOException) read;
            }else{
                throw new DivergenceError();
            }
        }else{
            if(ret < 0) // should have opened already
                throw new DivergenceError();
        }

        return ret;
    }

    @Override
    public int getTotal(long jzfile) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getTotal(jzfile);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetTotal) {
            //TODO error check
            return ((Recorder.GetTotal) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public boolean startsWithLOC(long jzfile) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$startsWithLOC(jzfile);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.StartsWithLOC) {
            //TODO error check
            return ((Recorder.StartsWithLOC) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public int read(long jzfile, long jzentry, long pos, byte[] b, int off, int len) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$read(jzfile, jzentry, pos, b, off, len);
        }
        //handled by streams...
        //should be wrapped with a call to a ZipFileInputStream
        /*Object read = receiveObjectFromCoordinator();
        if(read instanceof byte[]){
            byte[] bytes = (byte[])read;
            int n = Math.min(bytes.length, len);
            System.arraycopy(bytes, 0, b, off, n);
            return n;
        }else{
            throw new DivergenceError();
        }*/
        throw new DivergenceError();
    }

    @Override
    public long getEntryTime(long jzentry) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getEntryTime(jzentry);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetEntryTime) {
            //TODO error check
            return ((Recorder.GetEntryTime) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public long getEntryCrc(long jzentry) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getEntryCrc(jzentry);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetEntryCrc) {
            //TODO error check
            return ((Recorder.GetEntryCrc) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public long getEntryCSize(long jzentry) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getEntryCSize(jzentry);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetEntryCSize) {
            //TODO error check
            return ((Recorder.GetEntryCSize) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public long getEntrySize(long jzentry) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getEntrySize(jzentry);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetEntrySize) {
            //TODO error check
            return ((Recorder.GetEntrySize) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public int getEntryMethod(long jzentry) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getEntryMethod(jzentry);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetEntryMethod) {
            //TODO error check
            return ((Recorder.GetEntryMethod) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public int getEntryFlag(long jzentry) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getEntryFlag(jzentry);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetEntryFlag) {
            //TODO error check
            return ((Recorder.GetEntryFlag) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public byte[] getCommentBytes(long jzfile) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getCommentBytes(jzfile);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetCommentBytes) {
            //TODO error check
            return ((Recorder.GetCommentBytes) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public byte[] getEntryBytes(long jzentry, int type) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getEntryBytes(jzentry, type);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetEntryBytes) {
            //TODO error check
            return ((Recorder.GetEntryBytes) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public String getZipMessage(long jzfile) {
        if(loading > 0){
            return JMVXRuntime.zipfile.$JMVX$getZipMessage(jzfile);
        }
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Recorder.GetZipMessage) {
            //TODO error check
            return ((Recorder.GetZipMessage) read).ret;
        } else {
            throw new DivergenceError();
        }
    }

    @Override
    public String[] getMetaInfEntryNames(JMVXJarFile jarfile) {
        JarFile jf = (JarFile)jarfile;
        if(loading > 0){
            return jarfile.$JMVX$getMetaInfEntryNames();
        }
        String[] miss = new String[0]; //ptr
        String[] ret = manifests.getOrDefault(jf.getName(), miss);
        //null can be returned from this method!
        //so we compare against some object pointer, e.g., miss
        //to detect a divergence
        if(miss == ret){
            throw new DivergenceError();
        }
        return ret;
    }
}
