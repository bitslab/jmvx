package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.coordinate.CoordinatorThread;
import edu.uic.cs.jmvx.runtime.*;
import edu.uic.cs.jmvx.coordinate.Coordinator;
import edu.uic.cs.jmvx.runtime.JMVXFile;
import edu.uic.cs.jmvx.runtime.JMVXFileOutputStream;
import edu.uic.cs.jmvx.runtime.JMVXInputStream;
import edu.uic.cs.jmvx.runtime.JMVXOutputStream;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;
import org.apache.log4j.Logger;
import org.newsclub.net.unix.AFUNIXSocket;
import edu.uic.cs.jmvx.Utils;

import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.nio.ByteBuffer;

import java.util.zip.CRC32;
import edu.uic.cs.jmvx.runtime.DivergenceStatus;
import edu.uic.cs.jmvx.runtime.DivergenceHandler;

import java.io.*;
import java.net.*;
import sun.misc.Unsafe;
import sun.nio.fs.UnixException;
import sun.nio.fs.UnixFileAttributes;
import sun.nio.fs.UnixNativeDispatcher;
import sun.nio.fs.UnixPath;

import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

import java.util.concurrent.Callable;

public class Follower implements JMVXStrategy {
    protected AFUNIXSocket followerSocket;
    protected ObjectOutputStream toCoordinator;
    protected ObjectInputStream fromCoordinator;
    private ObjectInputStream fromSocketStream;
    private ObjectInputStream fromBufferStream;
    private ObjectOutputStream toSocketStream;
    private ObjectOutputStream toBufferStream;

    //Address of the server socket we use to emulate connections
    protected static final int junkPort = JMVXRuntime.junkPort;//9999;
    protected final static InetSocketAddress junkAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), junkPort);
    protected static ServerSocket junkServer;
    protected boolean connected = false;

    protected static Logger log;

    private static AFUNIXSocket clinitSocket;
    private static ObjectOutputStream clinitOut;
    private static ObjectInputStream clinitIn;
    //doesn't need to be a thread local because we sync on the class.
    protected Stack<ObjectInputStream> clinitsStreams = new Stack();
    //value to tell if we should modify the vectorclock on monitorenter (do if = 0)
    //thread local because not all threads will be in a clinit
    //those threads will need to use the original monitorenter
    protected int inClinit = 0;

    private static List<AFUNIXSocket> connections = new ArrayList<>();

    protected static int objectCounter = 0;

    private CRC32 crc32 = new CRC32();
    CircularBuffer buffer = null;
    int one = 0;

    // Using the default DivergenceHandler for now, but later can be changed to a user-supplied one
    DivergenceHandler divergenceHandler;
    {
        try {
            String className = System.getProperty("divergenceHandler", "edu.uic.cs.jmvx.runtime.DacapoDivergenceHandler");
            Class<?> clazz = null;
            clazz = Class.forName(className);
            Constructor<?>[] constructors = clazz.getConstructors();
            Object object = constructors[0].newInstance(new Object[]{});
            divergenceHandler = (DivergenceHandler)object;
        } catch (Exception e) {
            divergenceHandler = new DacapoDivergenceHandler();
        }
    }

    static{
        try{
            JMVXRuntime.enter();
            junkServer = new ServerSocket(junkPort);
        } catch (IOException exception) {
	    //if we run multiple experiments at once, this will fail and print
	    //but for most benchmarks, it doesn't matter
            //exception.printStackTrace();
        } finally {
            JMVXRuntime.exit();
        }
    }


    // creating a DivergenceStatus object here and storing it on the instance, so that we don't
    // have to create a new one every time. We can reuse this one by setting its fields accordingly.
    DivergenceStatus<Object> divergenceStatus = new DivergenceStatus();

    protected static boolean checksumCommunicationEnabled = JMVXRuntime.checksumCommunicationEnabled;
    protected static boolean consoleLogsEnabled = JMVXRuntime.consoleLogsEnabled;
    protected static boolean readsFromDisk = JMVXRuntime.readsFromDisk;
    protected static boolean circularBufferCommunicationEnabled = JMVXRuntime.circularBufferCommunicationEnabled;
    protected static boolean avoidObjectSerialization = JMVXRuntime.avoidObjectSerialization;
    protected static boolean useBufferBackedStreams = JMVXRuntime.useBufferBackedStreams;


    public void reConnect(AFUNIXSocket socket, ObjectOutputStream outputStream, ObjectInputStream inputStream, Logger l) {
        followerSocket = socket;
        toCoordinator = outputStream;
        fromCoordinator = inputStream;
        log = l;

        // Make sure that new threads use the Follower strategy
        System.setProperty(JMVXRuntime.ROLE_PROP_NAME, "Follower");
    }

    protected void sendMode() throws IOException {
        //Value is compared with lockstepMode on Coordinator
        toCoordinator.writeBoolean(false);
        toCoordinator.flush();
        toCoordinator.reset();
    }

    protected void divergence(String expected, String received) {
        log.warn("EXPECTED: " + expected + " RECEIVED: " + received + " OC: " + objectCounter);
        throw new DivergenceError();
    }

    private Object readFromBuffer() {
        ObjectInput in = null;
        Object ob;
        try {
            byte[] data = buffer.readData(true);

            //TODO: Fix performance bottleneck caused by new instance creation of
            //ByteArrayInputStream and ObjectInputStream on each read operation.
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            in = new ObjectInputStream(bis);
            ob = in.readObject();
        }
        catch (IOException | ClassNotFoundException e) {
            throw new Error(e);
        }
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                // Ignore close exception
            }
        }
        return ob;
    }

    protected void maybeWrite(JMVXOutputStream os, int b) throws IOException {
        if (os instanceof FileOutputStream) {
            FileOutputStream fos = (FileOutputStream) os;
            FileDescriptor fd = fos.getFD();
            if (fd == FileDescriptor.out || fd == FileDescriptor.err)
                os.$JMVX$write(b);
        }
    }

    protected void maybeWrite(JMVXOutputStream os, byte[] bytes) throws IOException {
        if (os instanceof FileOutputStream) {
            FileOutputStream fos = (FileOutputStream) os;
            FileDescriptor fd = fos.getFD();
            if (consoleLogsEnabled && (fd == FileDescriptor.out || fd == FileDescriptor.err))
                os.$JMVX$write(bytes);
        }
    }

    protected void maybeWrite(JMVXOutputStream os, byte[] bytes, int off, int len) throws IOException {
        if (os instanceof FileOutputStream) {
            FileOutputStream fos = (FileOutputStream) os;
            FileDescriptor fd = fos.getFD();
            if (consoleLogsEnabled && (fd == FileDescriptor.out || fd == FileDescriptor.err))
                os.$JMVX$write(bytes, off, len);
        }
    }

    protected void verifyWriteOb(JMVXOutputStream os, int expectedB, int receivedB) throws IOException {
        if (expectedB != receivedB) {
            log.warn("Potential mismatch! Difference in byte written");
        }
        maybeWrite(os, expectedB);
    }

    protected void verifyWriteOb(JMVXOutputStream os, byte[] expectedB, byte[] receivedB, Object write) throws IOException {
        String actualClassName;
        boolean bytesAreEqual;

        if(checksumCommunicationEnabled){
            bytesAreEqual = Arrays.equals(Utils.getChecksum(expectedB, crc32), receivedB);
        }else{
            bytesAreEqual = Arrays.equals(expectedB, receivedB);
        }

        if (!bytesAreEqual) {
            log.warn("Potential mismatch! Difference in bytes written");

            Callable<Object> function = () -> {
                JMVXRuntime.write((OutputStream) os, receivedB);
                return null;
            };

            Metadata metadata = new Metadata();
            metadata.source = DivergenceSource.FROM_WRITE;
            metadata.buffer = expectedB;
            metadata.originalBuffer = receivedB;
            metadata.isOffsetPresent = false;

            actualClassName = (write != null) ? write.getClass().getSimpleName() : "NoClassName";
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Write.class.getSimpleName(), actualClassName, write, function, metadata);
            maybeWrite(os, metadata.buffer);
        }else{
            maybeWrite(os, expectedB);
	}
    }

    protected void verifyWriteOb(JMVXOutputStream os, byte[] expectedBytes, byte[] recievedBytes, int expectedOff,
                               int recievedOff, int expectedLen, int recievedLen, Object write) throws IOException {
        boolean bytesAreEqual;

        if (checksumCommunicationEnabled) {
            bytesAreEqual = Arrays.equals(recievedBytes,
                    Utils.getChecksum(expectedBytes, expectedOff, expectedLen, crc32));
        } else {
            bytesAreEqual = true;
            int n = Math.min(recievedLen, expectedLen);
            int i = recievedOff;
            int j = expectedOff;
            for (int k = 0; k < n; k++){
                if(recievedBytes[i+k] != expectedBytes[j+k]){
                    bytesAreEqual = false;
                    break;
                }
            }
            //doesn't factor in the part of the buffer we care about
            //bytesAreEqual = Arrays.equals(recievedBytes, expectedBytes);
        }

        if (!bytesAreEqual) {
            String actualClassName;
            Metadata metadata = new Metadata();
            metadata.source = DivergenceSource.FROM_WRITE;
            metadata.buffer = expectedBytes;
            metadata.originalBuffer = recievedBytes;
            metadata.originalLength = recievedLen;
            metadata.originalOffset = recievedOff;
            metadata.length = expectedLen;
            metadata.offset = expectedOff;
            metadata.isOffsetPresent = true;

            log.warn("Potential mismatch! Difference in bytes written");
            if (checksumCommunicationEnabled) {
                // if checksum communication is enabled, we can't write the leader's string to the log
                // because we don't have it
                log.warn("The leader's string checksum: " + new String(recievedBytes));
            } else {
                log.warn("The leader wrote the string: " + new String(recievedBytes, recievedOff, recievedLen));
            }
            Callable<Object> function = () -> {
                JMVXRuntime.write((OutputStream) os, expectedBytes, expectedOff, expectedLen);
                return true;
            };

            actualClassName = (write != null) ? write.getClass().getSimpleName() : "NoClassName";
            // the divergence method may modify the metadata argument in C-style pass by reference
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Write.class.getSimpleName(), actualClassName, write, function, metadata);

            log.warn("The follower is trying to write the string: " + new String(expectedBytes, expectedOff, expectedLen));
            maybeWrite(os, metadata.buffer, metadata.offset, metadata.length);
        }else{
            maybeWrite(os, expectedBytes, expectedOff, expectedLen);
	}
    }

    @Override
    public void main() {
        // Make sure ObjectOutputStream is fully initialized
        // This may involve reading file /dev/urandom
        /*try {
            new ObjectOutputStream(new ByteArrayOutputStream(8000)).writeObject(new Exception());
            skipped = new ObjectOutputStream(skippedBytes);
        } catch (IOException e) {
        }*/
        connect(1235);
        log = Logger.getLogger(Follower.class);
        log.debug("Reading directly from disk: " + readsFromDisk);

        if(circularBufferCommunicationEnabled) {
            try {
                String status = (String) fromCoordinator.readObject();
                if (status.equals("BufferInitialized")) {
                    buffer = JMVXRuntime.mmapFile(false);
                    if (JMVXRuntime.deleteMmapOnDisk)
                        buffer.deleteFileOnDisk();
//                    toCoordinator.writeObject("FollowerReady");
//                    toCoordinator.flush();
//                    toCoordinator.reset();
//                    status = (String) fromCoordinator.readObject();
//                    while(!status.equals("BufferInitialized"));
//                    if (JMVXRuntime.deleteMmapOnDisk)
//                        buffer.deleteFileOnDisk();
                } else throw new Error();
            } catch (IOException | ClassNotFoundException e) {
                throw new Error(e);
            }

            if(useBufferBackedStreams) {
                CircularBufferInputStream bufferInputStream = new CircularBufferInputStream(buffer);
                try {
                    fromBufferStream = new ObjectInputStream(new BufferedInputStream(bufferInputStream));
                } catch (IOException e) {
                    throw new Error(e);
                }
                fromSocketStream = fromCoordinator;
                fromCoordinator = fromBufferStream;
                toSocketStream = toCoordinator;
                {
                    OutputStream ops;
                    AtomicBoolean flag = new AtomicBoolean(false);
                    try {
                        ops = new OutputStream() {
                            @Override
                            public void write(int b) throws IOException {
                                if (flag.get()) {
                                    throw new IOException("The follower cannot write to the ringbuffer");
                                }
                            }
                        };
                        this.toCoordinator = new ObjectOutputStream(ops);
                        toBufferStream = toCoordinator;
                    } catch (IOException e) {
                        throw new Error(e);
                    }
                    flag.set(true);
                }
            }
            else {
                fromSocketStream = fromCoordinator;
                fromBufferStream = fromCoordinator;
                toSocketStream = toCoordinator;
                toBufferStream = toCoordinator;
            }

        }
    }

    private static Object clinit = new Object();

    protected byte[] getClinit(String cls){
        synchronized (Follower.class) {
            try {
                clinitOut.writeObject(cls);
                clinitOut.flush();
                clinitOut.reset();

                Object o = clinitIn.readObject();
                return (byte[]) o;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        throw new Error("Hit unreachable code...?");
    }

    @Override
    public void clinitStart(String cls) {
        JMVXRuntime.enter();
    }

    @Override
    public void clinitEnd(String cls) {
        JMVXRuntime.exit();
    }

    @Deprecated
    public void inDepthClinitStart(String cls) {
        JMVXRuntime.unsafe.monitorEnter(Follower.class); //mimic the leader's concurrency structure
        JMVXRuntime.clock.setInClinit(true);
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

            this.monitorenter(clinit);

        } finally {
            JMVXRuntime.exit();
        }
    }

    @Deprecated
    public void inDepthClinitEnd(String cls) {
        this.monitorexit(clinit);
        fromCoordinator = clinitsStreams.pop();
        inClinit--;
        JMVXRuntime.clock.setInClinit(inClinit != 0);
        JMVXRuntime.unsafe.monitorExit(Follower.class);
    }

    public void connect(int port) {
        if (!connected) {
            Boolean coordinatorReady;
            int clockIndex;
            while (true) {
                try {
                    //Uncomment if switching back to inDepthClinit
                    //setupClinitSocket();

                    followerSocket = AFUNIXSocket.newInstance();
                    followerSocket.setAncillaryReceiveBufferSize(1024);
                    followerSocket.connect(Coordinator.FOLLOWER_COMMS_ADDR);
                    toCoordinator = new ObjectOutputStream(followerSocket.getOutputStream());

                    toCoordinator.writeObject(Thread.currentThread().getName());
                    toCoordinator.flush();

                    fromCoordinator = new ObjectInputStream(followerSocket.getInputStream());
                    clockIndex = fromCoordinator.readInt();

                    sendMode();
                    coordinatorReady = fromCoordinator.readBoolean();
                    while (!coordinatorReady) {
                        try {
                            coordinatorReady = fromCoordinator.readBoolean();
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                    break;
                } catch (IOException e) {
                    //System.out.println("Follower to Coordinator Connection Failed. Retrying...");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }
            JMVXRuntime.clock.registerNewThread(clockIndex);
            connected = true;

            synchronized (connections){
                connections.add(followerSocket);
            }
        }

        log = Logger.getLogger(Follower.class);
        log.info("Checksum communication enabled: " + checksumCommunicationEnabled);
    }

    @Deprecated
    private synchronized void setupClinitSocket() throws IOException {
        //already made the socket, skip
        if(clinitSocket == null) {
            //make the clinit Socket
            clinitSocket = AFUNIXSocket.newInstance();
            clinitSocket.connect(Coordinator.FOLLOWER_COMMS_ADDR);
            clinitOut = new ObjectOutputStream(clinitSocket.getOutputStream());

            clinitOut.writeObject("clinit");
            clinitOut.flush();

            clinitIn = new ObjectInputStream(clinitSocket.getInputStream());
        }
    }

    /**
     * read 1
     * never used in batik
     * @param is
     * @return
     * @throws IOException
     */
    @Override
    public int read(JMVXInputStream is) throws IOException {
        int ret = 0;
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Leader.Read) {
            ret = ((Leader.Read) read).ret;
            Leader.debug(is, ret);
            try {
                if(!(is instanceof JMVXSocketInputStream))
                    ((InputStream) is).skip(1);
            } catch (Exception e) { }
        } else if (read instanceof IOException) {
            throw ((IOException) read);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.read((InputStream) is);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Read.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to read a byte");
            return (int) divergenceStatus.returnValue;
        }

        return ret;
    }

    protected Object receiveObjectFromCoordinator() {
        try {
            objectCounter++;
            return fromCoordinator.readObject();
        }catch (ClassNotFoundException | IOException e){
            //an error with JMVX
            //OR the end of a recording's log
            //e.printStackTrace();
            return null;
        }
    }

    private Object receiveObjectOverSocket(){
        try {
            objectCounter++;
            return fromSocketStream.readObject();
        }catch (ClassNotFoundException | IOException e){
            return null;
        }
    }

    /**
     * read 2
     * never used in batik
     * @param is
     * @param bytes
     * @return
     * @throws IOException
     */
    @Override
    public int read(JMVXInputStream is, byte[] bytes) throws IOException {
        int ret = 0;
        if (readsFromDisk && Utils.isInputStreamFromDisk(is)) {
            int bytesRead = is.$JMVX$read(bytes);
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
        Object read = receiveObjectFromCoordinator();

        if (read instanceof Leader.ReadB) {
            Leader.debug(is, ret, bytes.length);
            Leader.ReadB r = (Leader.ReadB) read;
            ret = r.ret;
            if (ret == -1)
                return ret;
            System.arraycopy(r.b, 0, bytes, 0, ret);
            try {
                //skip if not is instanceof JMVXSocketInputStream (blank interface we will inject)
                if(!(is instanceof JMVXSocketInputStream))
                    ((InputStream) is).skip(ret);
            } catch (Exception e) { }
        } else if (read instanceof IOException) {
            throw ((IOException) read);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.read((InputStream) is, bytes);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Read.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to read the string: " + new String(bytes));
            return (int) divergenceStatus.returnValue;
        }

        return ret;
    }

    /**
     * raed 3
     * @param is
     * @param bytes
     * @param off
     * @param len
     * @return
     * @throws IOException
     */
    @Override
    public int read(JMVXInputStream is, byte[] bytes, int off, int len) throws IOException {
        int ret = 0;
        if (readsFromDisk && Utils.isInputStreamFromDisk(is)) {
            Leader.debug(is, ret, bytes.length, off, len);
            int spaceInBuffer = Math.min(len, bytes.length - off);
            int bytesRead = is.$JMVX$read(bytes, off, Math.min(len, spaceInBuffer));
            spaceInBuffer -= bytesRead;
            int availableBytes = is.$JMVX$available();

            while (availableBytes > 0 && spaceInBuffer > 0) {
                bytesRead += is.$JMVX$read(bytes, bytesRead + off, Math.min(availableBytes, spaceInBuffer));
                availableBytes = is.$JMVX$available();
                spaceInBuffer -= bytesRead;
            }
            return bytesRead;
        }

        Object read;
        if(circularBufferCommunicationEnabled) {
            if (avoidObjectSerialization) {
                int byteOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
                byte[] data = buffer.readData(true);
                long byteLen = JMVXRuntime.unsafe.getLong(data, byteOffset);
                int recievedLen = JMVXRuntime.unsafe.getInt(data, byteOffset + Long.BYTES + byteLen + Integer.BYTES);
                if(recievedLen < 0) {
                    return recievedLen;
                }
                JMVXRuntime.unsafe.copyMemory(data, byteOffset + Long.BYTES, bytes, byteOffset, byteLen);
                return recievedLen;
            } else {
                read = readFromBuffer();
            }
        } else {
            read = receiveObjectFromCoordinator();
        }

        if (read instanceof Leader.ReadBII) {
            Leader.ReadBII r = (Leader.ReadBII) read;
            Leader.debug(is, ret, bytes.length, off, len);
            ret = r.ret;
            if (r.off != off || r.len != len || bytes.length != r.b.length) {
                log.warn("Divergence");
                Callable<Object> function = () -> {
                    return JMVXRuntime.read((InputStream) is, bytes, off, len);
                };
                Metadata metadata = new Metadata(bytes, len, off, DivergenceSource.FROM_READ);
                // adding the inputstream to the metadata object because the divergence handler might need it
                metadata.is = is;
                // call the divergence handler
                DivergenceStatus<Object> divergenceStatus = divergence(Leader.Read.class.getSimpleName(), read.getClass().getSimpleName(), read, function, metadata);
            }

            if (ret == -1) {
                return ret;
            }
            System.arraycopy(r.b, off, bytes, off, ret);
            try {
                if(!(is instanceof JMVXSocketInputStream))
                    ((InputStream) is).skip(ret);
            } catch (Exception ex) { }
        } else if (read instanceof IOException) {
            throw ((IOException) read);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.read((InputStream) is, bytes, off, len);
            };
            Metadata metadata = new Metadata(bytes, len, off, DivergenceSource.FROM_READ);
            metadata.is = is;
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Read.class.getSimpleName(), read.getClass().getSimpleName(), read, function, metadata);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to read the string: " + new String(bytes, off, len));
            return (int) divergenceStatus.returnValue;
        }

        return ret;
    }
    @Override
    public int available(JMVXInputStream is) throws IOException{
        int ret = 0;
        Object read = receiveObjectFromCoordinator();
        if (JMVXRuntime.avoidObjectSerialization && read instanceof byte[]) {
            byte[] bb = (byte[]) read;
            return  ((bb[0] & 0xFF) << 24) |
                    ((bb[1] & 0xFF) << 16) |
                    ((bb[2] & 0xFF) << 8 ) |
                    ((bb[3] & 0xFF) << 0 );
        } else if (read instanceof Leader.Available) {
            Leader.Available a = (Leader.Available) read;
            ret = a.ret;
        } else if (read instanceof IOException) {
            throw (IOException) read;
        } else {
            Callable<Object> function = () -> {
                return is.$JMVX$available();
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Read.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
            Integer i = (Integer) divergenceStatus.returnValue;
            return i.intValue();
        }
        return ret;
    }

    @Override
    public void close(JMVXOutputStream os) throws IOException {
        try {
            os.$JMVX$close();
        } catch (IOException e) {
            throw e;
        }
    }

    public void flush(JMVXOutputStream os) throws IOException {
        try {
            os.$JMVX$flush();
        } catch (IOException e) {
            throw e;
        }
    }

    /**
     * write 1
     * @param os
     * @param b
     * @throws IOException
     */
    @Override
    public void write(JMVXOutputStream os, int b) throws IOException {
        Object write;
        if (circularBufferCommunicationEnabled) {
            if (avoidObjectSerialization) {
                byte[] data = buffer.readData(true);
                int rb = JMVXRuntime.unsafe.getInt(data, Unsafe.ARRAY_BYTE_BASE_OFFSET);
                verifyWriteOb(os, b, rb);
                return;
            }
            write = readFromBuffer();
        } else {
            write = receiveObjectFromCoordinator();
        }

        if (write instanceof Leader.Write) {
            Leader.Write w = (Leader.Write) write;
            int rb = w.b;
            verifyWriteOb(os, b, rb);
        } else if (write instanceof IOException) {
            throw ((IOException) write);
        } else {
            Callable<Object> function = () -> {
                JMVXRuntime.write((OutputStream) os, b);
                return true;
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Write.class.getSimpleName(), write.getClass().getSimpleName(), write, function);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to write the character: " + (char) b);
            return;
        }
    }

    // divergenceOrSwitchRoles

    public enum DivergenceSource { NONE, FROM_READ, FROM_WRITE };

    public static class Metadata {
        public byte[] buffer;
        public int offset;
        public int length;
        public boolean isOffsetPresent;

        // from represents which kind of method this metadata is coming from
        // There are 3 cases: 1) a read, 2) a write or 3) anything else
        public DivergenceSource source;

        // original buffer is the buffer that is coming from the leader
        // Similarly for originalOffset and originalLength
        public byte[] originalBuffer;
        public int originalOffset;
        public int originalLength;

        public JMVXInputStream is;

        Metadata() {}
        Metadata(byte[] buffer, int length, int offset) {
            this.buffer = buffer;
            this.length = length;
            this.offset = offset;
        }
        Metadata(byte[] buffer, int length, int offset, DivergenceSource source) {
            this(buffer, length, offset);
            this.source = source;
        }
    }

    protected DivergenceStatus<Object> divergence(String expectedClassName, String actualClassName, Object message, Callable<Object> function) {
        return divergence(expectedClassName, actualClassName, message, function, null);
    }

    private DivergenceStatus<Object> divergence(String expectedClassName, String actualClassName, Object message, Callable<Object> function, Metadata metadata) {
        divergenceStatus.hasDivergenceOccured = true; // explicity setting this field to true here
        // we assume beforehand that things are not going to go well

        if (message instanceof SwitchRolesEvent) {
            Leader strategy = new Leader();
            // set this to something larger than 1 to avoid entering the promote/demote strategy again.
            Leader.one.set(20);
            strategy.reConnect(followerSocket, toCoordinator, fromCoordinator, log);
            JMVXRuntime.setStrategy(new ReentrantStrategy(strategy));
            JMVXRuntime.openExistingFiles();

            try { // case 1
                // we recieved a SwitchRolesEvent, hence we call the lambda to run any pending code
                // stuff the return value of the lambda in the divergenceStatus
                // set the hasDivergenceOccured flag to false and return
                Object o = function.call();
                divergenceStatus.returnValue = o;
                divergenceStatus.hasDivergenceOccured = false;
                divergenceStatus.containsException = false;
            } catch (Exception e) {
                // case 2
                // we recieved a SwitchRolesEvent, hence we call the lambda to run any pending code
                // but the lambda threw an exception
                // we stuff the exception that occured in the placeholder for the return value
                // set the containsException flag to true and
                // set the hasDivergenceOccured flag to false and return
                divergenceStatus.hasDivergenceOccured = false;
                divergenceStatus.returnValue = e;
                divergenceStatus.containsException = true;
            }
            return divergenceStatus;
        } else { // case 3
            // now we know that a divergence has occured because
            // we got an object from the coordinator that we didn't expect
            // so we call our custom divergence handler
            // and send back to the caller whatever return value the divergence handler
            // returned to us
            // Lastly, set the hasDivergenceOccured flag to true and return
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
            List<StackTraceElement> stack = Arrays.asList(stackTraceElements);
            HandlerStatus r = null;
            List<StackTraceElement> filteredStack = stack.stream().filter(element -> !element.getClass().getName().contains("jmvx")).collect(Collectors.toList());

            if (metadata == null) {
                r = divergenceHandler.handleDivergence(filteredStack);
            } else {
                switch (metadata.source) {
                    case FROM_READ: {
                        r = divergenceHandler.handleReadDivergence(filteredStack, metadata);
                    } break;
                    case FROM_WRITE: {
                        r = divergenceHandler.handleWriteDivergence(filteredStack, metadata);
                    }
                }
            }

            if (r.status == DivergenceStatus.STATUS.NOT_OK) {
                // terminating the program if the divergence handler returns NOT_OK
                throw new DivergenceError();
            }

            divergenceStatus.hasDivergenceOccured = true;
            divergenceStatus.returnValue = r.returnValue;
            divergenceStatus.containsException = false;
            log.warn("divergence occured");
            return divergenceStatus;
        }
    }

    @Override
    public void write(JMVXOutputStream os, byte[] bytes) throws IOException {
        Object write;
        if(circularBufferCommunicationEnabled) {
            if (avoidObjectSerialization) {
                byte[] recievedBytes = buffer.readData(true);
                verifyWriteOb(os, bytes, recievedBytes, null);
                return;
            }
            write = readFromBuffer();
        }
        else {
            write = receiveObjectFromCoordinator();
        }

        if (write instanceof Leader.WriteB) {
            Leader.WriteB w = (Leader.WriteB) write;
            byte[] b = w.b;
            verifyWriteOb(os, bytes, b, write);
        } else if (write instanceof IOException) {
            throw ((IOException) write);
        } else {
            Callable<Object> function = () -> {
                JMVXRuntime.write((OutputStream) os, bytes);
                return true;
            };
            Metadata metadata = new Metadata();
            metadata.source = DivergenceSource.FROM_WRITE;
            metadata.buffer = bytes;
            metadata.isOffsetPresent = false;
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.WriteB.class.getSimpleName(), write.getClass().getSimpleName(), write, function, metadata);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to write the string: " + new String(bytes));
            return;
        }
    }

    /**
     * write 3
     * @param os
     * @param bytes
     * @param off
     * @param len
     * @throws IOException
     */
    @Override
    public void write(JMVXOutputStream os, byte[] bytes, int off, int len) throws IOException {
        Object write;
        if(circularBufferCommunicationEnabled) {
            if(avoidObjectSerialization) {
                int byteOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
                byte[] data = buffer.readData(true);
                long byteLen = JMVXRuntime.unsafe.getLong(data, byteOffset);
                byte[] recievedB =  new byte[(int)byteLen];

                JMVXRuntime.unsafe.copyMemory(data, byteOffset + Long.BYTES, recievedB, byteOffset, byteLen);
                int recievedOff = JMVXRuntime.unsafe.getInt(data, byteOffset + Long.BYTES + byteLen);
                int recievedLen = JMVXRuntime.unsafe.getInt(data, byteOffset + Long.BYTES + byteLen  + Integer.BYTES);

                if(recievedOff == -1){
                    throw new IOException();
                }
                if(recievedLen < 0){
                    return;
                }

                verifyWriteOb(os, bytes, recievedB, off, recievedOff, len, recievedLen, null);
                return;
            }
            write = readFromBuffer();
        }
        else {
            write = receiveObjectFromCoordinator();
        }

        if (write instanceof Leader.WriteBII) {
            Leader.WriteBII w = (Leader.WriteBII) write;
            verifyWriteOb(os, bytes, w.b, off, w.off, len, w.len, write);
        } else if (write instanceof IOException) {
            throw ((IOException) write);
        } else {
            Callable<Object> function = () -> {
                JMVXRuntime.write((OutputStream) os, bytes, off, len);
                return true;
            };
            Metadata metadata = new Metadata();
            metadata.source = DivergenceSource.FROM_WRITE;
            metadata.buffer = bytes;
            metadata.length = len;
            metadata.offset = off;
            metadata.isOffsetPresent = false;
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Write.class.getSimpleName(), write.getClass().getSimpleName(), write, function);

            if (divergenceStatus.containsException) {
                throw (IOException) divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to write the string: " + new String(bytes, off, len));
        }
    }

    @Override
    public boolean canRead(JMVXFile f) {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileCanRead) {
            ret = ((Leader.FileCanRead) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.canRead((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileCanRead.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to check if the file: " + f.getClass().getSimpleName() + " can be read");

            return (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    /**
     * never reached in batik
     * @param f
     * @return
     */
    @Override
    public boolean canWrite(JMVXFile f) {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileCanWrite) {
            ret = ((Leader.FileCanWrite) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.canWrite((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileCanWrite.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to write to the file: " + f.getClass().getSimpleName());

            return (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public boolean createNewFile(JMVXFile f) throws IOException {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileCreateNewFile) {
            ret = ((Leader.FileCreateNewFile) file).ret;
        } else if (file instanceof IOException) {
            throw ((IOException) file);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.createNewFile((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileCreateNewFile.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to create file: " + f.getClass().getSimpleName());

            return (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public boolean delete(JMVXFile f) {
        try {
            boolean ret = false;
            if (useBufferBackedStreams) {
                this.fromCoordinator = fromSocketStream;
                this.toCoordinator = toSocketStream;
            }
            Object file = receiveObjectFromCoordinator();
            if (file instanceof Leader.FileDelete) {
                ret = ((Leader.FileDelete) file).ret;
                try {
                    String status = (String) fromCoordinator.readObject();
                    if (status.equals("waiting")) {
                        toCoordinator.writeObject("synced");
                        toCoordinator.flush();
                        toCoordinator.reset();
                        ret = (boolean) fromCoordinator.readObject();
                    }
                }catch (ClassNotFoundException | IOException e){
                    e.printStackTrace();
                }
            } else{
                Callable<Object> function = () -> {
                    return JMVXRuntime.delete((File) f);
                };
                DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileDelete.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

                if (divergenceStatus.containsException) {
                    throw (SecurityException) divergenceStatus.returnValue;
                }
                log.warn("The follower is trying to delete file: " + f.getClass().getSimpleName());
                return (boolean) divergenceStatus.returnValue;
            }
            return ret;
        }
        finally {
            if (useBufferBackedStreams) {
                this.fromCoordinator = fromBufferStream;
                this.toCoordinator = toBufferStream;
            }
        }
    }

    @Override
    public boolean exists(JMVXFile f) {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileExists) {
            ret = ((Leader.FileExists) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.exists((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileExists.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to check if file: " + f.getClass().getSimpleName() + " can be read");

            return (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    /**
     * never used in batik
     * @param f
     * @return
     */
    @Override
    public File getAbsoluteFile(JMVXFile f) {
        File ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileGetAbsoluteFile) {
            ret = ((Leader.FileGetAbsoluteFile) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.getAbsoluteFile((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileGetAbsoluteFile.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the canonical file: " + f.getClass().getSimpleName());

            return (File)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public String getAbsolutePath(JMVXFile f) {
        String ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileGetAbsolutePath) {
            ret = ((Leader.FileGetAbsolutePath) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.getAbsolutePath((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileGetAbsolutePath.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the canonical file: " + f.getClass().getSimpleName());

            return (String)divergenceStatus.returnValue;
        }

        return ret;
    }

    /**
     * never used in batik
     * @param f
     * @return
     * @throws IOException
     */
    @Override
    public File getCanonicalFile(JMVXFile f) throws IOException {
        File ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileGetCanonicalFile) {
            ret = ((Leader.FileGetCanonicalFile) file).ret;
        } else if (file instanceof IOException) {
            throw ((IOException) file);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.getCanonicalFile((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileGetCanonicalFile.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the canonical file: " + f.getClass().getSimpleName());

            return (File)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public String getCanonicalPath(JMVXFile f) throws IOException {
        String ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileGetCanonicalPath) {
            ret = ((Leader.FileGetCanonicalPath) file).ret;
        } else if (file instanceof IOException) {
            throw ((IOException) file);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.getCanonicalPath((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileGetCanonicalPath.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the parent file of file: " + f.getClass().getSimpleName());

            return (String)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public String getName(JMVXFile f) {
        String ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileGetName) {
            ret = ((Leader.FileGetName) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.getName((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileGetName.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the parent file of file: " + f.getClass().getSimpleName());

            return (String)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public File getParentFile(JMVXFile f) {
        File ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileGetParentFile) {
            ret = ((Leader.FileGetParentFile) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.getParentFile((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileGetParentFile.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the parent file of file: " + f.getClass().getSimpleName());

            return (File)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public String getPath(JMVXFile f) {
        throw new UnsupportedOperationException("Shouldn't get here");
        /*
        String ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileGetPath) {
            ret = ((Leader.FileGetPath) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.getPath((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileGetPath.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the directory of file: " + f.getClass().getSimpleName());

            return (String)divergenceStatus.returnValue;
        }

        return ret;*/
    }

    @Override
    public boolean isDirectory(JMVXFile f) {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileIsDirectory) {
            ret = ((Leader.FileIsDirectory) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.isDirectory((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileIsDirectory.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the directory of file: " + f.getClass().getSimpleName());

            return (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    /**
     * never called in batik
     * @param f
     * @return
     */
    @Override
    public long length(JMVXFile f) {
        long ret = 0;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileLength) {
            ret = ((Leader.FileLength) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.length((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileLength.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the length of file: " + f.getClass().getSimpleName());

            return (long)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public String[] list(JMVXFile f) {
        String[] ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileList) {
            ret = ((Leader.FileList) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.list((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileList.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the list of filename: " + f.getClass().getSimpleName());

            return (String[])divergenceStatus.returnValue;
        }

        return ret;
    }

    public String[] list(JMVXFile f, FilenameFilter filter) {
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileListFilter) {
            String[] unfiltered = ((Leader.FileListFilter) file).unfiltered;
            if (unfiltered == null)
                return null;
            return Leader.filterFiles(unfiltered, filter);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.list((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileList.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the list of filename: " + f.getClass().getSimpleName());

            return (String[])divergenceStatus.returnValue;
        }
    }

    @Override
    public File[] listFiles(JMVXFile f) {
        File[] ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileListFiles) {
            ret = ((Leader.FileListFiles) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.listFiles((File) f);
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileListFiles.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to list files: " + f.getClass().getSimpleName());

            return (File[])divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public File[] listFiles(JMVXFile f, FilenameFilter filter) {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.FileListFilesFilter){
            //Have to run the filter over the entire listing of files to capture the results of side effects
            File[] unfiltered = ((Leader.FileListFilesFilter)read).unfiltered;
            return Leader.filterFiles(unfiltered, filter);
        }else if(read instanceof SecurityException){
            throw (SecurityException) read;
        }else{
            Callable<Object> function = () -> f.$JMVX$listFiles(filter);
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileListFilesFilter.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            return (File[])divergenceStatus.returnValue;
        }
    }

    @Override
    public boolean mkdir(JMVXFile f) {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileMkdir) {
            ret = ((Leader.FileMkdir) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.mkdir((File) f);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileMkdir.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }

            log.warn("The follower is trying to make dir " + f.getClass().getSimpleName());

            return (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public boolean mkdirs(JMVXFile f) {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileMkdirs) {
            ret = ((Leader.FileMkdirs) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.mkdirs((File)f);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileMkdirs.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            log.warn("The follower is trying to make the directory path: " + f.$JMVX$getAbsolutePath());

            return (boolean)divergenceStatus.returnValue;

        }

        return ret;
    }

    /**
     * never used in batik
     * @param f
     * @param dest
     * @return
     */
    @Override
    public boolean renameTo(JMVXFile f, File dest) {
        boolean ret = false;
        File d = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileRenameTo) {
            ret = ((Leader.FileRenameTo) file).ret;
            d = ((Leader.FileRenameTo) file).f;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.renameTo((File) f, dest);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileRenameTo.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }

            log.warn("The follower is trying to rename file " + f.getClass().getSimpleName() + " to " + dest.getName());

            return (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    /**
     * never used in batik
     * @param f
     * @return
     */
    @Override
    public boolean setReadOnly(JMVXFile f) {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileSetReadOnly) {
            ret = ((Leader.FileSetReadOnly) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.setReadOnly((File) f);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileSetReadOnly.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }

            log.warn("The follower is trying to set read only on file " + f.getClass().getSimpleName());

            return (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public URL toURL(JMVXFile f) throws MalformedURLException {
        URL ret = null;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileToURL) {
            ret = ((Leader.FileToURL) file).ret;
        } else if (file instanceof MalformedURLException) {
            throw ((MalformedURLException) file);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.toURL((File) f);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileToURL.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (MalformedURLException)divergenceStatus.returnValue;
            }

            log.warn("The follower is trying to convert to a URL file " + f.getClass().getSimpleName());

            return (URL)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public long lastModified(JMVXFile f) {
        long ret = 0;
        Object file = receiveObjectFromCoordinator();
        if (file instanceof Leader.FileLastModified) {
            ret = ((Leader.FileLastModified) file).ret;
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.lastModified((File) f);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileLastModified.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get last modified date of file" + f.$JMVX$getName());

            return (long)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public boolean isFile(JMVXFile f) {
        boolean ret = false;
        Object file = receiveObjectFromCoordinator();
        if(file instanceof Leader.IsFile) {
            ret = ((Leader.IsFile) file).ret;
        }else if(file instanceof SecurityException){
            throw (SecurityException)file;

        }else{
            Callable<Object> function = () -> {
                return JMVXRuntime.isFile((File) f);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.IsFile.class.getSimpleName(), file.getClass().getSimpleName(), file, function);

            if(divergenceStatus.containsException){
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to see if this is a valid file:" + f.$JMVX$getName());

            ret = (boolean)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public void open(JMVXFileOutputStream fos, String name, boolean append) throws FileNotFoundException {
        Object fileOS;
        fileOS = receiveObjectFromCoordinator();

        if (fileOS instanceof Leader.FileOutputStreamOpen) {
            Leader.FileOutputStreamOpen foso = (Leader.FileOutputStreamOpen) fileOS;
            String n = foso.name;
            boolean a = foso.append;
            if (!n.equals(name) || a != append) {
                log.warn("Potential mismatch! Difference in files opened");
                log.warn("The leader tried to open: " + n + " " + a);
                log.warn("The follower is trying to open: " + name + " " + append);
            }
            fos.$JMVX$open("/dev/null", false);
            JMVXRuntime.openedFiles.put(fos, name);
        } else if (fileOS instanceof FileNotFoundException) {
            throw ((FileNotFoundException) fileOS);
        } else {
            Callable<Object> function = () -> {
                JMVXRuntime.open((FileOutputStream) fos, name, append);
                return null; // returning null because there's nothing to return for open - it is void
            };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileOutputStreamOpen.class.getSimpleName(), fileOS.getClass().getSimpleName(), fileOS, function);

            // if the divergenceStatus contains an exception, we
            // throw the exception back to the caller
            // casting it to FileNotFoundException for now but that's risky!
            if (divergenceStatus.containsException) {
                throw (FileNotFoundException)divergenceStatus.returnValue;
            }
            // in the case that a divergence occured, we log that a divergence has occured
            // and return the returnValue in divergenceStatus
            // However, if a divergence has occured, the returnValue will contain the return value the divergence handler
            // otherwise the returnValue will contain the return value of the lambda
            // In either case we simply return because open() returns void
            log.warn("The follower is trying to open file " + fos.getClass().getSimpleName());
            return;
            // in this case we have just a return because the function is void
//                Consumer<Void> function = (doNotUse) -> {
//                    try {
//                        JMVXRuntime.open((FileOutputStream) fos, name, append);
//                    }  catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    }
//                };
//                boolean isDiverged = divergence(Leader.Write.class.getSimpleName(), fileOS.getClass().getSimpleName(), fileOS, function);
//                if (isDiverged) {
//                    log.warn("The follower is trying to set read only on file " + fos.getClass().getSimpleName());
//                }
//                return;
        }
    }

    @Override
    public void open(JMVXFileInputStream fis, String name) throws FileNotFoundException {
        Object fileIS;
        fileIS = receiveObjectFromCoordinator();

        if (fileIS instanceof Leader.FileInputStreamOpen) {
            Leader.FileInputStreamOpen fiso = (Leader.FileInputStreamOpen) fileIS;
            String n = fiso.name;
            if (!n.equals(name)) {
                log.warn("Potential mismatch! Difference in files opened");
                log.warn("The leader tried to open: " + n);
                log.warn("The follower is trying to open: " + name);
            }
            fis.$JMVX$open(name);
        } else if (fileIS instanceof FileNotFoundException) {
            throw ((FileNotFoundException) fileIS);
        } else {

            Callable<Object> function = () -> {
                JMVXRuntime.open((FileInputStream) fis, name);
                return null;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileInputStreamOpen.class.getSimpleName(), fileIS.getClass().getSimpleName(), fileIS, function);

            if (divergenceStatus.containsException) {
                throw (FileNotFoundException)divergenceStatus.returnValue;
            }
        }
    }

    @Override
    public String fileOutputStream(String name) {
        return "/dev/null";
    }

    @Override
    public boolean fileOutputStream(boolean append) {
        return false;
    }

    @Override
    public File fileOutputStream(File file) {
        return new File("/dev/null");
    }

    @Override
    public FileDescriptor fileOutputStream(FileDescriptor fdObj) {
        //TODO: Figure out how to handle properly
        FileOutputStream fos;
        try {
            fos = new FileOutputStream("/dev/null");
            return fos.getFD();
        } catch (IOException ignored) {
        }
        return fdObj;
    }

    @Override
    public void sync(FileDescriptor fd) throws SyncFailedException {
        Object obj = receiveObjectFromCoordinator();
        if (obj instanceof Leader.FileDescriptorSync) {
            return;
        } else if (obj instanceof SyncFailedException) { //throw an exception if we are given one
            throw (SyncFailedException) obj;
        } else {
            Callable<Object> function = () -> {
                fd.sync();
                return null;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileDescriptorSync.class.getSimpleName(), obj.getClass().getSimpleName(), obj, function);

            if (divergenceStatus.containsException) {
                throw (SyncFailedException) divergenceStatus.returnValue;
            }
        }
    }

    public void connect(JMVXSocket sock, SocketAddress endpoint, int timeout) throws IOException {
        Object obj = receiveObjectOverSocket();
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
                    Socket accepted = junkServer.accept();
                    //switch the file descriptor the socket uses
                    FileDescriptor[] fds = followerSocket.getReceivedFileDescriptors();
                    FileDescriptorUtils.swapFd((Socket) sock, fds[0], true);
                    //close the old connection used by the loopback
                    accepted.close();
                }
            } catch (Exception e) {
                log.error("Error connecting to junk server");
                e.printStackTrace();
            }
            //FileDescriptorUtils.setPort((Socket) sock, ((InetSocketAddress)endpoint).getPort());
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
        Object obj = receiveObjectOverSocket();
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
            FileDescriptor[] fds = followerSocket.getReceivedFileDescriptors();
            try {
                FileDescriptorUtils.swapFd((ServerSocket) serv, fds[0], true);
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
        Object obj = receiveObjectOverSocket();
        if(obj instanceof Leader.Accept){
            //don't actually need the object
            Leader.Accept acc = (Leader.Accept)obj;

            //create a new socket and swap its file descriptor
            synchronized (junkServer){
                new Socket(junkAddress.getHostName(), junkAddress.getPort());
                Socket sock = junkServer.accept();
                FileDescriptor[] fds = followerSocket.getReceivedFileDescriptors();
                try {
                    FileDescriptorUtils.swapFd(sock, fds[0], true);
                }catch (Exception e){
                    e.printStackTrace();
                }
                return sock;
            }
            //FileDescriptorUtils.setPort(sock, acc.localPort);
        }else if(obj instanceof IOException){ //throw an exception if we are given one
            throw (IOException) obj;
        }else{
            Callable<Object> function = () -> {
                return JMVXRuntime.accept((ServerSocket) serv);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Accept.class.getSimpleName(), obj.getClass().getSimpleName(), obj, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException) divergenceStatus.returnValue;
            }

            return (Socket) divergenceStatus.returnValue;
        }
    }

    public void monitorenter(Object o) {
//        if (JMVXRuntime.clock.size() == 1) {
//            JMVXRuntime.unsafe.monitorEnter(o);
//            return;
//        }

        Object me;
        if(circularBufferCommunicationEnabled) {
            if(avoidObjectSerialization) {
                byte[] clockCopyBytes = buffer.readData(true);
                long[] clockCopy = new long[(clockCopyBytes.length)/Long.BYTES];

                JMVXRuntime.unsafe.copyMemory(clockCopyBytes, Unsafe.ARRAY_BYTE_BASE_OFFSET,
                        clockCopy, Unsafe.ARRAY_LONG_BASE_OFFSET,
                        clockCopyBytes.length);
                JMVXRuntime.clock.sync(clockCopy, true);
                JMVXRuntime.unsafe.monitorEnter(o);
                JMVXRuntime.clock.increment(null);
                return;
            }
            me = readFromBuffer();
        }
        else {
            me = receiveObjectFromCoordinator();
        }

        if (me instanceof Leader.MonitorEnter) {
            Leader.MonitorEnter monEnter = (Leader.MonitorEnter) me;
            
            JMVXRuntime.clock.sync(monEnter.clock, true);

            JMVXRuntime.unsafe.monitorEnter(o);
            JMVXRuntime.clock.increment(null);
        } else {
            divergence(Leader.MonitorEnter.class.getSimpleName(), me.getClass().getSimpleName());
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
        // Release the lock while waiting, just like Object.wait does
        int i = 0;
        while(Thread.holdsLock(o)) {
            i += 1;
            JMVXRuntime.unsafe.monitorExit(o);
        }

        Object w;
        if(circularBufferCommunicationEnabled) {
            if (avoidObjectSerialization) {
                byte[] clockCopyBytes = buffer.readData(true);
                long[] clockCopy = new long[(clockCopyBytes.length) / Long.BYTES];

                JMVXRuntime.unsafe.copyMemory(clockCopyBytes, Unsafe.ARRAY_BYTE_BASE_OFFSET,
                        clockCopy, Unsafe.ARRAY_LONG_BASE_OFFSET,
                        clockCopyBytes.length);
                JMVXRuntime.clock.sync(clockCopy, true);
                while (i != 0) {
                    JMVXRuntime.unsafe.monitorEnter(o);
                    i -= 1;
                }
                JMVXRuntime.clock.increment(null);
                return;
            }
            w = readFromBuffer();
        }else{
            w = receiveObjectFromCoordinator();
        }

        if (w instanceof Leader.Wait) {
            Leader.Wait wait = (Leader.Wait) w;

            // Wait for our turn to get the lock
            JMVXRuntime.clock.sync(wait.clock, true);

            // Get the lock
            while (i != 0) {
                JMVXRuntime.unsafe.monitorEnter(o);
                i -= 1;
            }

            JMVXRuntime.clock.increment(null);
        } else {
            Callable<Object> function = () -> {
                JMVXRuntime.wait((Object) o, timeout, nanos);
                return null;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.Wait.class.getSimpleName(), w.getClass().getSimpleName(), w, function);

            if (divergenceStatus.containsException) {
                throw (InterruptedException) divergenceStatus.returnValue;
            }
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
            if (useBufferBackedStreams)
                this.toCoordinator = toSocketStream;
            toCoordinator.writeObject(CoordinatorThread.exitMessage); //Exit
            toCoordinator.flush();
            //followerSocket.close();
            if (useBufferBackedStreams)
                this.toCoordinator = toBufferStream;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void systemExit(int status) {
        exitLogic();
        synchronized (connections) {
            for (AFUNIXSocket sock : connections) {
                try {
                    sock.close();
                } catch (IOException e) {
                    log.info("Failed to close a socket in Follower.systemExit");
                    //e.printStackTrace();
                }
            }
        }
    }

    @Override
    public int read(JMVXRandomAccessFile f) throws IOException {
        int ret = 0;
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Leader.RARead) {
            ret = ((Leader.RARead) read).ret;
            Leader.debug(f, ret, Optional.empty(), Optional.empty(), Optional.empty());
        } else if (read instanceof IOException) {
            throw ((IOException) read);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.read((RandomAccessFile) f);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RARead.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException) divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to read from a random access file ()" + f.getClass().getSimpleName());

            return (int) divergenceStatus.returnValue;
        }
        return ret;
    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] bytes) throws IOException {
        int ret = 0;
        Object read = receiveObjectFromCoordinator();
        if (read instanceof Leader.RAReadB) {
            Leader.debug(f, ret, Optional.of(bytes.length), Optional.empty(), Optional.empty());
            Leader.RAReadB r = (Leader.RAReadB) read;
            ret = r.ret;
            if (ret == -1)
                return ret;
            System.arraycopy(r.b, 0, bytes, 0, ret);
        } else if (read instanceof IOException) {
            throw ((IOException) read);
        } else {
            Callable<Object> function = () -> {
                    return JMVXRuntime.read((RandomAccessFile) f, bytes);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RAReadB.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to read from a random access file (b[]) " + f.getClass().getSimpleName());

            return (int)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] bytes, int off, int len) throws IOException {
        Object read;
        if (circularBufferCommunicationEnabled) {
            if (avoidObjectSerialization) {
                int byteOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
                byte[] data = buffer.readData(true);
                long byteLen = JMVXRuntime.unsafe.getLong(data, byteOffset);
                int recievedLen = JMVXRuntime.unsafe.getInt(data, byteOffset + Long.BYTES + byteLen  + Integer.BYTES);

                if(recievedLen < 0) {
                    return recievedLen;
                }
                JMVXRuntime.unsafe.copyMemory(data, byteOffset + Long.BYTES, bytes, byteOffset + off*Unsafe.ARRAY_BYTE_INDEX_SCALE, recievedLen);

                return recievedLen;
            } else {
                read = readFromBuffer();
            }
        } else {
            read = receiveObjectFromCoordinator();//fromCoordinator.readObject();
        }

        int ret = 0;
        if (read instanceof Leader.RAReadBII) {
            Leader.RAReadBII r = (Leader.RAReadBII) read;
            Leader.debug(f, ret, Optional.of(bytes.length), Optional.of(off), Optional.of(len));
            ret = r.ret;
            if (r.off != off || r.len != len || bytes.length != r.b.length)
                log.warn("Divergence");
            if (ret == -1) {
                return ret;
            }
            System.arraycopy(r.b, r.off, bytes, off, ret);
        } else if (read instanceof IOException) {
            throw ((IOException) read);
        } else {
            Callable<Object> function = () -> {
                return JMVXRuntime.read((RandomAccessFile) f, bytes, off, len);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RAReadBII.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to read from random access file (b[], off, len)" + f.getClass().getSimpleName());

            return (int)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public void open(JMVXRandomAccessFile raf, String name, int mode) throws FileNotFoundException {
        Object fileRAF;
        fileRAF = receiveObjectFromCoordinator();

        if (fileRAF instanceof Leader.RAOpen) {
            Leader.RAOpen fiso = (Leader.RAOpen) fileRAF;
            String n = fiso.name;
            int m = fiso.mode;
            if (!n.equals(name) || m != mode) {
                log.warn("Potential mismatch! Difference in files opened");
                log.warn("The leader tried to open: " + n + " with mode " + Integer.toString(m));
                log.warn("The follower is trying to open: " + name + " with mode " + Integer.toString(mode));
            }
            raf.$JMVX$open("/dev/null", m);
            //raf.$JMVX$open(name, m);
            //TODO, need to extend to track random access files as well as input/output streams
            //JMVXRuntime.openedFiles.put(raf, name);
        } else if (fileRAF instanceof FileNotFoundException) {
            throw ((FileNotFoundException) fileRAF);
        } else {
            divergence(Leader.RAOpen.class.getSimpleName(), fileRAF.getClass().getSimpleName());

            Callable<Object> function = () -> {
                JMVXRuntime.open((RandomAccessFile) raf, name, mode);
                return true;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RAOpen.class.getSimpleName(), fileRAF.getClass().getSimpleName(), fileRAF, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to open a random access file " + raf.getClass().getSimpleName());
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
        Object write = receiveObjectFromCoordinator();
        if (write instanceof Leader.RAWrite) {
            Leader.RAWrite w = (Leader.RAWrite) write;
            int rb = w.b;
            if (rb != b)
                log.warn("Potential mismatch! Difference in byte written");
        } else if (write instanceof IOException) {
            throw ((IOException) write);
        } else {
            Callable<Object> function = () -> {
                JMVXRuntime.write((RandomAccessFile) raf, b);
                return true;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RAWrite.class.getSimpleName(), write.getClass().getSimpleName(), write, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to write to a random access file (b)" + raf.getClass().getSimpleName());
        }
    }

    @Override
    public void write(JMVXRandomAccessFile raf, byte[] bytes) throws IOException {
        Object write = receiveObjectFromCoordinator();
        if (write instanceof Leader.RAWriteB) {
            Leader.RAWriteB w = (Leader.RAWriteB) write;
            byte[] b = w.b;
            if (!Arrays.equals(bytes, b))
                log.warn("Potential mismatch! Difference in bytes written");
        } else if (write instanceof IOException) {
            throw ((IOException) write);
        } else {
            Callable<Object> function = () -> {
                JMVXRuntime.write((RandomAccessFile) raf, bytes);
                return true;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RAWriteB.class.getSimpleName(), write.getClass().getSimpleName(), write, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying write to a random access file (b[])" + raf.getClass().getSimpleName());
        }
    }

    @Override
    public void write(JMVXRandomAccessFile raf, byte[] bytes, int off, int len) throws IOException {
        Object write = receiveObjectFromCoordinator();
        if (write instanceof Leader.RAWriteBII) {
            Leader.RAWriteBII w = (Leader.RAWriteBII) write;
            int o = w.off;
            int l = w.len;
            byte[] b = w.b;
            byte[] followerBytes = new byte[len];
            System.arraycopy(bytes, off, followerBytes, 0, len);
            byte[] leaderBytes   = new byte[w.len];
            System.arraycopy(w.b, w.off, leaderBytes, 0, w.len);
            if (o != off || l != len || !Arrays.equals(leaderBytes, followerBytes)) {
                log.warn("Potential mismatch! Difference in bytes written");
                log.warn("The leader wrote the string: " + new String(w.b, w.off, w.len));
                log.warn("The follower is trying to write the string: " + new String(bytes, off, len));
            }
        } else if (write instanceof IOException) {
            throw ((IOException) write);
        } else {
            Callable<Object> function = () -> {
                JMVXRuntime.write((RandomAccessFile) raf, bytes, off, len);
                return true;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RAWriteBII.class.getSimpleName(), write.getClass().getSimpleName(), write, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to write to a random access file (b[], off, len) " + raf.getClass().getSimpleName());
        }
    }

    @Override
    public long length(JMVXRandomAccessFile f) throws IOException {
        long ret = 0;
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.RALength){
            ret = ((Leader.RALength)read).len;
        }else if(read instanceof IOException){
            throw((IOException) read);
        }else{
            Callable<Object> function = () -> {
                return JMVXRuntime.length((RandomAccessFile) f);
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RALength.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to get the length of a random access file " + read.getClass().getSimpleName());

            return (long) divergenceStatus.returnValue;
        }
        return ret;
    }

    @Override
    public void setLength(JMVXRandomAccessFile f, long newLength) throws IOException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.RASetLength){
            //pass
        }else if(read instanceof IOException){
            throw((IOException) read);
        }else{
            Callable<Object> function = () -> {
                JMVXRuntime.setLength((RandomAccessFile) f, newLength);
                return true;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RASetLength.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to set the length of a random access file " + read.getClass().getSimpleName());
        }
    }

    @Override
    public void seek(JMVXRandomAccessFile f, long pos) throws IOException {
        Object read;

        if (circularBufferCommunicationEnabled) {
            if (avoidObjectSerialization) {
                int byteOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
                byte[] data = buffer.readData(true);
                long byteLen = JMVXRuntime.unsafe.getLong(data, byteOffset);
                int recievedLen = JMVXRuntime.unsafe.getInt(data, byteOffset + Long.BYTES + byteLen  + Integer.BYTES);

                byte[] bytes = new byte[8];
                JMVXRuntime.unsafe.copyMemory(data, byteOffset + Long.BYTES, bytes, byteOffset, 8);

                long l =  ((long) bytes[7] << 56)
                        | ((long) bytes[6] & 0xff) << 48
                        | ((long) bytes[5] & 0xff) << 40
                        | ((long) bytes[4] & 0xff) << 32
                        | ((long) bytes[3] & 0xff) << 24
                        | ((long) bytes[2] & 0xff) << 16
                        | ((long) bytes[1] & 0xff) << 8
                        | ((long) bytes[0] & 0xff);

                // TODO check that l matches pos for divergence
                return;
            } else {
                read = readFromBuffer();
            }
        } else {
            read = receiveObjectFromCoordinator();
        }


        if(read instanceof Leader.RASeek){
            //pass
            //TODO check for divergence on pos argument
        }else if(read instanceof IOException){
            throw((IOException) read);
        }else{
            Callable<Object> function = () -> {
                JMVXRuntime.seek((RandomAccessFile) f, pos);
                return true;
            };

            DivergenceStatus<Object> divergenceStatus = divergence(Leader.RASeek.class.getSimpleName(), read.getClass().getSimpleName(), read, function);

            if (divergenceStatus.containsException) {
                throw (SecurityException)divergenceStatus.returnValue;
            }
            log.warn("The follower is trying to seek in a random access file" + read.getClass().getSimpleName());
        }
    }

    @Override
    public int read(JMVXFileChannel c, ByteBuffer dst) throws IOException {
        int ret = 0;
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.ChannelRead){
            Leader.ChannelRead obj = (Leader.ChannelRead) read;
            dst.put(obj.dst);
            ret = obj.ret;
        }else if(read instanceof IOException){
            throw (IOException) read;
        }else{
            Callable<Object> func = () -> c.$JMVX$read(dst);
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.ChannelRead.class.getSimpleName(), read.getClass().getSimpleName(), read, func);
            if(divergenceStatus.containsException){
                throw (IOException) divergenceStatus.returnValue;
            }
            ret = (int)divergenceStatus.returnValue;
        }

        return ret;
    }

    @Override
    public long size(JMVXFileChannel c) throws IOException {
        long ret = 0;
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.ChannelSize){
            ret = ((Leader.ChannelSize)read).size;
        }else if(read instanceof IOException){
            throw (IOException) read;
        }else{
            Callable<Object> function = () -> c.$JMVX$size();
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.ChannelSize.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (IOException)divergenceStatus.returnValue;
            }
            ret = (long)divergenceStatus.returnValue;
        }
        
        return ret;
    }

    @Override
    public long currentTimeMillis() {
        if (JMVXRuntime.avoidObjectSerialization && JMVXRuntime.circularBufferCommunicationEnabled) {
            byte[] b = new byte[8];
            this.buffer.readData(b, 0, b.length);
            long ret = ((long) b[7] << 56)
                    | ((long) b[6] & 0xff) << 48
                    | ((long) b[5] & 0xff) << 40
                    | ((long) b[4] & 0xff) << 32
                    | ((long) b[3] & 0xff) << 24
                    | ((long) b[2] & 0xff) << 16
                    | ((long) b[1] & 0xff) << 8
                    | ((long) b[0] & 0xff);
            return ret;
        } else {
            Object read = receiveObjectFromCoordinator();
            if(read instanceof Leader.TimeMillis){
                return ((Leader.TimeMillis) read).millis;
            }else{
                Callable<Object> function = () -> System.currentTimeMillis();
                DivergenceStatus<Object> divergenceStatus = divergence(Leader.TimeMillis.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
                return (long)divergenceStatus.returnValue;
            }

        }
    }

    @Override
    public long nanoTime() {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NanoTime){
            return ((Leader.NanoTime) read).nano;
        }else{
            Callable<Object> function = () -> System.nanoTime();
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NanoTime.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            return (long)divergenceStatus.returnValue;
        }
    }

    @Override
    public Path createTempFile(Path dir, String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.CreateTempFile){
            return (((Leader.CreateTempFile) read).ret).toPath();
        }else{
            Callable<Object> function = () -> Files.createTempFile(dir, prefix, suffix, attrs);
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.CreateTempFile.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            return (Path)divergenceStatus.returnValue;
        }
    }

    @Override
    public Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        return createTempFile(null, prefix, suffix, attrs);
    }

    @Override
    public void copy(JMVXFileSystemProvider fsp, Path source, Path target, CopyOption... options) throws IOException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.FileSystemProviderCopy){
            return;
        }else{
            Callable<Object> function = () -> { fsp.$JMVX$copy(source, target, options); return null; };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileSystemProviderCopy.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
        }
    }

    @Override
    public void checkAccess(JMVXFileSystemProvider fsp, Path path, AccessMode... modes) throws IOException {
        Object check = receiveObjectFromCoordinator();
        if(check instanceof Leader.FileSystemProviderCheckAccess){
            IOException e = ((Leader.FileSystemProviderCheckAccess) check).e;
            if (e != null)
                throw e;
        }else{
            Callable<Object> function = () -> { fsp.$JMVX$checkAccess(path, modes); return null; };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileSystemProviderCheckAccess.class.getSimpleName(), check.getClass().getSimpleName(), check, function);
        }
    }

    @Override
    public int open(UnixPath path, int flags, int mode) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        int stub;
        if(read instanceof Leader.NativeOpen){
            Leader.NativeOpen open = (Leader.NativeOpen) read;
            if(!open.path.equals(path.toString()))
                stub = 0;
            return UnixNativeDispatcher.open(UnixPath.toUnixPath(Paths.get("/dev/null")), flags, mode);
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> UnixNativeDispatcher.open(path, flags, mode);
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeOpen.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
            return (int)divergenceStatus.returnValue;
        }
    }

    @Override
    public void stat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeStat){
            Leader.NativeStat stat = (Leader.NativeStat) read;
            UnixFileAttributesUtil.copy(stat.attrs, attrs);
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { UnixNativeDispatcher.stat(path, attrs); return true; };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeStat.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
            //no need to copy to attrs, stat should have done that already
        }
    }

    @Override
    public void lstat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeLstat){
            Leader.NativeLstat lstat = (Leader.NativeLstat) read;
            UnixFileAttributesUtil.copy(lstat.attrs, attrs);
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { UnixNativeDispatcher.stat(path, attrs); return true; };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeLstat.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
        }
    }

    private static final Path EMPTY_DIR;
    static {
        try {
            EMPTY_DIR = Files.createTempDirectory("JMVXEmptyDir");
        } catch (IOException e) {
            throw new Error("Cannot make JMVXEMptyDir", e);
        }
    }

    @Override
    public long opendir(UnixPath path) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeOpenDir){
            Leader.NativeOpenDir obj = (Leader.NativeOpenDir) read;
            assert path.toString().equals(obj.path);
            return UnixNativeDispatcher.opendir(UnixPath.toUnixPath(EMPTY_DIR));
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { return UnixNativeDispatcher.opendir(path); };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeOpenDir.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
            return (long)divergenceStatus.returnValue;
        }
    }

    @Override
    public byte[] readdir(long dir) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeReadDir){
            Leader.NativeReadDir obj = (Leader.NativeReadDir) read;
            assert dir == obj.dir;
            return obj.ret;
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { return UnixNativeDispatcher.readdir(dir); };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeReadDir.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
            return (byte[])divergenceStatus.returnValue;
        }
    }

    @Override
    public void access(UnixPath path, int amode) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeAccess){
            Leader.NativeAccess obj = (Leader.NativeAccess) read;
            assert (path.toString().equals(obj.path) && amode == obj.amode);
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { UnixNativeDispatcher.access(path, amode); return true; };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeAccess.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
        }
    }

    @Override
    public void closedir(long dir) throws UnixException {
        UnixNativeDispatcher.closedir(dir);
    }

    @Override
    public void mkdir(UnixPath path, int mode) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeMkdir){
            Leader.NativeMkdir obj = (Leader.NativeMkdir) read;
            assert (path.toString().equals(obj.path) && mode == obj.mode);
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { UnixNativeDispatcher.mkdir(path, mode); return true; };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeMkdir.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
        }
    }

    @Override
    public int dup(int fd) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeDup){
            Leader.NativeDup obj = (Leader.NativeDup) read;
            assert fd == obj.fd;
            return UnixNativeDispatcher.dup(fd);
            //return obj.ret; //this could collide with an fd number that has been assigned...?
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { return UnixNativeDispatcher.dup(fd); };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeDup.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
            return (int)divergenceStatus.returnValue;
        }
    }

    @Override
    public long fdopendir(int dfd) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeFdOpenDir){
            Leader.NativeFdOpenDir obj = (Leader.NativeFdOpenDir) read;
            assert dfd == obj.dfd;
            return UnixNativeDispatcher.opendir(UnixPath.toUnixPath(EMPTY_DIR));//obj.ret;
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { return UnixNativeDispatcher.fdopendir(dfd); };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeFdOpenDir.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
            return (long)divergenceStatus.returnValue;
        }
    }

    @Override
    public byte[] realpath(UnixPath path) throws UnixException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.NativeRealPath){
            Leader.NativeRealPath obj = (Leader.NativeRealPath) read;
            assert path.equals(obj.path);
            return obj.ret;
        }else if (read instanceof UnixException){
            throw (UnixException) read;
        }else{
            Callable<Object> function = () -> { return UnixNativeDispatcher.realpath(path); };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.NativeRealPath.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (UnixException)divergenceStatus.returnValue;
            }
            return (byte[])divergenceStatus.returnValue;
        }
    }

    @Override
    public int read(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented"); //handled by JMVXFileChannel read earlier
    }

    @Override
    public int pread(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long readv(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int write(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int pwrite(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long writev(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long seek(JMVXFileDispatcherImpl impl, FileDescriptor fd, long offset) throws IOException {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.FileDispatcherSeek){
            Leader.FileDispatcherSeek obj = (Leader.FileDispatcherSeek) read;
            assert FileDescriptorUtils.fdAccess.get(fd) == obj.fd && offset == obj.offset;
            return obj.ret;
        }else if (read instanceof IOException){
            throw (IOException) read;
        }else{
            Callable<Object> function = () -> { return impl.$JMVX$seek(fd, offset); };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.FileDispatcherSeek.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            if(divergenceStatus.containsException){
                throw (IOException)divergenceStatus.returnValue;
            }
            return (long)divergenceStatus.returnValue;
        }
    }

    @Override
    public int force(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean metaData) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int truncate(JMVXFileDispatcherImpl impl, FileDescriptor fd, long size) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long size(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int lock(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void release(JMVXFileDispatcherImpl impl, FileDescriptor fd, long pos, long size) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void close(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        impl.$JMVX$close(fd);
    }

    @Override
    public void preClose(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getSystemTimeZoneID(String javaHome) {
        Object read = receiveObjectFromCoordinator();
        if(read instanceof Leader.GetSystemTimeZoneID){
            Leader.GetSystemTimeZoneID obj = (Leader.GetSystemTimeZoneID) read;
            assert obj.javaHome.equals(javaHome);
            return obj.ret;
        }else {
            Callable<Object> function = () -> { return TimeZone.getSystemTimeZoneID(javaHome); };
            DivergenceStatus<Object> divergenceStatus = divergence(Leader.GetSystemTimeZoneID.class.getSimpleName(), read.getClass().getSimpleName(), read, function);
            return (String) divergenceStatus.returnValue;
        }
    }
}
