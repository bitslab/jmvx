package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.coordinate.CoordinatorThread;
import edu.uic.cs.jmvx.runtime.*;
import edu.uic.cs.jmvx.coordinate.Coordinator;
import org.apache.log4j.Logger;
import edu.uic.cs.jmvx.runtime.JMVXSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import edu.uic.cs.jmvx.runtime.JMVXFile;
import edu.uic.cs.jmvx.runtime.JMVXFileOutputStream;
import edu.uic.cs.jmvx.runtime.JMVXInputStream;
import edu.uic.cs.jmvx.runtime.JMVXOutputStream;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import edu.uic.cs.jmvx.vectorclock.VectorClock;
import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;
import edu.uic.cs.jmvx.Utils;
import sun.misc.Unsafe;
import sun.nio.fs.UnixException;
import sun.nio.fs.UnixFileAttributes;
import sun.nio.fs.UnixNativeDispatcher;
import sun.nio.fs.UnixPath;

import java.io.*;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Stack;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import java.util.zip.CRC32;


public class Leader implements JMVXStrategy {
    // TODO add more fields as needed
    /*
    *Ahem* Allow me to explain:
    * toBuffer goes to the ring buffer (if active) otherwise the coordinator
    * toSocket always goes to the coordinator.
    * toCoordinator goes to the fastest communication mechanism
    *   When the ring buffer is active - it goes to the ring buffer
    *   Otherwise, it goes to the coordinator
    *   This variable REALLY should be renamed.
     */
    protected AFUNIXSocket leaderSocket;
    protected ObjectOutputStream toCoordinator;
    protected ObjectInputStream fromCoordinator;
    private ObjectOutputStream toSocketStream;
    private ObjectOutputStream toBufferStream;
    private ObjectInputStream fromSocketStream;
    private ObjectInputStream fromBufferStream;
    protected boolean connected = false;
    protected static Logger log;
    boolean promoteDemoteEnabled = true; // use promoteDemoteEnabled = ture, when testing a specific method
    private static final boolean fastExit = System.getProperty("fastExit", "true").equals("true");

    private static AFUNIXSocket clinitSocket;
    protected static ObjectOutputStream clinitOut;
    //use stacks in case one static init happens to trigger another
    protected Stack<ByteArrayOutputStream> clinitsByteStreams = new Stack();
    protected Stack<ObjectOutputStream> clinitsStreams = new Stack();
    protected int inClinit = 0;

    protected static int objectCounter = 0;

    private CRC32 crc32 = new CRC32();
    CircularBuffer buffer = null;

    protected static boolean checksumCommunicationEnabled = JMVXRuntime.checksumCommunicationEnabled;
    protected static boolean readsFromDisk = JMVXRuntime.readsFromDisk;
    protected static boolean circularBufferCommunicationEnabled = JMVXRuntime.circularBufferCommunicationEnabled;
    protected static boolean avoidObjectSerialization = JMVXRuntime.avoidObjectSerialization;
    protected static boolean useBufferBackedStreams = JMVXRuntime.useBufferBackedStreams;

    private static final AtomicBoolean isExecuting;

    private static final int junkPort = 10000;

    static {
        isExecuting = new AtomicBoolean();

        /**
         * Make and close a server to load libraries necessary for sockets and connections
         * The follower does the same thing, except it needs the server to simulate connections
         * If the code below is omitted, we will get divergences when performing loadLibrary calls
         */
        try {
            JMVXRuntime.enter();
            ServerSocket junk = new ServerSocket(junkPort);
            junk.close();
        } catch (IOException e) { //don't care server isn't used
        }finally {
            JMVXRuntime.exit();
        }
    }

    protected void sendMode() throws IOException {
        //Value is stored in Boolean lockstepMode on Coordinator
        toCoordinator.writeBoolean(false);
        toCoordinator.flush();
        toCoordinator.reset();
    }

    public static AtomicInteger one = new AtomicInteger(2);

    public void reConnect(AFUNIXSocket socket, ObjectOutputStream outputStream, ObjectInputStream inputStream, Logger l) {
        leaderSocket = socket;
        toCoordinator = outputStream;
        fromCoordinator = inputStream;
        log = l;

        // Make sure that new threads use the Leader strategy
        System.setProperty(JMVXRuntime.ROLE_PROP_NAME, "Leader");

        startSwitchingThread();
    }

    private static LeaderSwitchingThread switchingThread = null;

    private static void startSwitchingThread() {
        synchronized (Leader.class) {
            if (switchingThread == null || !switchingThread.isAlive()) {
                switchingThread = new LeaderSwitchingThread();
                switchingThread.start();
            }
        }

    }

    private void writeToCircularBuffer(Object ob) {

        //TODO: Fix performance bottleneck caused by new instance creation of
        //ByteArrayOutputStream and ObjectOutputStream on each write operation.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = null;

        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(ob);
            out.flush();
            byte[] obBytes = bos.toByteArray();
            buffer.writeData(obBytes, true);
        } catch (IOException e) {
            throw new Error(e);
        }
        finally {
            try {
                bos.close();
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }

    @Override
    public void main() {
        // Make sure ObjectOutputStream is fully initialized
        // This may involve reading file /dev/urandom
        try {
            new ObjectOutputStream(new ByteArrayOutputStream(8000)).writeObject(new Exception());
        } catch (IOException e) {
        }
        connect(1234);

        synchronized (Leader.class){
            if (!isExecuting.get()) {
                isExecuting.set(true);
                try {
                    toCoordinator.writeObject(CoordinatorThread.startMessage); //want coordinator thread to start it's switching thread
                    toCoordinator.flush();
                    fromCoordinator.readObject();
                } catch (IOException | ClassNotFoundException e) {
                    throw new Error(e);
                }
                startSwitchingThread();
                LeaderSwitchingThread.leaderThreads.clear();
            }
            LeaderSwitchingThread.addThreads(Thread.currentThread());
        }

        log = Logger.getLogger(Leader.class);

        log.info("Checksum communication enabled: " + checksumCommunicationEnabled);
        log.debug("Reading directly from disk: " + readsFromDisk);
        log.debug("Leader/Follower communication via circular buffer enabled: " + circularBufferCommunicationEnabled);
        log.debug("Avoiding object serialization enabled: " + avoidObjectSerialization);
        log.info("Buffer backed streams enabled: " + useBufferBackedStreams);

        if(circularBufferCommunicationEnabled) {
            buffer = JMVXRuntime.mmapFile(true);
//            this.sendObjectToCoordinator("WaitingForFollower");
//            String status = (String) this.receiveObjectFromCoordinator();
//            if (status.equals("FollowerReady")) {
                buffer.initializeOnBufferData();
                this.sendObjectToCoordinator("BufferInitialized");
//            } else {
//                throw new Error("Buffer initialization failed");
//            }

            if(useBufferBackedStreams) {
                CircularBufferOutputStream bufferOutputStream = new CircularBufferOutputStream(buffer);
                try {
                    toBufferStream = new ObjectOutputStream(new BufferedOutputStream(bufferOutputStream));
                    toBufferStream.flush();
                    toBufferStream.reset();
                } catch (IOException e) {
                    throw new Error(e);
                }
                toSocketStream = toCoordinator;
                toCoordinator = toBufferStream;
                fromSocketStream = fromCoordinator;
                try {
                    this.fromCoordinator = new ObjectInputStream(new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new IOException("The leader cannot read from the ring buffer");
                        }
                    }) {
                        @Override
                        protected void readStreamHeader() throws IOException, StreamCorruptedException {
                            return;
                        }
                    };
                    fromBufferStream = this.fromCoordinator;
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
            else {
                toBufferStream = toCoordinator;
                toSocketStream = toCoordinator;
                fromSocketStream = fromCoordinator;
                fromBufferStream = fromCoordinator;
            }
        }

    }

    private static Object clinit = new Object();

    @Override
    public void clinitStart(String cls){
        JMVXRuntime.enter();
    }

    @Override
    public void clinitEnd(String cls) {
        JMVXRuntime.exit();
    }

    @Deprecated
    public void inDepthClinitStart(String cls) {
        //lock so only 1 thread can clinit at a time
        //this is b/c we use static fields to store data related to streams of all clinits
        JMVXRuntime.unsafe.monitorEnter(Leader.class);
        JMVXRuntime.clock.setInClinit(true);
        inClinit++;

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

            this.monitorenter(clinit);

        } finally {
            JMVXRuntime.exit();
        }
    }

    /*
    NOTE: Recorder.java tied into this logic to write the clinits.dat file. Keep this around in case we need it again
    or want to move it to Recorder.java
     */
    @Deprecated
    public void inDepthClinitEnd(String cls){
        //TODO fix null pointer exception. Can happen if the leader is started before the coordinator.
        //try {
            //toCoordinator.close();
            toCoordinator = clinitsStreams.pop();

            //Clinit dat = new Clinit(cls, clinitsByteStreams.pop().toByteArray());
            JMVXRuntime.enter();
            try {
                synchronized (Leader.class) {
                    try {
                        clinitOut.writeObject(cls);
                        clinitOut.writeObject(clinitsByteStreams.pop().toByteArray());
                        clinitOut.flush();
                        clinitOut.reset();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                JMVXRuntime.exit();
            }

        inClinit--;
        JMVXRuntime.clock.setInClinit(inClinit != 0);
        JMVXRuntime.unsafe.monitorExit(Leader.class);
    }

    private static AtomicInteger threadID = new AtomicInteger(0);

    public void connect(int port) {
        if (!connected) {
            boolean coordinatorReady;
            int clockIndex;
            while (true) {
                try {
                    //uncomment if using inDepthClinit
                    //setupClinitSocket();

                    leaderSocket = AFUNIXSocket.newInstance();
                    leaderSocket.connect(Coordinator.LEADER_COMMS_ADDR);
                    toCoordinator = new ObjectOutputStream(leaderSocket.getOutputStream());

                    int id = threadID.getAndIncrement();

                    toCoordinator.writeObject(Thread.currentThread().getName());
                    toCoordinator.writeInt(id);
                    toCoordinator.flush();

                    fromCoordinator = new ObjectInputStream(leaderSocket.getInputStream());
//                    clockIndex = fromCoordinator.readInt();

                    clockIndex = id;

                    sendMode();
//                    coordinatorReady = fromCoordinator.readBoolean();
//                    while (!coordinatorReady) {
//                        try {
//                            coordinatorReady = fromCoordinator.readBoolean();
//                            Thread.sleep(100);
//                        } catch (InterruptedException ie) {
//                            ie.printStackTrace();
//                        }
//                    }
                    break;
                } catch (IOException e) {
                    //System.out.println("Leader to Coordinator Connection Failed. Retrying...");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }
            JMVXRuntime.clock.registerNewThread(clockIndex);
            connected = true;
        }
    }

    @Deprecated
    private synchronized void setupClinitSocket() throws IOException {
        //already made the socket, skip
        if(clinitSocket == null) {
            //make the clinit Socket
            clinitSocket = AFUNIXSocket.newInstance();
            clinitSocket.connect(Coordinator.LEADER_COMMS_ADDR);
            clinitOut = new ObjectOutputStream(clinitSocket.getOutputStream());

            clinitOut.writeObject("clinit");
            clinitOut.flush();
        }
    }

    /**
     * read 1
     * never used in batik
     *
     * @param is
     * @return
     * @throws IOException
     */
    @Override
    public int read(JMVXInputStream is) throws IOException {
        try {
            Callable<Optional<Object>> function = () -> {
                try {
                    return Optional.of(JMVXRuntime.read((InputStream) is));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(true);
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return (int) result.get();
            }
            int ret = is.$JMVX$read();
            sendObjectToCoordinator(new Read(ret));
            debug(is, ret);
            return ret;
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    /**
     * read 2
     * never used in batik
     *
     * @param is
     * @param bytes
     * @return
     * @throws IOException
     */
    @Override
    public int read(JMVXInputStream is, byte[] bytes) throws IOException {
        try {

            Callable<Optional<Object>> function = () -> {
                try {
                    return Optional.of(JMVXRuntime.read((InputStream) is, bytes));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(true);
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return (int) result.get();
            }
            int ret = is.$JMVX$read(bytes);
            if (readsFromDisk && Utils.isInputStreamFromDisk(is)) {
                int bytesRead = ret;
                debug(is, ret, bytes.length);
                Leader.debug(is, ret, bytes.length);

                int availableBytes = is.$JMVX$available();
                int spaceInBuffer = bytes.length - 0 - bytesRead;
                while (availableBytes > 0 && spaceInBuffer > 0) {
                    bytesRead += is.$JMVX$read(bytes, bytesRead, Math.min(availableBytes, spaceInBuffer));
                    availableBytes = is.$JMVX$available();
                    spaceInBuffer = bytes.length - 0 - bytesRead;
                }
                return bytesRead;
            }
            sendObjectToCoordinator(new ReadB(ret, bytes));
            debug(is, ret, bytes.length);
            return ret;
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    /**
     * read 3
     *
     * @param is
     * @param bytes
     * @param off
     * @param len
     * @return
     * @throws IOException
     */
    @Override
    public int read(JMVXInputStream is, byte[] bytes, int off, int len) throws IOException {
        try {
            Callable<Optional<Object>> function = () -> {
                return Optional.of(JMVXRuntime.read((InputStream) is, bytes, off, len));
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return (int) result.get();
            }

            if (readsFromDisk && Utils.isInputStreamFromDisk(is)) {
                int spaceInBuffer = Math.min(len, bytes.length - off);
                int bytesRead = is.$JMVX$read(bytes, off, len);
                // we take the minimum because if (len + off) > bytes.length, it will try to read more than
                // the capacity of the buffer
                spaceInBuffer -= bytesRead;
                int availableBytes = is.$JMVX$available();

                debug(is, bytesRead, bytes.length, off, len);

                // keep trying to read until we filled the buffer or there aren't more bytes available
                while (availableBytes > 0 && spaceInBuffer > 0) {
                    // again, to avoid buffer overflow, we take the min of the number of bytes available
                    // and the space left in the buffer
                    bytesRead += is.$JMVX$read(bytes, bytesRead + off, Math.min(availableBytes, spaceInBuffer));
                    availableBytes = is.$JMVX$available();
                    spaceInBuffer -= bytesRead;
                }
                return bytesRead;
            }
            int ret = is.$JMVX$read(bytes, off, len);
            if (circularBufferCommunicationEnabled) {
                if (avoidObjectSerialization) {
                    buffer.writeData(bytes, off, ret, true);
                } else {
                    ReadBII rbii = new ReadBII(ret, bytes, off, len);
                    this.writeToCircularBuffer(rbii);
                }
                return ret;
            } else {
                sendObjectToCoordinator(new ReadBII(ret, bytes, off, len));
                log.info("READBII " + objectCounter);
                return ret;
            }
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int available(JMVXInputStream is) throws IOException {
        try {
            int ret = is.$JMVX$available();
            if (JMVXRuntime.avoidObjectSerialization) {
                byte[] b = new byte[]{
                        (byte)(ret >>> 24),
                        (byte)(ret >>> 16),
                        (byte)(ret >>> 8),
                        (byte) ret
                };
                this.sendObjectToCoordinator(b);
            } else {
                Available a = new Available(ret);
                sendObjectToCoordinator(a);
            }
            return ret;
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void close(JMVXOutputStream os) throws IOException {
        try {
            os.$JMVX$close();
        } catch (IOException e) {
            throw e;
        }
    }

    @Override
    public void flush(JMVXOutputStream os) throws IOException {
        try {
            os.$JMVX$flush();
        } catch (IOException e) {
            throw e;
        }
    }

    /**
     * write 1
     *
     * @param os
     * @param b
     * @throws IOException
     */
    @Override
    public void write(JMVXOutputStream os, int b) throws IOException {
        try {
            // trigger the promote/demote process
            Callable<Optional<Object>> function = () -> {
                try {
                    JMVXRuntime.write((OutputStream) os, b);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(new VoidObject());
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return;
            }

            os.$JMVX$write(b);

            if(circularBufferCommunicationEnabled) {
                if(avoidObjectSerialization) {
                    byte data[] = new byte[Integer.BYTES];
                    JMVXRuntime.unsafe.putInt(data, Unsafe.ARRAY_BYTE_BASE_OFFSET, b);
                    buffer.writeData(data, true);
                }
                else {
                    Write writeOb =  new Write(b);
                    this.writeToCircularBuffer(writeOb);
                }
            }
            else {
                sendObjectToCoordinator(new Write(b));
            }
        } catch (IOException e) {
            if(!circularBufferCommunicationEnabled) {
                sendObjectToCoordinator(e);
            }
            throw e;
        }
    }

    /**
     * perform role switching
     * @param function lambda function
     * @param isEnabled default is false
     * @return DoNothingObject if role switching criteria are not matched.
     */
    private Optional<Object> switchRoles(Callable<Optional<Object>> function, boolean isEnabled) {
        if (one.get() == 1 && isEnabled) {
            sendObjectToCoordinator(new SwitchRolesEvent());
            Follower strategy = new Follower();
            strategy.reConnect(leaderSocket, toCoordinator, fromCoordinator, log);
            JMVXRuntime.setStrategy(new ReentrantStrategy(strategy));
            JMVXRuntime.openExistingFilesToNull();
            try {
                return function.call();
            } catch (Exception e) {
                return Optional.of(e);
            }
        }
        return Optional.of(new DoNothingObject());
    }

    /**
     * write 2, never called in batik
     * @param os
     * @param bytes
     * @throws IOException
     */
    @Override
    public void write(JMVXOutputStream os, byte[] bytes) throws IOException {
        byte[] bytesToSend = checksumCommunicationEnabled ? Utils.getChecksum(bytes, crc32) : bytes;
        try {
            Callable<Optional<Object>> function = () -> {
                try {
                    JMVXRuntime.write((OutputStream) os, bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(new VoidObject());
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return;
            }

            os.$JMVX$write(bytes);

            if(circularBufferCommunicationEnabled) {
                if(avoidObjectSerialization) {
                    buffer.writeData(bytesToSend, true);
                }
                else {
                    WriteB wb =  new WriteB(bytesToSend);
                    this.writeToCircularBuffer(wb);
                }
            }
            else {
                sendObjectToCoordinator(new WriteB(bytesToSend));
            }
        } catch (IOException e) {
            if(!circularBufferCommunicationEnabled) {
                sendObjectToCoordinator(e);
            }
            throw e;
        }
    }

    /**
     * write 3
     *
     * @param os
     * @param bytes
     * @param off
     * @param len
     * @throws IOException
     */
    @Override
    public void write(JMVXOutputStream os, byte[] bytes, int off, int len) throws IOException {
        byte[] bytesToSend = checksumCommunicationEnabled ? Utils.getChecksum(bytes, off, len, crc32) : bytes;

        try {
            Callable<Optional<Object>> function = () -> {
                try {
                    JMVXRuntime.write((OutputStream) os, bytes, off, len);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(new VoidObject());
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return;
            }

            os.$JMVX$write(bytes, off, len);

            if(circularBufferCommunicationEnabled) {
                if(avoidObjectSerialization) {
                    buffer.writeData(bytesToSend, off, len, true);
                }
                else {
                    WriteBII wbii =  new WriteBII(bytesToSend, off, len);
                    this.writeToCircularBuffer(wbii);
                }
            }
            else {
                sendObjectToCoordinator(new WriteBII(bytesToSend, off, len));
            }
        } catch (IOException e) {
            if(circularBufferCommunicationEnabled) {
                if(avoidObjectSerialization) {
                    //placeholder to say throw an IOException...
                    buffer.writeData(new byte[0], -1, 0, true);
                }
                else {
                    this.writeToCircularBuffer(e);
                }
            }else{
                sendObjectToCoordinator(e);
            }
            throw e;
        }
    }

    @Override
    public boolean canRead(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.canRead((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (boolean) result.get();
        }

        boolean ret = f.$JMVX$canRead();
        this.sendObjectToCoordinator(new FileCanRead(ret));
        return ret;
    }

    /**
     * never reached in batik
     *
     * @param f
     * @return
     */
    @Override
    public boolean canWrite(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.canWrite((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (boolean) result.get();
        }

        boolean ret = f.$JMVX$canWrite();
        this.sendObjectToCoordinator(new FileCanWrite(ret));
        return ret;
    }

    @Override
    public boolean createNewFile(JMVXFile f) throws IOException {
        try {
            Callable<Optional<Object>> function = () -> {
                try {
                    return Optional.of(JMVXRuntime.createNewFile((File) f));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(new VoidObject());
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return (boolean) result.get();
            }

            boolean ret = f.$JMVX$createNewFile();
            this.sendObjectToCoordinator(new FileCreateNewFile(ret));
            return ret;
        } catch (IOException e) {
            log.warn("createNewFile", e);
            this.sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public boolean delete(JMVXFile f) {
        try {
            boolean ret = (((File) f).exists());
            if (this.useBufferBackedStreams) {
                this.toCoordinator = toSocketStream;
                this.fromCoordinator = fromSocketStream;
            }
            this.sendObjectToCoordinator(new FileDelete(ret));
            this.sendObjectToCoordinator("waiting");
            String status = (String) this.receiveObjectFromCoordinator();

            if (status.equals("synced")) {
                ret = f.$JMVX$delete();
                this.sendObjectToCoordinator(ret);
            } else {
                throw new Error("file delete failed");
            }
            return ret;
        }
        finally {
            if (this.useBufferBackedStreams) {
                this.toCoordinator = toBufferStream;
                this.fromCoordinator = fromBufferStream;
            }
        }

    }

    @Override
    public boolean exists(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.exists((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (boolean) result.get();
        }

        boolean ret = f.$JMVX$exists();
        this.sendObjectToCoordinator(new FileExists(ret));
        return ret;
    }

    /**
     * never used in batik
     *
     * @param f
     * @return
     */
    @Override
    public File getAbsoluteFile(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.getAbsoluteFile((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (File) result.get();
        }

        File ret = f.$JMVX$getAbsoluteFile();
        this.sendObjectToCoordinator(new FileGetAbsoluteFile(ret));
        return ret;
    }

    @Override
    public String getAbsolutePath(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.getAbsolutePath((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (String) result.get();
        }

        String ret = f.$JMVX$getAbsolutePath();
        this.sendObjectToCoordinator(new FileGetAbsolutePath(ret));
        return ret;
    }

    /**
     * never called in batik
     *
     * @param f
     * @return
     * @throws IOException
     */
    @Override
    public File getCanonicalFile(JMVXFile f) throws IOException {
        try {
            Callable<Optional<Object>> function = () -> {
                try {
                    return Optional.of(JMVXRuntime.getCanonicalFile((File) f));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(new VoidObject());
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return (File) result.get();
            }

            File ret = f.$JMVX$getCanonicalFile();
            this.sendObjectToCoordinator(new FileGetCanonicalFile(ret));
            return ret;
        } catch (IOException e) {
            log.warn("getCanonicalFile", e);
            this.sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public String getCanonicalPath(JMVXFile f) throws IOException {
        try {
            Callable<Optional<Object>> function = () -> {
                try {
                    return Optional.of(JMVXRuntime.getCanonicalPath((File) f));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(new VoidObject());
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return (String) result.get();
            }

            String ret = f.$JMVX$getCanonicalPath();
            this.sendObjectToCoordinator(new FileGetCanonicalPath(ret));
            return ret;
        } catch (IOException e) {
            log.warn("getCanonicalPath", e);
            this.sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public String getName(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.getName((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (String) result.get();
        }

        String ret = f.$JMVX$getName();
        this.sendObjectToCoordinator(new FileGetName(ret));
        return ret;
    }

    /**
     * never used in batik
     * @param f
     * @return
     */
    @Override
    public File getParentFile(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.getParentFile((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (File) result.get();
        }

        File ret = f.$JMVX$getParentFile();
        this.sendObjectToCoordinator(new FileGetParentFile(ret));
        return ret;
    }

    @Override
    public String getPath(JMVXFile f) {
        throw new UnsupportedOperationException("Shouldn't get here");
        /*Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.getPath((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (String) result.get();
        }

        String ret = f.$JMVX$getPath();
        this.sendObjectToCoordinator(new FileGetPath(ret));
        return ret;*/
    }

    @Override
    public boolean isDirectory(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.isDirectory((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (boolean) result.get();
        }
        boolean ret = f.$JMVX$isDirectory();
        this.sendObjectToCoordinator(new FileIsDirectory(ret));
        return ret;
    }

    /**
     * never called in batik
     *
     * @param f
     * @return
     */
    @Override
    public long length(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.length((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (long) result.get();
        }

        long ret = f.$JMVX$length();
        this.sendObjectToCoordinator(new FileLength(ret));
        return ret;
    }

    @Override
    public String[] list(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.list((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (String[]) result.get();
        }
        String[] ret = f.$JMVX$list();
        this.sendObjectToCoordinator(new FileList(ret));
        return ret;
    }

    @Override
    public String[] list(JMVXFile f, FilenameFilter filter) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.list((File) f, filter));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (String[]) result.get();
        }
        String[] unfiltered = f.$JMVX$list();
        String[] ret = unfiltered;
        if (ret != null)
            ret = filterFiles(unfiltered, filter);
        //Send the unfiltered so the follower's filter can process them (and cause the same side effects)
        this.sendObjectToCoordinator(new FileListFilter(unfiltered));//luindex filter not serializable
        return ret;
    }

    @Override
    public File[] listFiles(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.listFiles((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (File[]) result.get();
        }
        File[] ret = f.$JMVX$listFiles();
        this.sendObjectToCoordinator(new FileListFiles(ret));
        return ret;
    }

    @Override
    public File[] listFiles(JMVXFile f, FilenameFilter filter) {
        //TODO add code for promote demote
        File[] unfiltered = f.$JMVX$listFiles();
        File[] ret = filterFiles(unfiltered, filter);
        //send the list of unfiltered files so the follower can apply the filter.
        //this is done to capture the side effects caused by the filter!
        this.sendObjectToCoordinator(new FileListFilesFilter(unfiltered));
        return ret;
    }

    public static File[] filterFiles(File[] unfiltered, FilenameFilter filter) {
        ArrayList<File> files = new ArrayList<>();
        for (File file : unfiltered) {
            JMVXFile jf = (JMVXFile) file;
            if ((filter == null) || filter.accept(jf.$JMVX$getParentFile(), jf.$JMVX$getName())){
                files.add(file);
            }
        }
        return files.toArray(new File[files.size()]);
    }

    public static String[] filterFiles(String[] unfiltered, FilenameFilter filter) {
        ArrayList<String> files = new ArrayList<>();
        for (String filename : unfiltered) {
            JMVXFile jf = (JMVXFile) new File(filename);
            if ((filter == null) || filter.accept(jf.$JMVX$getParentFile(), jf.$JMVX$getName())){
                files.add(jf.$JMVX$getName());
            }
        }
        return files.toArray(new String[files.size()]);
    }

    @Override
    public boolean mkdir(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.mkdir((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (boolean) result.get();
        }
        boolean ret = f.$JMVX$mkdir();
        this.sendObjectToCoordinator(new FileMkdir(ret));
        return ret;
    }

    @Override
    public boolean mkdirs(JMVXFile f) {
        boolean ret = f.$JMVX$mkdirs();
        this.sendObjectToCoordinator(new FileMkdirs(ret));
        return ret;
    }

    /**
     * never used in batik
     *
     * @param f
     * @param dest
     * @return
     */
    @Override
    public boolean renameTo(JMVXFile f, File dest) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.renameTo((File) f, dest));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (boolean) result.get();
        }
        boolean ret = f.$JMVX$renameTo(dest);
        this.sendObjectToCoordinator(new FileRenameTo(ret, dest));
        return ret;
    }

    /**
     * never used in batik
     *
     * @param f
     * @return
     */
    @Override
    public boolean setReadOnly(JMVXFile f) {
        Callable<Optional<Object>> function = () -> {
            return Optional.of(JMVXRuntime.setReadOnly((File) f));
        };
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (boolean) result.get();
        }
        boolean ret = f.$JMVX$setReadOnly();
        this.sendObjectToCoordinator(new FileSetReadOnly(ret));
        return ret;
    }

    @Override
    public URL toURL(JMVXFile f) throws MalformedURLException {
        try {
            Callable<Optional<Object>> function = () -> {
                try {
                    return Optional.of(JMVXRuntime.toURL((File) f));
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                return Optional.of(new VoidObject());
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return (URL) result.get();
            }
            URL ret = f.$JMVX$toURL();
            this.sendObjectToCoordinator(new FileToURL(ret));
            return ret;
        } catch (MalformedURLException e) {
            log.warn("toURL", e);
            this.sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long lastModified(JMVXFile f) {
        Callable<Optional<Object>> function = () -> Optional.of(JMVXRuntime.lastModified((File) f));
        Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
        if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
            return (long) result.get();
        }

        long ret = f.$JMVX$lastModified();
        this.sendObjectToCoordinator(new FileLastModified(ret));
        return ret;
    }

    @Override
    public boolean isFile(JMVXFile f) {
        boolean ret = f.$JMVX$isFile();
        this.sendObjectToCoordinator(new IsFile(ret));
        return ret;
    }

    @Override
    public void open(JMVXFileOutputStream fos, String name, boolean append) throws FileNotFoundException {
        try {
            Callable<Optional<Object>> function = () -> {
                try {
                    JMVXRuntime.open((FileOutputStream) fos, name, append);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                return Optional.of(new VoidObject());
            };
            Optional<Object> result = switchRoles(function, promoteDemoteEnabled);
            if (result.isPresent() && !(result.get() instanceof DoNothingObject)) {
                return;
            }
            fos.$JMVX$open(name, append);
            JMVXRuntime.openedFiles.put(fos, name);
            this.sendObjectToCoordinator(new FileOutputStreamOpen(name, append));
        } catch (FileNotFoundException e) {
            log.warn("FileOutputStreamOpen", e);
            this.sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void open(JMVXFileInputStream fis, String name) throws FileNotFoundException {
        try{
            fis.$JMVX$open(name);
            this.sendObjectToCoordinator(new FileInputStreamOpen(name));
        } catch (FileNotFoundException e) {
            log.warn("FileInputStreamOpen", e);
            this.sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public String fileOutputStream(String name) {
        return name;
    }

    @Override
    public boolean fileOutputStream(boolean append) {
        return append;
    }

    @Override
    public File fileOutputStream(File file) {
        return file;
    }

    @Override
    public FileDescriptor fileOutputStream(FileDescriptor fdObj) {
        return fdObj;
    }

    @Override
    public void sync(FileDescriptor fd) throws SyncFailedException {
        try{
            fd.sync();
            this.sendObjectToCoordinator(new FileDescriptorSync());
        } catch (SyncFailedException e) {
            this.sendObjectToCoordinator(e);
            throw e;
        }
    }

    protected void sendObjectToCoordinator(Serializable o) {
        try {
            objectCounter++;
            toCoordinator.writeObject(o);
            toCoordinator.flush();
            toCoordinator.reset();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    protected void sendObjectOverSocket(Serializable o){
        try {
            objectCounter++;
            toSocketStream.writeObject(o);
            toSocketStream.flush();
            toSocketStream.reset();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private Object receiveObjectFromCoordinator() {
        Object o = null;
        try {
            o = fromCoordinator.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new Error(e);
        }
        return o;
    }

    public void connect(JMVXSocket sock, SocketAddress endpoint, int timeout) throws IOException{
        //Leader connects normally
        try {
            sock.$JMVX$connect(endpoint, timeout);
            //may need to refactor getFD to prevent exceptions from needing to be added...
            FileDescriptor fd = FileDescriptorUtils.getFileDescriptor((Socket) sock);
            leaderSocket.setOutboundFileDescriptors(fd);
            sendObjectOverSocket(new Connect(endpoint, timeout));
        } catch (IOException e) {
            //TODO catch other exceptions thrown by connect
            sendObjectToCoordinator(e);
            throw e;
        } catch (Exception e) {
        //(IllegalAccessException | NoSuchMethodException | InvocationTargetException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void bind(JMVXServerSocket serv, SocketAddress endpoint, int backlog) throws IOException {
        try {
            //bind to the endpoint
            serv.$JMVX$bind(endpoint, backlog);
            //get the fd and send it to the coordinator/follower
            FileDescriptor fd = FileDescriptorUtils.getFileDescriptor((ServerSocket) serv);
            leaderSocket.setOutboundFileDescriptors(fd);
            sendObjectOverSocket(new Bind(endpoint, backlog));
        }catch(IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public Socket accept(JMVXServerSocket serv) throws IOException {
        try {
            //get the new client socket
            Socket sock = serv.$JMVX$accept();
            //Get and send its file descriptor
            FileDescriptor fd = FileDescriptorUtils.getFileDescriptor(sock);
            leaderSocket.setOutboundFileDescriptors(fd);
            sendObjectOverSocket(new Accept());
            return sock;
        } catch (IOException e) {
            //TODO catch other exceptions thrown by connect
            sendObjectToCoordinator(e);
            throw e;
        } catch (Exception e) { //errors related to getting the fd...
            e.printStackTrace();
        }
        //Only get here if there is an error...
        return null;
    }

    private long[] clockCopy = new long[0];

    @Override
    public void monitorenter(Object o) {
        /*if(inClinit.get() > 0){
            JMVXRuntime.unsafe.monitorEnter(o);
            return;
        }*/

//        if (JMVXRuntime.clock.size() == 1) {
//            JMVXRuntime.unsafe.monitorEnter(o);
//            return;
//        }

        VectorClock clock = JMVXRuntime.clock;

        JMVXRuntime.unsafe.monitorEnter(o);

        clockCopy = clock.increment(clockCopy);

        MonitorEnter me;
        if(circularBufferCommunicationEnabled) {
            if(avoidObjectSerialization) {
                buffer.writeData(clockCopy, true);
            }
            else {
                me = new MonitorEnter(clockCopy);
                this.writeToCircularBuffer(me);
            }
        }
        else {
            me = new MonitorEnter(clockCopy);
            this.sendObjectToCoordinator(me);
        }
    }

    @Override
    public void monitorexit(Object o) {
        JMVXRuntime.unsafe.monitorExit(o);
    }

    //TODO/NOTE: should modify to handle when inClinit > 0
    //we don't want to modify the vector clock in that case (or send clock objects)
    @Override
    public void wait(Object o, long timeout, int nanos) throws InterruptedException {
        /*if(inClinit.get() > 0){
            o.wait(timeout, nanos);
            return;
        }*/

        VectorClock clock = JMVXRuntime.clock;

        o.wait(timeout, nanos);

        clockCopy = clock.increment(clockCopy);

        Wait wt;
        if(circularBufferCommunicationEnabled) {
            if(avoidObjectSerialization) {
                buffer.writeData(clockCopy, true);
            }
            else {
                wt = new Wait(clockCopy);
                this.writeToCircularBuffer(wt);
            }
        }
        else {
            wt = new Wait(clockCopy);
            this.sendObjectToCoordinator(wt);
        }
    }

    @Override
    public void threadStart(Runnable r) {

    }

    @Override
    public void threadExit(Runnable r) {
        exitLogic();
    }

    @Override
    public void exitLogic() {
        try {
            /*
            We don't need to send "RTE" to cleanly exit--we could just break the connection
            Not doing that to determine why the connection was terminated.
            E.g., disconnecting can come from crashing or exiting. The "RTE" message informs us the disconnect
            was from an exit; it also nicely mimics the Followers logic.
             */
            LeaderSwitchingThread.removeThreads(Thread.currentThread());
            if (useBufferBackedStreams) {
                toCoordinator = toSocketStream;
                fromCoordinator = fromSocketStream;
            }
            if (LeaderSwitchingThread.leaderThreads.size() == 0){
                toCoordinator.writeObject(CoordinatorThread.finalExitMessage); //Last thread is exiting
                isExecuting.set(false);
            }
            else
                toCoordinator.writeObject(CoordinatorThread.exitMessage); //want coordinator thread to start listening to the follower
            toCoordinator.flush();
            if(!fastExit) {
                //wait for the coordinator to tell us the follower has exited
                fromCoordinator.readObject(); //wait for ACK
            }
            if (useBufferBackedStreams) {
                toCoordinator = toBufferStream;
                fromCoordinator = fromBufferStream;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static final boolean DEBUG = (System.getProperty("IS_DEBUG") != null);

    /*default*/
    static void debug(JMVXInputStream is, int ret) {
        if (DEBUG)
            debug(is, ret, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /*default*/
    static void debug(JMVXInputStream is, int ret, int size) {
        if (DEBUG)
            debug(is, ret, Optional.of(size), Optional.empty(), Optional.empty());
    }

    /*default*/
    static void debug(JMVXInputStream is, int ret, int size, int off, int len) {
        if (DEBUG)
            debug(is, ret, Optional.of(size), Optional.of(off), Optional.of(len));
    }

    private static void debug(JMVXInputStream is, int ret, Optional<Integer> size, Optional<Integer> off, Optional<Integer> len) {
        String name = "";
        if (is instanceof FileInputStream) {
            FileInputStream fis = (FileInputStream) is;
            try {
                Field f = FileInputStream.class.getDeclaredField("path");
                f.setAccessible(true);
                name = (String) f.get(fis);
            } catch (ReflectiveOperationException ex) {
                throw new Error(ex);
            }
        } else if (is.getClass().getSimpleName().endsWith("ZipFileInputStream")) {
            try {
                Field outerField = is.getClass().getDeclaredField("this$0");
                outerField.setAccessible(true);
                ZipFile outer = (ZipFile) outerField.get(is);
                name = outer.getName();
            } catch (ReflectiveOperationException ex) {
                throw new Error(ex);
            }
        } else {
            name = is.getClass().getName();
        }

        String o = off.map(i -> "offset: " + i + " ").orElse("");
        String s = size.map(i -> "size: " + i + " ").orElse("");
        String l = len.map(i -> "length: " + i + " ").orElse("");

        System.out.println("" + o + l + s + ret + " " + name);
    }

    public static void debug(JMVXRandomAccessFile f, int ret, Optional<Integer> size, Optional<Integer> off, Optional<Integer> len) throws IOException {
        if(DEBUG) {
            System.out.format("(RAF) FD: %s offset: %s size: %s length: %s\n",
                    ((RandomAccessFile) f).getFD().toString(),
                    off.map(i -> i.toString()).orElse("-"),
                    size.map(i -> i.toString()).orElse("-"),
                    len.map(i -> i.toString()).orElse("-"));
        }
    }

    @Override
    public int read(JMVXRandomAccessFile f) throws IOException {
        try {
            int ret = f.$JMVX$read();
            sendObjectToCoordinator(new RARead(ret));
            return ret;
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] bytes) throws IOException {
        try {
            int ret = f.$JMVX$read(bytes);
            sendObjectToCoordinator(new RAReadB(ret, bytes));
            return ret;
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }

    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] bytes, int off, int len) throws IOException {
        if (circularBufferCommunicationEnabled) {
            try {
                int ret = f.$JMVX$read(bytes, off, len);
                if (avoidObjectSerialization) {
                    buffer.writeData(bytes, off, ret, true);
                } else {
                    RAReadBII rbii = new RAReadBII(ret, bytes, off, len);
                    this.writeToCircularBuffer(rbii);
                }
                return ret;
            } catch (IOException e) {
                throw new Error("Not supported");
            }
        } else {
            try {
                int ret = f.$JMVX$read(bytes, off, len);
                sendObjectToCoordinator(new RAReadBII(ret, bytes, off, len));
                return ret;
            } catch (IOException e) {
                sendObjectToCoordinator(e);
                throw e;
            }
        }
    }

    @Override
    public void open(JMVXRandomAccessFile raf, String name, int mode) throws FileNotFoundException {
        try {
            raf.$JMVX$open(name, mode);
            this.sendObjectToCoordinator(new RAOpen(name, mode));
        } catch (FileNotFoundException e) {
            this.sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void close(JMVXRandomAccessFile raf) throws IOException {
        try {
            raf.$JMVX$close();
        } catch (IOException e) {
            throw e;
        }
    }

    @Override
    public void write(JMVXRandomAccessFile raf, int b) throws IOException {
        try {
            raf.$JMVX$write(b);
            sendObjectToCoordinator(new RAWrite(b));
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void write(JMVXRandomAccessFile raf, byte[] bytes) throws IOException {
        try {
            raf.$JMVX$write(bytes);
            sendObjectToCoordinator(new RAWriteB(bytes));
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void write(JMVXRandomAccessFile raf, byte[] bytes, int off, int len) throws IOException {
        try {
            raf.$JMVX$write(bytes, off, len);
            sendObjectToCoordinator(new RAWriteBII(bytes, off, len));
        } catch (IOException e) {
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    public long length(JMVXRandomAccessFile f) throws IOException {
        try{
            long len = ((RandomAccessFile)f).length();
            sendObjectToCoordinator(new RALength(len));
            return len;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void setLength(JMVXRandomAccessFile f, long newLength) throws IOException {
        try{
            ((RandomAccessFile)f).setLength(newLength);
            sendObjectToCoordinator(new RASetLength(newLength));
        }catch (IOException e){
            sendObjectToCoordinator(e);
        }
    }

    @Override
    public void seek(JMVXRandomAccessFile f, long pos) throws IOException {
        if (circularBufferCommunicationEnabled) {
            try {
                f.$JMVX$seek(pos);
                if (avoidObjectSerialization) {
                    byte[] data = new byte[] {
                            (byte)  pos,
                            (byte) (pos >> 8),
                            (byte) (pos >> 16),
                            (byte) (pos >> 24),
                            (byte) (pos >> 32),
                            (byte) (pos >> 40),
                            (byte) (pos >> 48),
                            (byte) (pos >> 56)};
                    buffer.writeData(data, 0, 8, true);
                } else {
                    RASeek raseek = new RASeek(pos);
                    this.writeToCircularBuffer(raseek);
                }
            } catch (IOException e) {
                throw new Error("Not supported");
            }
        } else {
            try{
                f.$JMVX$seek(pos);
                sendObjectToCoordinator(new RASeek(pos));
            }catch (IOException e){
                sendObjectToCoordinator(e);
            }
        }

    }

    @Override
    public int read(JMVXFileChannel c, ByteBuffer dst) throws IOException {
        try{
            int ret = c.$JMVX$read(dst);
            /*ByteBuffer.array is an OPTIONAL METHOD.
            The methods available depends on the underlying mechanism.
            E.g., HeapBuffer versus DirectBuffer*/
            byte[] buf;
            if(dst.isDirect()){
                //memory is outside the JVM, we need to pull it in
                //ret is the number of bytes read!
                buf = new byte[ret];
                dst.rewind();
                dst.get(buf);
            }else{
                //memory resides on the heap, we can access it easily!
                buf = dst.array();
            }
            sendObjectToCoordinator(new ChannelRead(buf, ret));
            return ret;
        }catch(IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long size(JMVXFileChannel c) throws IOException {
        try {
            long ret = c.$JMVX$size();
            sendObjectToCoordinator(new ChannelSize(ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long currentTimeMillis() {
        long ret = System.currentTimeMillis();

        if (JMVXRuntime.avoidObjectSerialization && JMVXRuntime.circularBufferCommunicationEnabled) {
            byte[] b = new byte[] {
                    (byte)  ret,
                    (byte) (ret >> 8),
                    (byte) (ret >> 16),
                    (byte) (ret >> 24),
                    (byte) (ret >> 32),
                    (byte) (ret >> 40),
                    (byte) (ret >> 48),
                    (byte) (ret >> 56)};
            this.buffer.writeData(b, 0, b.length);
//            sendObjectToCoordinator(b);
        } else {
            sendObjectToCoordinator(new TimeMillis(ret));
        }

        return ret;
    }

    @Override
    public long nanoTime() {
        long ret = System.nanoTime();
        sendObjectToCoordinator(new NanoTime(ret));
        return ret;
    }

    @Override
    public Path createTempFile(Path dir, String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        Path ret = Files.createTempFile(dir, prefix, suffix, attrs);
        sendObjectToCoordinator(new CreateTempFile(ret.toFile(), null, prefix, suffix, attrs));
        return ret;
    }

    @Override
    public Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        Path ret = Files.createTempFile(prefix, suffix, attrs);
        sendObjectToCoordinator(new CreateTempFile(ret.toFile(), null, prefix, suffix, attrs));
        return ret;
    }

    @Override
    public void copy(JMVXFileSystemProvider fsp, Path source, Path target, CopyOption... options) throws IOException {
        fsp.$JMVX$copy(source, target, options);
        sendObjectToCoordinator(new FileSystemProviderCopy(source.toFile().getPath(), target.toFile().getPath()));
    }

    @Override
    public void checkAccess(JMVXFileSystemProvider fsp, Path path, AccessMode... modes) throws IOException {
        try {
            fsp.$JMVX$checkAccess(path, modes);
            sendObjectToCoordinator(new FileSystemProviderCheckAccess(null));
        } catch (IOException e) {
            sendObjectToCoordinator(new FileSystemProviderCheckAccess(e));
            throw e;
        }
    }

    @Override
    public int open(UnixPath path, int flags, int mode) throws UnixException {
        try {
            int ret = UnixNativeDispatcher.open(path, flags, mode);
            sendObjectToCoordinator(new NativeOpen(path.toString(), flags, mode, ret));
            return ret;
        }catch (UnixException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void stat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        try {
            UnixNativeDispatcher.stat(path, attrs);
            sendObjectToCoordinator(new NativeStat(path.toString(), attrs));
        }catch (Exception e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void lstat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        try {
            UnixNativeDispatcher.lstat(path, attrs);
            sendObjectToCoordinator(new NativeLstat(path.toString(), attrs));
        }catch (Exception e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long opendir(UnixPath path) throws UnixException {
        try {
            long ret = UnixNativeDispatcher.opendir(path);
            sendObjectToCoordinator(new NativeOpenDir(path.toString(), ret));
            return ret;
        }catch (Exception e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public byte[] readdir(long dir) throws UnixException {
        try {
            byte[] ret = UnixNativeDispatcher.readdir(dir);
            sendObjectToCoordinator(new NativeReadDir(dir, ret));
            return ret;
        }catch (Exception e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void access(UnixPath path, int amode) throws UnixException {
        try {
            UnixNativeDispatcher.access(path, amode);
            sendObjectToCoordinator(new NativeAccess(path.toString(), amode));
        }catch (Exception e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void closedir(long dir) throws UnixException {
        UnixNativeDispatcher.closedir(dir);
    }

    @Override
    public void mkdir(UnixPath path, int mode) throws UnixException {
        try {
            UnixNativeDispatcher.mkdir(path, mode);
            sendObjectToCoordinator(new NativeMkdir(path.toString(), mode));
        }catch (Exception e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int dup(int fd) throws UnixException {
        try {
            int ret = UnixNativeDispatcher.dup(fd);
            sendObjectToCoordinator(new NativeDup(fd, ret));
            return ret;
        }catch (Exception e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long fdopendir(int dfd) throws UnixException {
        try {
            long ret = UnixNativeDispatcher.fdopendir(dfd);
            sendObjectToCoordinator(new NativeFdOpenDir(dfd, ret));
            return ret;
        }catch (Exception e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public byte[] realpath(UnixPath path) throws UnixException {
        try{
            byte[] ret = UnixNativeDispatcher.realpath(path);
            sendObjectToCoordinator(new NativeRealPath(path.toString(), ret));
            return ret;
        }catch (UnixException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int read(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        try{
            int ret = impl.$JMVX$read(fd, address, len);
            sendObjectToCoordinator(new FileDispatcherRead(FileDescriptorUtils.fdAccess.get(fd), address, len, ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int pread(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException {
        try{
            int ret = impl.$JMVX$pread(fd, address, len, position);
            sendObjectToCoordinator(new FileDispatcherPread(FileDescriptorUtils.fdAccess.get(fd), address, len, ret, position));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long readv(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        try{
            long ret = impl.$JMVX$readv(fd, address, len);
            sendObjectToCoordinator(new FileDispatcherReadv(FileDescriptorUtils.fdAccess.get(fd), address, len));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int write(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        try{
            int ret = impl.$JMVX$write(fd, address, len);
            sendObjectToCoordinator(new FileDispatcherWrite(FileDescriptorUtils.fdAccess.get(fd), address, len, ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int pwrite(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException {
        try{
            int ret = impl.$JMVX$pwrite(fd, address, len, position);
            sendObjectToCoordinator(new FileDispatcherPwrite(FileDescriptorUtils.fdAccess.get(fd), address, len, position, ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long writev(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        try{
            long ret = impl.$JMVX$writev(fd, address, len);
            sendObjectToCoordinator(new FileDispatcherWritev(FileDescriptorUtils.fdAccess.get(fd), address, len, ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long seek(JMVXFileDispatcherImpl impl, FileDescriptor fd, long offset) throws IOException {
        try{
            long ret = impl.$JMVX$seek(fd, offset);
            sendObjectToCoordinator(new FileDispatcherSeek(FileDescriptorUtils.fdAccess.get(fd), offset, ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int force(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean metaData) throws IOException {
        try{
            int ret = impl.$JMVX$force(fd, metaData);
            sendObjectToCoordinator(new FileDispatcherForce(FileDescriptorUtils.fdAccess.get(fd), metaData, ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int truncate(JMVXFileDispatcherImpl impl, FileDescriptor fd, long size) throws IOException {
        try{
            int ret = impl.$JMVX$truncate(fd,size);
            sendObjectToCoordinator(new FileDispatcherTruncate(FileDescriptorUtils.fdAccess.get(fd), size));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public long size(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        try{
            long ret = impl.$JMVX$size(fd);
            sendObjectToCoordinator(new FileDispatcherSize(FileDescriptorUtils.fdAccess.get(fd), ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public int lock(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException {
        try{
            int ret = impl.$JMVX$lock(fd, blocking, pos, size, shared);
            sendObjectToCoordinator(new FileDispatcherLock(FileDescriptorUtils.fdAccess.get(fd), blocking, pos, size, shared, ret));
            return ret;
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void release(JMVXFileDispatcherImpl impl, FileDescriptor fd, long pos, long size) throws IOException {
        try{
            impl.$JMVX$release(fd, pos, size);
            sendObjectToCoordinator(new FileDispatcherRelease(FileDescriptorUtils.fdAccess.get(fd), pos, size));
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public void close(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        impl.$JMVX$close(fd);
    }

    @Override
    public void preClose(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        try{
            impl.$JMVX$preClose(fd);
            sendObjectToCoordinator(new FileDispatcherPreClose(FileDescriptorUtils.fdAccess.get(fd)));
        }catch (IOException e){
            sendObjectToCoordinator(e);
            throw e;
        }
    }

    @Override
    public String getSystemTimeZoneID(String javaHome) {
        String ret = TimeZone.getSystemTimeZoneID(javaHome);
        sendObjectToCoordinator(new GetSystemTimeZoneID(javaHome, ret));
        return ret;
    }

    /*default*/ static class Read implements Serializable {
        private static final long serialVersionUID = 8248701411369551163L;
        public final int ret;

        public Read(int ret) {
            this.ret = ret;
        }
    }

    /*default*/ static class ReadB implements Serializable {
        private static final long serialVersionUID = -7753689735171121522L;
        public final int ret;
        public final byte[] b;

        public ReadB(int ret, byte[] b) {
            this.ret = ret;
            this.b = b;
        }
    }

    /*default*/ static class ReadBII implements Serializable {
        private static final long serialVersionUID = -8166362716492380175L;
        public final int ret;
        public final byte[] b;
        public final int off, len;

        public ReadBII(int ret, byte[] b, int off, int len) {
            this.ret = ret;
            this.b = b;
            this.off = off;
            this.len = len;
        }
    }

    /*default*/ static class Available implements Serializable {
        private static final long serialVersionUID = -8738498116207867056L;
        public final int ret;
        public Available(int ret) { this.ret = ret; }
    }

    /*default*/ static class Write implements Serializable {
        private static final long serialVersionUID = -1275307460146515728L;
        public final int b;

        public Write(int b) {
            this.b = b;
        }
    }

    /*default*/ static class WriteB implements Serializable {
        private static final long serialVersionUID = -5864970803544372903L;
        public final byte[] b;

        public WriteB(byte[] b) {
            this.b = b;
        }
    }

    /*default*/ static class WriteBII implements Serializable {
        private static final long serialVersionUID = -7471915106796087089L;
        public final byte[] b;
        public final int off, len;

        public WriteBII(byte[] b, int off, int len) {
            this.b = b;
            this.off = off;
            this.len = len;
        }
    }

    static class FileCanRead implements Serializable {
        private static final long serialVersionUID = -538337334067959266L;
        public final boolean ret;

        public FileCanRead(boolean ret) {
            this.ret = ret;
        }
    }

    static class FileCanWrite implements Serializable {
        private static final long serialVersionUID = -2251054193655571397L;
        public final boolean ret;

        public FileCanWrite(boolean ret) {
            this.ret = ret;
        }
    }

    static class FileCreateNewFile implements Serializable {
        private static final long serialVersionUID = -1949115824823475728L;
        public final boolean ret;

        public FileCreateNewFile(boolean ret) {
            this.ret = ret;
        }
    }

    public static class FileDelete implements Serializable {
        private static final long serialVersionUID = -2555686438346063173L;
        public final boolean ret;

        public FileDelete(boolean ret) {
            this.ret = ret;
        }
    }

    static class FileExists implements Serializable {
        private static final long serialVersionUID = 2539456588280997741L;
        public final boolean ret;

        public FileExists(boolean ret) {
            this.ret = ret;
        }
    }

    static class FileGetAbsoluteFile implements Serializable {
        private static final long serialVersionUID = 5512595607004449365L;
        public final File ret;

        public FileGetAbsoluteFile(File ret) {
            this.ret = ret;
        }
    }

    static class FileGetAbsolutePath implements Serializable {
        private static final long serialVersionUID = 6347986768603929179L;
        public final String ret;

        public FileGetAbsolutePath(String ret) {
            this.ret = ret;
        }
    }

    static class FileGetCanonicalFile implements Serializable {
        private static final long serialVersionUID = -2602211034593248454L;
        public final File ret;

        public FileGetCanonicalFile(File ret) {
            this.ret = ret;
        }
    }

    static class FileGetCanonicalPath implements Serializable {
        private static final long serialVersionUID = 5155015148720024491L;
        public final String ret;

        public FileGetCanonicalPath(String ret) {
            this.ret = ret;
        }
    }

    static class FileGetName implements Serializable {
        private static final long serialVersionUID = -3466414227998379811L;
        public final String ret;

        public FileGetName(String ret) {
            this.ret = ret;
        }
    }

    static class FileGetPath implements Serializable {
        private static final long serialVersionUID = 2740765556256838133L;
        public final String ret;

        public FileGetPath(String ret) {
            this.ret = ret;
        }
    }

    static class FileGetParentFile implements Serializable {
        private static final long serialVersionUID = -4497910538424005170L;
        public final File ret;

        public FileGetParentFile(File ret) {
            this.ret = ret;
        }
    }

    static class FileIsDirectory implements Serializable {
        private static final long serialVersionUID = -1524919575881849250L;
        public final boolean ret;

        public FileIsDirectory(boolean ret) {
            this.ret = ret;
        }
    }

    static class FileLength implements Serializable {
        private static final long serialVersionUID = 2634594409135129500L;
        public final long ret;

        public FileLength(long ret) {
            this.ret = ret;
        }
    }

    static class FileList implements Serializable {
        private static final long serialVersionUID = 8731278317227578603L;
        public final String[] ret;

        public FileList(String[] ret) {
            this.ret = ret;
        }
    }

    /*default*/ static class FileListFilter implements Serializable {
        private static final long serialVersionUID = -8766884245044452682L;
        public final String[] unfiltered;

        public FileListFilter(String[] unfiltered) {
            this.unfiltered = unfiltered;
        }
    }

    static class FileListFiles implements Serializable {
        private static final long serialVersionUID = -5119001235753128588L;
        public final File[] ret;

        public FileListFiles(File[] ret) {
            this.ret = ret;
        }
    }

    static class FileListFilesFilter implements Serializable {
        private static final long serialVersionUID = 5926312547546983799L;
        public final File[] unfiltered;

        public FileListFilesFilter(File[] unfiltered) {
            this.unfiltered = unfiltered;
        }
    }

    static class FileMkdir implements Serializable {
        private static final long serialVersionUID = 3097647237497766240L;
        public final boolean ret;
        public FileMkdir(boolean ret) { this.ret = ret; }
    }

    static class FileMkdirs implements Serializable {
        private static final long serialVersionUID = 2856519211731018476L;
        public final boolean ret;
        public FileMkdirs(boolean ret) { this.ret = ret; }
    }


    static class FileRenameTo implements Serializable {
        private static final long serialVersionUID = 2926524963692438733L;
        public final boolean ret;
        public final File f;

        public FileRenameTo(boolean ret, File f) {
            this.ret = ret;
            this.f = f;
        }
    }

    static class FileSetReadOnly implements Serializable {
        private static final long serialVersionUID = -2895509903845809085L;
        public final boolean ret;

        public FileSetReadOnly(boolean ret) {
            this.ret = ret;
        }
    }

    static class FileToURL implements Serializable {
        private static final long serialVersionUID = -3291163945452082024L;
        public final URL ret;

        public FileToURL(URL ret) {
            this.ret = ret;
        }
    }

    static class FileLastModified implements Serializable {
        private static final long serialVersionUID = -5851863238493584154L;
        public final long ret;
        public FileLastModified(long ret) {
            this.ret = ret;
        }
    }

    static class IsFile implements Serializable{
        private static final long serialVersionUID = -2835638096061908902L;
        public final boolean ret;
        public IsFile(boolean ret) {
            this.ret = ret;
        }
    }

    static class FileOutputStreamOpen implements Serializable {
        private static final long serialVersionUID = -8895509075644283251L;
        public final String name;
        public final boolean append;

        public FileOutputStreamOpen(String name, boolean append) {
            this.name = name;
            this.append = append;
        }
    }

    static class FileInputStreamOpen implements Serializable {
        private static final long serialVersionUID = -1980148051600649867L;
        public final String name;

        public FileInputStreamOpen(String name) {
            this.name = name;
        }
    }

    static class FileDescriptorSync implements Serializable {
        private static final long serialVersionUID = 6555690687761121811L;
    }

    static class Connect implements Serializable {
        private static final long serialVersionUID = -5518083562481732343L;
        public SocketAddress endpoint;
        public int timeout;
        public Connect(SocketAddress endpoint, int timeout) {
            this.endpoint = endpoint;
            this.timeout = timeout;
        }
    }

    static class Bind implements Serializable {
        private static final long serialVersionUID = 2474132660619817310L;
        public SocketAddress endpoint;
        public int backlog;
        public Bind(SocketAddress endpoint, int backlog){
            this.endpoint = endpoint;
            this.backlog = backlog;
        }
    }

    static class Accept implements Serializable {
        private static final long serialVersionUID = -3463609108315756653L;
        public Accept() {}
        //public int localPort;
        //public Accept(int port){this.localPort = port;}
    }

    static class AcceptBegin implements Serializable{
        private static final long serialVersionUID = 7817326191653933018L;
        public AcceptBegin() {
        }
    }

    static class AcceptEnd implements Serializable{
        private static final long serialVersionUID = 2577250288949774035L;
        public AcceptEnd() {
        }
    }

    static class MonitorEnter implements Serializable {
        private static final long serialVersionUID = -1059341792896636602L;
        public final long[] clock;
        public MonitorEnter(long[] clock) {
            this.clock = clock;
        }
    }

    static class Wait implements Serializable {
        private static final long serialVersionUID = -125207637055109316L;
        public final long[] clock;

        public Wait(long[] clock) {
            this.clock = clock;
        }
    }

    /*default*/ static class RARead implements Serializable {
        private static final long serialVersionUID = -5385942546610734976L;
        public final int ret;
        public RARead(int ret) { this.ret = ret; }
    }

    /*default*/ static class RAReadB implements Serializable {
        private static final long serialVersionUID = 6283512089592128395L;
        public final int ret;
        public final byte[] b;
        public RAReadB(int ret, byte[] b) { this.ret = ret; this.b = b; }
    }

    /*default*/ static class RAReadBII implements Serializable {
        private static final long serialVersionUID = 5350319809033626351L;
        public final int ret;
        public final byte[] b;
        public final int off, len;
        public RAReadBII(int ret, byte[] b, int off, int len) {
            this.ret = ret;
            this.b = b;
            this.off = off;
            this.len = len;
        }
    }

    static class RAOpen implements Serializable {
        private static final long serialVersionUID = 7299414554108795266L;
        public final String name;
        public final int mode;

        public RAOpen(String name, int mode) {
            this.name = name;
            this.mode = mode;
        }
    }

    /*default*/ static class RAWrite implements Serializable {
        private static final long serialVersionUID = 6208884415621475682L;
        public final int b;
        public RAWrite(int b) { this.b = b; }
    }

    /*default*/ static class RAWriteB implements Serializable {
        private static final long serialVersionUID = 5413983382540899826L;
        public final byte[] b;
        public RAWriteB(byte[] b) { this.b = b; }
    }

    /*default*/ static class RAWriteBII implements Serializable {
        private static final long serialVersionUID = 5579457088785271345L;
        public final byte[] b;
        public final int off, len;
        public RAWriteBII(byte[] b, int off, int len) {
            this.b = b;
            this.off = off;
            this.len = len;
        }
    }

    static class RALength implements Serializable{
        private static final long serialVersionUID = 1248459452001320354L;
        public long len;
        public RALength(long len) {
            this.len = len;
        }
    }

    static class RASeek implements Serializable{
        private static final long serialVersionUID = 375470715892433022L;
        public long pos;
        public RASeek(long pos){ this.pos = pos; }
    }

    static class RASetLength implements Serializable{
        private static final long serialVersionUID = 7251967962539238084L;
        public long newLength;

        public RASetLength(long newLength) {
            this.newLength = newLength;
        }
    }

    public static class Clinit implements Serializable {
        private static final long serialVersionUID = 882327620241254787L;
        public String className;
        public byte[] bytes;

        public Clinit(String className, byte[] bytes) {
            this.className = className;
            this.bytes = bytes;
        }
    }

    public static class FDRead implements Serializable{
        //public FileDescriptor fd; //don't think we can send this
        public long address;
        public int len;
        public int ret;

        public FDRead(long address, int len, int ret) {
            this.address = address;
            this.len = len;
            this.ret = ret;
        }
    }

    public static class ChannelRead implements Serializable{
        private static final long serialVersionUID = 8715867463147430044L;
        public byte[] dst;
        public int ret;

        public ChannelRead(byte[] dst, int ret) {
            this.dst = dst;
            this.ret = ret;
        }
    }

    public static class ChannelSize implements Serializable{
        private static final long serialVersionUID = -8842643811563164427L;
        public long size;

        public ChannelSize(long size) {
            this.size = size;
        }
    }

    public static class TimeMillis implements Serializable{
        private static final long serialVersionUID = -5620984772153064295L;
        public long millis;

        public TimeMillis(long millis) {
            this.millis = millis;
        }
    }

    public static class NanoTime implements Serializable{
        private static final long serialVersionUID = -3337608226516264862L;
        public long nano;

        public NanoTime(long nano) {
            this.nano = nano;
        }
    }

    public static class CreateTempFile implements Serializable{
        private static final long serialVersionUID = 475975878408384558L;

        public Path dir;
        public String prefix;
        public String suffix;
        public FileAttribute[] attrs;
        public File ret;

        public CreateTempFile(File ret, Path dir, String prefix, String suffix, FileAttribute[] attrs) {
            this.ret = ret;
            this.dir = dir;
            this.prefix = prefix;
            this.suffix = suffix;
            this.attrs = attrs;
        }
    }

    public static class FileSystemProviderCopy implements Serializable{
        private static final long serialVersionUID = -3918026227087274639L;

        public String source;
        public String target;

        public FileSystemProviderCopy(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }

    public static class FileSystemProviderCheckAccess implements Serializable{
        private static final long serialVersionUID = -3918026227087274639L;
        public final IOException e;

        public FileSystemProviderCheckAccess(IOException e) {
            this.e = e;
        }
    }

    public static class NativeOpen implements Serializable {
        private static final long serialVersionUID = 5857984960927495682L;
        public final String path;
        public final int flags, mode, ret;

        public NativeOpen(String path, int flags, int mode, int ret) {
            this.path = path;
            this.flags = flags;
            this.mode = mode;
            this.ret = ret;
        }
    }

    public static class NativeStat implements Serializable {
        private static final long serialVersionUID = 3377700338828457394L;
        public final String path;
        public final UnixFileAttributes attrs;

        public NativeStat(String path, UnixFileAttributes attrs) {
            this.path = path;
            this.attrs = attrs;
        }
    }

    public static class FileDispatcherRead implements Serializable {
        private static final long serialVersionUID = -6570421893958931275L;
        public final int fd;
        public final long address;
        public final int len;
        public final int ret;

        public FileDispatcherRead(int fd, long address, int len, int ret) {
            this.fd = fd;
            this.address = address;
            this.len = len;
            this.ret = ret;
        }
    }

    public static class FileDispatcherPread implements Serializable {
        private static final long serialVersionUID = -7373651238655781345L;
        public final int fd;
        public final long address;
        public final int len;
        public final int ret;
        public final long position;

        public FileDispatcherPread(int fd, long address, int len, int ret, long position) {
            this.fd = fd;
            this.address = address;
            this.len = len;
            this.ret = ret;
            this.position = position;
        }
    }

    public static class FileDispatcherReadv implements Serializable {
        private static final long serialVersionUID = 7136189469086228469L;
        public final int fd;
        public final long address;
        public final int len;

        public FileDispatcherReadv(int fd, long address, int len) {
            this.fd = fd;
            this.address = address;
            this.len = len;
        }
    }

    public static class FileDispatcherWrite implements Serializable {
        private static final long serialVersionUID = -442125178841449864L;
        public final int fd;
        public final long address;
        public final int len;
        public final int ret;

        public FileDispatcherWrite(int fd, long address, int len, int ret) {
            this.fd = fd;
            this.address = address;
            this.len = len;
            this.ret = ret;
        }
    }

    public static class FileDispatcherPwrite implements Serializable {
        private static final long serialVersionUID = -7698200688030650251L;
        public final int fd;
        public final long address;
        public final int len;
        public final long position;
        public final int ret;

        public FileDispatcherPwrite(int fd, long address, int len, long position, int ret) {
            this.fd = fd;
            this.address = address;
            this.len = len;
            this.position = position;
            this.ret = ret;
        }
    }

    public static class FileDispatcherWritev implements Serializable {
        private static final long serialVersionUID = -7583520236781157893L;
        public final int fd;
        public final long address;
        public final int len;
        public final long ret;

        public FileDispatcherWritev(int fd, long address, int len, long ret) {
            this.fd = fd;
            this.address = address;
            this.len = len;
            this.ret = ret;
        }
    }

    public static class FileDispatcherSeek implements Serializable {
        private static final long serialVersionUID = 4404456679987720533L;
        public final int fd;
        public final long offset;
        public final long ret;

        public FileDispatcherSeek(int fd, long offset, long ret) {
            this.fd = fd;
            this.offset = offset;
            this.ret = ret;
        }
    }

    public static class FileDispatcherForce implements Serializable {
        private static final long serialVersionUID = -9021202856754253274L;
        public final int fd;
        public final boolean metaData;
        public final int ret;

        public FileDispatcherForce(int fd, boolean metaData, int ret) {
            this.fd = fd;
            this.metaData = metaData;
            this.ret = ret;
        }
    }

    public static class FileDispatcherTruncate implements Serializable {
        private static final long serialVersionUID = 8177294452484171249L;
        public final int fd;
        public final long size;

        public FileDispatcherTruncate(int fd, long size) {
            this.fd = fd;
            this.size = size;
        }
    }

    public static class FileDispatcherSize implements Serializable {
        private static final long serialVersionUID = 625585071867032715L;
        public final int fd;
        public final long ret;

        public FileDispatcherSize(int fd, long ret) {
            this.fd = fd;
            this.ret = ret;
        }
    }

    public static class FileDispatcherLock implements Serializable {
        private static final long serialVersionUID = -1483914572482816913L;
        public final int fd;
        public final boolean blocking;
        public final long pos;
        public final long size;
        public final boolean shared;
        public final int ret;

        public FileDispatcherLock(int fd, boolean blocking, long pos, long size, boolean shared, int ret) {
            this.fd = fd;
            this.blocking = blocking;
            this.pos = pos;
            this.size = size;
            this.shared = shared;
            this.ret = ret;
        }
    }

    public static class FileDispatcherRelease implements Serializable {
        private static final long serialVersionUID = 7581053300371227361L;
        public final int fd;
        public final long pos;
        public final long size;

        public FileDispatcherRelease(int fd, long pos, long size) {
            this.fd = fd;
            this.pos = pos;
            this.size = size;
        }
    }

    public static class FileDispatcherClose implements Serializable {
        private static final long serialVersionUID = -4633690080606423662L;
        public final int fd;
        public FileDispatcherClose(int fd) {
            this.fd = fd;
        }
    }

    public static class FileDispatcherPreClose implements Serializable {
        private static final long serialVersionUID = -1973058767995652709L;
        public final int fd;
        public FileDispatcherPreClose(int fd) {
            this.fd = fd;
        }
    }

    public static class NativeLstat implements Serializable {
        private static final long serialVersionUID = -9091498469406063793L;
        public final String path;
        public final UnixFileAttributes attrs;

        public NativeLstat(String path, UnixFileAttributes attrs) {
            this.path = path;
            this.attrs = attrs;
        }
    }

    public static class NativeOpenDir implements Serializable {
        private static final long serialVersionUID = 2370731297937022039L;
        public final String path;
        public final long ret;

        public NativeOpenDir(String path, long ret) {
            this.path = path;
            this.ret = ret;
        }
    }

    public static class NativeReadDir implements Serializable {
        private static final long serialVersionUID = -4183441330098558893L;
        public final long dir;
        public final byte[] ret;

        public NativeReadDir(long dir, byte[] ret) {
            this.dir = dir;
            this.ret = ret;
        }
    }

    public static class NativeAccess implements Serializable {
        private static final long serialVersionUID = -135971315179952436L;
        public final String path;
        public final int amode;

        public NativeAccess(String path, int amode) {
            this.path = path;
            this.amode = amode;
        }
    }

    public static class NativeCloseDir implements Serializable {
        private static final long serialVersionUID = 6918143277393324101L;
        public long dir;

        public NativeCloseDir(long dir) {
            this.dir = dir;
        }
    }

    public static class NativeMkdir implements Serializable {
        private static final long serialVersionUID = -670312247901546298L;
        public final String path;
        public final int mode;

        public NativeMkdir(String path, int mode) {
            this.path = path;
            this.mode = mode;
        }
    }

    public static class GetSystemTimeZoneID implements Serializable {
        private static final long serialVersionUID = -8790456217026337376L;
        public final String javaHome;
        public final String ret;

        public GetSystemTimeZoneID(String javaHome, String ret) {
            this.javaHome = javaHome;
            this.ret = ret;
        }
    }

    public static class NativeDup implements Serializable {
        private static final long serialVersionUID = 5487259947290521663L;
        public final int fd;
        public final int ret;

        public NativeDup(int fd, int ret) {
            this.fd = fd;
            this.ret = ret;
        }
    }

    public static class NativeFdOpenDir implements Serializable {
        private static final long serialVersionUID = -436548434539476692L;
        public final int dfd;
        public final long ret;

        public NativeFdOpenDir(int dfd, long ret) {
            this.dfd = dfd;
            this.ret = ret;
        }
    }

    public static class NativeRealPath implements Serializable {
        private static final long serialVersionUID = -2274973256708003669L;
        public final String path;
        public final byte[] ret;

        public NativeRealPath(String path, byte[] ret) {
            this.path = path;
            this.ret = ret;
        }
    }
}
