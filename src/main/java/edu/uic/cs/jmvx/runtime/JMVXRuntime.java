package edu.uic.cs.jmvx.runtime;

import edu.uic.cs.jmvx.runtime.strategy.*;
import edu.uic.cs.jmvx.vectorclock.BackoffVectorClock;
import edu.uic.cs.jmvx.vectorclock.VectorClock;
import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;
import org.apache.log4j.Logger;
import org.objectweb.asm.Type;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

public class JMVXRuntime {
    public static boolean initialized = false;
    private static Logger log;
    public static final String ROLE_PROP_NAME = "jmvx.role";
    private static final String SYNC_PROP_NAME = "jmvx.sync";
    public static final String Prefix = "$JMVX$";

    public final static VectorClock clock = new BackoffVectorClock();
    public static AtomicInteger mainCalls = new AtomicInteger(0);
    public static volatile long currentTime = 0;
    public static HashMap<String, Integer> threadNames = new HashMap<>();
    public static volatile Thread mainThread;

//    public static final boolean checksumCommunicationEnabled = Boolean.parseBoolean(System.getProperty("checksumCommunicationEnabled", "true"));
    public static final boolean checksumCommunicationEnabled = Boolean.parseBoolean(System.getProperty("checksumCommunicationEnabled", "0"));
    public static final boolean consoleLogsEnabled = Boolean.parseBoolean(System.getProperty("consoleLogsEnabled", "true"));
    public static final boolean readsFromDisk = Boolean.parseBoolean(System.getProperty("readsFromDisk", "0"));
    public static boolean circularBufferCommunicationEnabled = Boolean.parseBoolean(System.getProperty("circularBufferCommunicationEnabled", "true"));
    public static final long mappedMemorySize = Long.parseLong(System.getProperty("mappedMemorySize", "819200000"));
//    public static final long mappedMemorySize = Long.parseLong(System.getProperty("mappedMemorySize", "50000"));
    public static boolean avoidObjectSerialization = Boolean.parseBoolean(System.getProperty("avoidObjectSerialization", "true"));
//    public static boolean avoidObjectSerialization = Boolean.parseBoolean(System.getProperty("avoidObjectSerialization", "0"));
    public static final boolean sync = Boolean.parseBoolean(System.getProperty("sync", "true"));
    public static boolean useBufferBackedStreams = Boolean.parseBoolean(System.getProperty("useBufferBackedStreams", "true"));
    public static boolean deleteMmapOnDisk = Boolean.parseBoolean(System.getProperty("deleteMmapOnDisk", "true"));
    public static boolean coreDumpAtEnd = Boolean.parseBoolean(System.getProperty("coreDumpAtEnd", "false"));
    public static boolean logNatives = Boolean.parseBoolean(System.getProperty("logNatives", "false"));
    public static int junkPort = Integer.parseInt(System.getProperty("junkPort", "9999"));
    //  file, path
    public static Map<JMVXFileOutputStream, String> openedFiles = new ConcurrentHashMap<>();

    public static final Unsafe unsafe;
    public static final String UNSAFE_FIELD_NAME = "theUnsafe";

    //used to redirect calls from zip files
    public static JMVXZipFile zipfile;

    static {
        Class<?> unsafeClass = Unsafe.class;
        Field unsafeField;
        try {
            unsafeField = unsafeClass.getDeclaredField(UNSAFE_FIELD_NAME);
            boolean accessible = unsafeField.isAccessible();
            unsafeField.setAccessible(true);
            unsafe = (Unsafe) unsafeField.get(null);
            unsafeField.setAccessible(accessible);
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        } catch (SecurityException e) {
            throw new Error(e);
        } catch (IllegalArgumentException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    public static CircularBuffer mmapFile(boolean leader) {
        CircularBuffer buffer = null;
        buffer = new CircularBuffer(mappedMemorySize, leader);
        return buffer;
    }

    public static void openExistingFiles() {
        for (JMVXFileOutputStream stream : openedFiles.keySet()) {
            try {
                stream.$JMVX$open(openedFiles.get(stream), true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public static void openExistingFilesToNull() {
        for (JMVXFileOutputStream stream : openedFiles.keySet()) {
            try {
                stream.$JMVX$open("/dev/null", false);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }



    public static InputStream wrap(InputStream target) {
        if (target == null)
            return target;

        if (target instanceof JMVXInputStream) {
            // Nothing to do, already wrapped
            return target;
        }

        System.out.println(target.getClass());

        // TODO wrap target
        return target;
    }

    private static ThreadLocal<JMVXStrategy> strategy = null;
    private static ThreadLocal topLevel = null;
    private static JMVXStrategy preInit = new SingleLeaderWithoutCoordinator();

    static JMVXStrategy getStrategy() {
        if (!initialized)
            return preInit;
        else
            return strategy.get();
    }

    public static void setStrategy(JMVXStrategy strategy) {
        JMVXRuntime.strategy.set(strategy);
    }

    public static void enter() {
        getStrategy().enter();
    }

    public static void exit() {
        getStrategy().exit();
    }

    private static JMVXStrategy initISStrategy() {
        String role = System.getProperty(ROLE_PROP_NAME);
        if (role != null) {
            /**
             * Set cirular buffer communicatoin to false if set to true while running recorder.
             */
            if(circularBufferCommunicationEnabled && (role.equalsIgnoreCase("Recorder")
                    || role.equalsIgnoreCase("Replayer"))) {
                log.debug("Cannot use cirular buffer for communication with recorder.");
                log.debug("Communication via circular buffer set to false.");
                circularBufferCommunicationEnabled = false;
            }

            //get rid of slashes in the name
            String name = Thread.currentThread().getName()
                    .replace("/", "_")
                    .replace("\\", "_");

            Thread.currentThread().setName(name);


            //Eliminate same thread name conflicts
            synchronized (threadNames){
                //String name = Thread.currentThread().getName();
                Integer v = threadNames.get(name);
                if(v == null){
                    v = 1;
                }else{
                    Thread.currentThread().setName(name + v);
                    v++;
                }
                threadNames.put(name, v);
            }

            // Make sure the locales are initialized
            new Formatter().format("");

            try {
                Class<?> roleClass = Class.forName(JMVXStrategy.class.getPackage().getName() + "." + role);
                JMVXStrategy temp = (JMVXStrategy) roleClass.newInstance();
                temp.main();
                log.info("Using role: " + temp.getClass().getSimpleName());
                temp = new ReentrantStrategy(temp);
                return temp;
            } catch (ReflectiveOperationException e) {
                log.warn("Could not find role: " + role, e);
                log.warn("Please set the appropriate role with -D" + ROLE_PROP_NAME + "=<role name>");
                log.warn("Defaulting to default role: " + getStrategy().getClass().getSimpleName());
            }
        } else {
            log.warn("Role not set");
            log.warn("Please set the appropriate role with -D" + ROLE_PROP_NAME + "=<role name>");
            log.warn("Defaulting to default role: " + getStrategy().getClass().getSimpleName());
        }

        return getStrategy();
    }

    public static Class<?> loadClass(JMVXClassLoader loader, String name) throws ClassNotFoundException {
        return JMVXRuntime.getStrategy().loadClass(loader, name);
    }

    public static Class<?> loadClass(JMVXClassLoader loader, String name, boolean resolve) throws ClassNotFoundException {
        return JMVXRuntime.getStrategy().loadClass(loader, name, resolve);
    }

    public static Class<?> defineClass(SecureClassLoader cl, String name, byte[] b, int off, int len, CodeSource cs){
        if (!name.startsWith("org.dacapo") && !name.contains("water.init.JarHash"))
            cs = null;

        JMVXSecureClassLoader scl = (JMVXSecureClassLoader) cl;
        return scl.$JMVX$defineClass(name, b, off, len, cs);
    }

    public static Class<?> defineClass(SecureClassLoader cl, String name, java.nio.ByteBuffer b, CodeSource cs){
        if (!name.startsWith("org.dacapo") && !name.contains("water.init.JarHash"))
            cs = null;

        JMVXSecureClassLoader scl = (JMVXSecureClassLoader) cl;
        return scl.$JMVX$defineClass(name, b, cs);
    }

    public static void $JMVX$clinitStart(String cls){
        JMVXRuntime.getStrategy().clinitStart(cls);
    }

    public static void $JMVX$clinitEnd(String cls){
        JMVXRuntime.getStrategy().clinitEnd(cls);
    }

    /**
     * Executes the kill command.
     * @param args, usually two strings, -signal, and pid
     * @throws IOException
     */
    private static void kill(String... args) {
        String[] cmd = new String[args.length+1];
        cmd[0] = "kill";
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
        }
    }

    public static void coreDump(){
        JMVXRuntime.enter();
        RuntimeMXBean run = ManagementFactory.getRuntimeMXBean();
        String pid = run.getName().split("@")[0];
        kill("-QUIT", pid); //causes a core dump
    }


    public static void $JMVX$main() {
        if(mainCalls.getAndIncrement() == 0)
            mainThread = Thread.currentThread();

        if (initialized)
            return;

        try {
            //handle to call zip file native methods elsewhere. see doc comment in ZipFileClassVisitor for more.
            zipfile = (JMVXZipFile) JMVXRuntime.unsafe.allocateInstance(ZipFile.class);
        } catch (ClassCastException | InstantiationException e) {
            //fails when instrumenting (with class cast), but we pass through
        }

        if(coreDumpAtEnd){
            Runtime.getRuntime().addShutdownHook(new Thread(JMVXRuntime::coreDump));
        }

        /*putting data in a concurrent hash map MAY initialize
          ThreadLocalRandom, which can create a divergence.
          Init here to prevent that.
          Note: values generated but the ThreadLocalRandom will differ
          between leader/follower and recorder/replayer, but that should be
          okay because it is used mainly for ConcurrentHashMaps, which do not
          cause our benchmarks to fail.
        */
        ThreadLocalRandom.current();
        log = Logger.getLogger(JMVXRuntime.class);

        try {
            InetAddress.getLocalHost().getHostName();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(new byte[1024]);
            oos.close();
            bos.close();
        } catch (IOException e) {
            // Ignore
        }

        // Initialize the system packages now, instead of later non-deterministically by a thread
        HashMap.class.getPackage();

        // New threads start with SingleLeaderWithoutCoordinator
        strategy = ThreadLocal.withInitial(() -> new SingleLeaderWithoutCoordinator());
        topLevel = ThreadLocal.withInitial(() -> null);

        JMVXStrategy in    = initISStrategy();

        // Set the strategy for the current thread
        strategy.set(in);
        if (!JMVXRuntime.sync)
            strategy = new ThreadLocal<JMVXStrategy>(){
                @Override
                public JMVXStrategy get() {
                    return in;
                }
            };
        // Main is not a Runnable, so just stick an object in the map to mark this thread as initialized
        topLevel.set(new Object());

        // Decorate strategy to avoid reentrant calls
        /*if (System.getProperty(SYNC_PROP_NAME) != null) {
            switch (System.getProperty(SYNC_PROP_NAME)) {
                case "BIGLOCK":
                    strategy = new BigLockReentrantStrategy(strategy);
                    break;
                default:
                    strategy = new ReentrantStrategy(strategy);
                    break;
            }
        } else {
            strategy = new ReentrantStrategy(strategy);
        }*/
        //JMVX can ignore the value of the property at runtime, so we reset it here.
        logNatives = Boolean.parseBoolean(System.getProperty("logNatives", "false"));
	if(logNatives){
            JMVXNativeDetector.registerHandler();
	}

        initialized = true;
    }

    public static void exitMain(){
        if(mainCalls.getAndDecrement() == 1){
            JMVXNativeDetector.logStacks();
            getStrategy().exitLogic();
        }
        //while(true);
    }

    public static int read(InputStream is) throws IOException {
        JMVXInputStream jmvxis = (JMVXInputStream) is;
        return getStrategy().read(jmvxis);
    }

    public static int read(InputStream is, byte[] b) throws IOException {
        JMVXInputStream jmvxis = (JMVXInputStream) is;
        return getStrategy().read(jmvxis, b);
    }

    public static int read(InputStream is, byte[] b, int off, int len) throws IOException {
        JMVXInputStream jmvxis = (JMVXInputStream) is;
        return getStrategy().read(jmvxis, b, off, len);
    }

    public static int available(InputStream is) throws IOException{
        JMVXInputStream jmvxis = (JMVXInputStream) is;
        return getStrategy().available(jmvxis);
    }

    public static void close(OutputStream os) throws IOException {
        JMVXOutputStream jmvxos = (JMVXOutputStream) os;
        getStrategy().close(jmvxos);
    }

    public static void flush(OutputStream os) throws IOException {
        JMVXOutputStream jmvxos = (JMVXOutputStream) os;
        getStrategy().flush(jmvxos);
    }

    public static void write(OutputStream os, int b) throws IOException {
        JMVXOutputStream jmvxos = (JMVXOutputStream) os;
        getStrategy().write(jmvxos, b);
    }

    public static void write(OutputStream os, byte[] b) throws IOException {
        JMVXOutputStream jmvxos = (JMVXOutputStream) os;
        getStrategy().write(jmvxos, b);
    }

    public static void write(OutputStream os, byte[] b, int off, int len) throws IOException {
        JMVXOutputStream jmvxos = (JMVXOutputStream) os;
        getStrategy().write(jmvxos, b, off, len);
    }

    public static boolean canRead(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().canRead(jmvxf);
    }

    public static boolean canWrite(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().canWrite(jmvxf);
    }

    public static boolean createNewFile(File f) throws IOException {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().createNewFile(jmvxf);
    }

    public static boolean delete(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().delete(jmvxf);
    }

    public static boolean exists(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().exists(jmvxf);
    }

    public static File getAbsoluteFile(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().getAbsoluteFile(jmvxf);
    }

    public static String getAbsolutePath(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().getAbsolutePath(jmvxf);
    }

    public static File getCanonicalFile(File f) throws IOException {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().getCanonicalFile(jmvxf);
    }

    public static String getCanonicalPath(File f) throws IOException{
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().getCanonicalPath(jmvxf);
    }

    public static String getName(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().getName(jmvxf);
    }

    public static File getParentFile(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().getParentFile(jmvxf);
    }

    public static String getPath(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().getPath(jmvxf);
    }

    public static boolean isDirectory(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().isDirectory(jmvxf);
    }

    public static long length(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().length(jmvxf);
    }

    public static String[] list(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().list(jmvxf);
    }

    public static String[] list(File f, FilenameFilter filter){
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().list(jmvxf, filter);
    }

    public static File[] listFiles(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().listFiles(jmvxf);
    }

    public static File[] listFiles(File f, FilenameFilter filter){
        return getStrategy().listFiles((JMVXFile) f, filter);
    }

    public static boolean mkdir(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().mkdir(jmvxf);
    }

    public static boolean mkdirs(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().mkdirs(jmvxf);
    }

    public static boolean renameTo(File f, File dest) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().renameTo(jmvxf, dest);
    }

    public static boolean setReadOnly(File f) {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().setReadOnly(jmvxf);
    }

    public static URL toURL(File f) throws MalformedURLException {
        JMVXFile jmvxf = (JMVXFile) f;
        return getStrategy().toURL(jmvxf);
    }

    public static long lastModified(File f){
        return getStrategy().lastModified((JMVXFile) f);
    }

    public static boolean isFile(File f){
        return getStrategy().isFile((JMVXFile) f);
    }

    public static void open(FileOutputStream fos, String name, boolean append) throws FileNotFoundException {
        JMVXFileOutputStream jmvxfos = (JMVXFileOutputStream) fos;
        getStrategy().open(jmvxfos, name, append);
    }

    public static void open(FileInputStream fis, String name) throws FileNotFoundException {
        JMVXFileInputStream jmvxfis = (JMVXFileInputStream) fis;
        getStrategy().open(jmvxfis, name);
    }

    public static String fileOutputStream(String name) {
        return getStrategy().fileOutputStream(name);
    }

    public static boolean fileOutputStream(boolean append) {
        return getStrategy().fileOutputStream(append);
    }

    public static File fileOutputStream(File file) {
        return getStrategy().fileOutputStream(file);
    }

    public static FileDescriptor fileOutputStream(FileDescriptor fdObj) {
        return getStrategy().fileOutputStream(fdObj);
    }

    public static void connect(Socket sock, SocketAddress endpoint, int timeout) throws IOException{
        JMVXSocket jmvxsock = (JMVXSocket) sock;
        getStrategy().connect(jmvxsock, endpoint, timeout);
    }

    public static void bind(ServerSocket serv, SocketAddress endpoint, int backlog) throws IOException{
        getStrategy().bind((JMVXServerSocket) serv, endpoint, backlog);
    }

    public static Socket accept(ServerSocket serv) throws IOException {
        return getStrategy().accept((JMVXServerSocket) serv);
    }

    public static void run(Runnable r) {
        // Hack for Jython, should be removed
        if (Thread.currentThread().getName().startsWith("process reaper"))
            return;
        if (Thread.currentThread().getName().startsWith("Reference Handler")) {
            // Reference handlers (GC) are not synchronized, skip them
            return;
        }

        //we can receive a signal before we hit a main method call
        //thus, $JMVX$main hasn't been called and topLevel = null
        if(topLevel == null){
            //set the stage to look like we have an assigned toplevel and init like a regular thread later
            //now the case for a signal w/ and w/o a call to $JMVX$main is the same
            strategy = ThreadLocal.withInitial(() -> new SingleLeaderWithoutCoordinator());
            topLevel = ThreadLocal.withInitial(() -> null);
        }
        if (topLevel.get() == null) {
            //special case: signals, we want them to work as intended
            //so don't change the strategy (should be init'd to SingleLeaderWithoutCoordinator)
            //adding another case, process reapers in jython.
            //Assumes external process will work correctly without intervention. We may want to substitute the
            //process reaper thread with a nop role that discards all operations.
            if(r instanceof Thread) {
                Thread t = (Thread)r;
                if(t.getName().contains("SIG")){// || t.getName().contains("process reaper")) {
                    topLevel.set(r);  
                    //let threads created to handler sigterm and sigint run without instrumentation
                    if(t.getName().contains("TERM") || t.getName().contains("INT"))
			    System.setProperty(ROLE_PROP_NAME, SingleLeaderWithoutCoordinator.class.getSimpleName());
                    return;
                }
            }
            strategy.set(initISStrategy());
            topLevel.set(r);
        }
    }

    public static void monitorenter(Object o) {
        getStrategy().monitorenter(o);
    }

    public static void monitorexit(Object o) { unsafe.monitorExit(o); }

    public static void wait(Object o) throws InterruptedException {
        getStrategy().wait(o, 0, 0);
    }

    public static void wait(Object o, long timeout) throws InterruptedException {
        getStrategy().wait(o, timeout, 0);
    }

    public static void wait(Object o, long timeout, int nanos) throws InterruptedException {
        getStrategy().wait(o, timeout, nanos);
    }

    public static void startThread(Runnable r){
        //log.info("Starting new thread " + r); //logging here causes a divergence
        if (topLevel.get() == r)
            getStrategy().threadStart(r);
    }

    public static void exitThread(Runnable r){
        //log.info("Stopping thread " + r); //logging here causes a divergence
        if (topLevel.get() == r)
            getStrategy().threadExit(r);
    }

    public static void sync(FileDescriptor fd) throws SyncFailedException {
        getStrategy().sync(fd);
    }

    public static int read(RandomAccessFile f) throws IOException{
        return getStrategy().read((JMVXRandomAccessFile) f);
    }

    public static int read(RandomAccessFile f, byte[] b) throws IOException {
        return getStrategy().read((JMVXRandomAccessFile) f, b);
    }

    public static int read(RandomAccessFile f, byte[] b, int off, int len) throws IOException {
        return getStrategy().read((JMVXRandomAccessFile) f, b, off, len);
    }

    public static void open(RandomAccessFile f, String name, int mode) throws FileNotFoundException {
        getStrategy().open((JMVXRandomAccessFile) f, name, mode);
    }

    public static void close(RandomAccessFile f) throws IOException {
        getStrategy().close((JMVXRandomAccessFile) f);
    }

    public static void write(RandomAccessFile f, int b) throws IOException {
        getStrategy().write((JMVXRandomAccessFile) f, b);
    }

    public static void write(RandomAccessFile f, byte[] b) throws IOException {
        getStrategy().write((JMVXRandomAccessFile) f, b);
    }

    public static void write(RandomAccessFile f, byte[] b, int off, int len) throws IOException {
        getStrategy().write((JMVXRandomAccessFile) f, b, off, len);
    }

    public static long length(RandomAccessFile f) throws IOException {
        return getStrategy().length((JMVXRandomAccessFile) f);
    }

    public static void seek(RandomAccessFile f, long pos) throws IOException{
        getStrategy().seek((JMVXRandomAccessFile) f, pos);
    }

    public static void setLength(RandomAccessFile f, long newLength) throws IOException{
        getStrategy().setLength((JMVXRandomAccessFile) f, newLength);
    }

    public static int read(FileChannel c, ByteBuffer dst) throws IOException{
        return getStrategy().read((JMVXFileChannel) c, dst);
    }

    public static long size(FileChannel c) throws IOException{
        return getStrategy().size((JMVXFileChannel) c);
    }

    public static long currentTimeMillis(){
        /*currentTime += 10;
        return currentTime;*/
        return getStrategy().currentTimeMillis();
    }

    public static long nanoTime(){
        return getStrategy().nanoTime();
    }

    public static Method[] getMethods(Class<?> clazz){
        Method[] meths = clazz.getMethods();
        Arrays.sort(meths, new Comparator<Method>() {
            @Override
            public int compare(Method o1, Method o2) {
                String m1 = o1.getName() + Type.getMethodDescriptor(o1);
                String m2 = o2.getName() + Type.getMethodDescriptor(o2);
                return m1.compareTo(m2);
            }
        });
        return meths;
    }

    public static Method[] getDeclaredMethods(Class<?> clazz){
        Method[] meths = clazz.getDeclaredMethods();
        Arrays.sort(meths, new Comparator<Method>() {
            @Override
            public int compare(Method o1, Method o2) {
                String m1 = o1.getName() + Type.getMethodDescriptor(o1);
                String m2 = o2.getName() + Type.getMethodDescriptor(o2);
                return m1.compareTo(m2);
            }
        });
        return meths;
    }

    public static Field[] getFields(Class<?> clazz){
        Field[] fields = clazz.getFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        return fields;
    }

    public static Constructor<?>[] getConstructors(Class<?> clazz){
        Constructor<?>[] cons = clazz.getConstructors();
        Arrays.sort(cons, new Comparator<Constructor<?>>() {
            @Override
            public int compare(Constructor<?> o1, Constructor<?> o2) {
                return Type.getConstructorDescriptor(o1).compareTo(Type.getConstructorDescriptor(o2));
            }
        });
        return cons;
    }

    public static void systemExit(int status){
        //skips shutdown threads
        System.setProperty(ROLE_PROP_NAME, SingleLeaderWithoutCoordinator.class.getSimpleName());
        getStrategy().systemExit(status);
    }

    public static ExecutorService newCachedThreadPool() {
        // Increase timeout from 1min to 5min
        // Only allow for 1 thread max
        return new ThreadPoolExecutor(0, 1,
                5L*60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
    }

    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        // Increase timeout from 1min to 5min
        // Only allow for 1 thread max
        return new ThreadPoolExecutor(0, 1,
                5L*60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory);
    }

    public static void initIDs(){
        getStrategy().initIDs();
    }
    public static long getEntry(long jzfile, byte[] name, boolean addSlash){
        return getStrategy().getEntry(jzfile, name, addSlash);
    }
    public static void freeEntry(long jzfile, long jzentry){
        getStrategy().freeEntry(jzfile, jzentry);
    }
    public static long getNextEntry(long jzfile, int i){
        return getStrategy().getNextEntry(jzfile, i);
    }
    public static void close(long jzfile){
        getStrategy().close(jzfile);
    }
    public static long open(String name, int mode, long lastModified, boolean usemmap) throws IOException{
        return getStrategy().open(name, mode, lastModified, usemmap);
    }
    public static int getTotal(long jzfile){
        return getStrategy().getTotal(jzfile);
    }
    public static boolean startsWithLOC(long jzfile){
        return getStrategy().startsWithLOC(jzfile);
    }
    public static int read(long jzfile, long jzentry, long pos, byte[] b, int off, int len){
        return getStrategy().read(jzfile, jzentry, pos, b, off, len);
    }
    public static long getEntryTime(long jzentry){
        return getStrategy().getEntryTime(jzentry);
    }
    public static long getEntryCrc(long jzentry){
        return getStrategy().getEntryCrc(jzentry);
    }
    public static long getEntryCSize(long jzentry){
        return getStrategy().getEntryCSize(jzentry);
    }
    public static long getEntrySize(long jzentry){
        return getStrategy().getEntrySize(jzentry);
    }
    public static int getEntryMethod(long jzentry){
        return getStrategy().getEntryMethod(jzentry);
    }
    public static int getEntryFlag(long jzentry){
        return getStrategy().getEntryFlag(jzentry);
    }
    public static byte[] getCommentBytes(long jzfile){
        return getStrategy().getCommentBytes(jzfile);
    }
    public static byte[] getEntryBytes(long jzentry, int type){
        return getStrategy().getEntryBytes(jzentry, type);
    }
    public static String getZipMessage(long jzfile){
        return getStrategy().getZipMessage(jzfile);
    }
    public static String[] getMetaInfEntryNames(JMVXJarFile jarfile){ return getStrategy().getMetaInfEntryNames(jarfile); }


    public static Path createTempFile(Path dir, String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        return getStrategy().createTempFile(dir, prefix, suffix, attrs);
    }

    public static Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        return getStrategy().createTempFile(prefix, suffix, attrs);
    }

//    public static String[] newProcessBuilder(String ... command) {
//        return getStrategy().newProcessBuilder(command);
//    }

    public static Process start(ProcessBuilder pb) throws IOException {
        JMVXProcessBuilder jpb = (JMVXProcessBuilder) (Object) pb;
        return getStrategy().start(jpb);
    }

    public static void checkAccess(FileSystemProvider fsp, Path path, AccessMode... modes) throws IOException {
        JMVXFileSystemProvider jfsp = (JMVXFileSystemProvider) fsp;
        getStrategy().checkAccess(jfsp, path, modes);
    }

    public static void copy(FileSystemProvider fsp, Path source, Path target, CopyOption... options) throws IOException {
        JMVXFileSystemProvider jfsp = (JMVXFileSystemProvider) fsp;
        getStrategy().copy(jfsp, source, target, options);
    }

    public static int read(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        return getStrategy().read(impl, fd, address, len);
    }
    public static int pread(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException {
        return getStrategy().pread(impl, fd, address, len, position);
    }
    public static long readv(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        return getStrategy().readv(impl, fd, address, len);
    }
    public static int write(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        return getStrategy().write(impl, fd, address, len);
    }
    public static int pwrite(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException {
        return getStrategy().pwrite(impl, fd, address, len, position);
    }
    public static long writev(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException {
        return getStrategy().writev(impl, fd, address, len);
    }
    public static long seek(JMVXFileDispatcherImpl impl, FileDescriptor fd, long offset) throws IOException {
        return getStrategy().seek(impl, fd, offset);
    }
    public static int force(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean metaData) throws IOException {
        return getStrategy().force(impl, fd, metaData);
    }
    public static int truncate(JMVXFileDispatcherImpl impl, FileDescriptor fd, long size) throws IOException {
        return getStrategy().truncate(impl, fd, size);
    }
    public static long size(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        return getStrategy().size(impl, fd);
    }
    public static int lock(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException {
        return getStrategy().lock(impl, fd, blocking, pos, size, shared);
    }
    public static void release(JMVXFileDispatcherImpl impl, FileDescriptor fd, long pos, long size) throws IOException {
        getStrategy().release(impl, fd, pos, size);
    }
    public static void close(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        getStrategy().close(impl, fd);
    }
    public static void preClose(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        getStrategy().preClose(impl, fd);
    }

    public static String getSystemTimeZoneID(String javaHome){
        return getStrategy().getSystemTimeZoneID(javaHome);
    }
}
