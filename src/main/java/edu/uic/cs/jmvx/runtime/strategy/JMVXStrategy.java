package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.runtime.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import sun.nio.fs.UnixPath;
import sun.nio.fs.UnixException;
import sun.nio.fs.UnixFileAttributes;
import sun.nio.fs.UnixNativeDispatcher;

public interface JMVXStrategy {

    public void main();

    // From InputStream

    public int read(JMVXInputStream is) throws IOException;

    public int read(JMVXInputStream is, byte[] b) throws IOException;

    public int read(JMVXInputStream is, byte[] b, int off, int len) throws IOException;

    public int available(JMVXInputStream is) throws IOException;

    // From OutputStream

    public void close(JMVXOutputStream os) throws IOException;

    public void flush(JMVXOutputStream os) throws IOException;

    public void write(JMVXOutputStream os, int b) throws IOException;

    public void write(JMVXOutputStream os, byte[] b) throws IOException;

    public void write(JMVXOutputStream os, byte[] b, int off, int len) throws IOException;

    //From File

    public boolean canRead(JMVXFile f);

    public boolean canWrite(JMVXFile f);

    public boolean createNewFile(JMVXFile f) throws IOException;

    public boolean delete(JMVXFile f);

    public boolean exists(JMVXFile f);

    public File getAbsoluteFile(JMVXFile f);

    public String getAbsolutePath(JMVXFile f);

    public File getCanonicalFile(JMVXFile f) throws IOException;

    public String getCanonicalPath(JMVXFile f) throws IOException;

    public String getName(JMVXFile f);

    public File getParentFile(JMVXFile f);

    public String getPath(JMVXFile f);

    public boolean isDirectory(JMVXFile f);

    public long length(JMVXFile f);

    public String[] list(JMVXFile f);

    public String[] list(JMVXFile f, FilenameFilter filter);

    public File[] listFiles(JMVXFile f);

    public File[] listFiles(JMVXFile f, FilenameFilter filter);

    public boolean mkdir(JMVXFile f);

    public boolean mkdirs(JMVXFile f);

    public boolean renameTo(JMVXFile f, File dest);

    public boolean setReadOnly(JMVXFile f);

    public URL toURL(JMVXFile f) throws MalformedURLException;

    public long lastModified(JMVXFile f);

    public boolean isFile(JMVXFile f);

    //For use in FileOutputStream constructors
    public void open(JMVXFileOutputStream fos, String name, boolean append) throws FileNotFoundException;

    public void open(JMVXFileInputStream fis, String name) throws FileNotFoundException;

    public String fileOutputStream(String name);

    public boolean fileOutputStream(boolean append);

    public File fileOutputStream(File file);

    public FileDescriptor fileOutputStream(FileDescriptor fdObj);

    public void sync(FileDescriptor fd) throws SyncFailedException;

    //For RandomAccessFile (read/write methods work the same as Input/Output streams, but don't use a stream...)
    public int read(JMVXRandomAccessFile f) throws IOException;

    public int read(JMVXRandomAccessFile f, byte[] b) throws IOException;

    public int read(JMVXRandomAccessFile f, byte[] b, int off, int len) throws IOException;

    public void open(JMVXRandomAccessFile f, String name, int mode) throws FileNotFoundException;

    public void close(JMVXRandomAccessFile f) throws IOException;

    public void write(JMVXRandomAccessFile f, int b) throws IOException;

    public void write(JMVXRandomAccessFile f, byte[] b) throws IOException;

    public void write(JMVXRandomAccessFile f, byte[] b, int off, int len) throws IOException;

    public long length(JMVXRandomAccessFile f) throws IOException;

    public void setLength(JMVXRandomAccessFile f, long newLength) throws IOException;

    public void seek(JMVXRandomAccessFile f, long pos) throws IOException;

    //For FileChannel
    public int read(JMVXFileChannel c, ByteBuffer dst) throws IOException;

    public long size(JMVXFileChannel c) throws IOException;

    //For Socket and ServerSocket

    public void connect(JMVXSocket sock, SocketAddress endpoint, int timeout) throws IOException;

    public void bind(JMVXServerSocket serv, SocketAddress endpoint, int backlog) throws IOException;

    public Socket accept(JMVXServerSocket serv) throws IOException;

    // For Monitor

    public void monitorenter(Object o);

    public void monitorexit(Object o);

    public void wait(Object o, long timeout, int nanos) throws InterruptedException;

    public void threadStart(Runnable r);

    public void threadExit(Runnable r);

    public void exitLogic();

    public default void systemExit(int status){
        JMVXRuntime.exitMain();
    }

    //timing methods
    public long currentTimeMillis();

    public long nanoTime();

    // Allow to enable/disable

    public default void enter() { }

    public default void exit() { }

    // Class loading

    public default Class<?> loadClass(JMVXClassLoader loader, String name) throws ClassNotFoundException {
        JMVXRuntime.enter();
        try {
            return loader.$JMVX$loadClass(name);
        } finally {
            JMVXRuntime.exit();
        }
    }

    public default Class<?> loadClass(JMVXClassLoader loader, String name, boolean resolve) throws ClassNotFoundException {
        JMVXRuntime.enter();
        try {
            return loader.$JMVX$loadClass(name, resolve);
        } finally {
            JMVXRuntime.exit();
        }
    }

    public default void clinitStart(String cls){

    }

    public default void clinitEnd(String cls){

    }

    /*public default void systemExit(int status){
        JMVXRuntime.exitMain();
    }*/


    //Zipfile native methods
    public default void initIDs(){
        JMVXRuntime.zipfile.$JMVX$initIDs();
    }
    public default long getEntry(long jzfile, byte[] name, boolean addSlash){
        return JMVXRuntime.zipfile.$JMVX$getEntry(jzfile, name, addSlash);
    }
    public default void freeEntry(long jzfile, long jzentry){
        /*
        There is a chance that a Replayer attempts to freeEntry on a pointer
        taken from the Recorder's log. This can happen from code within Follower/Replayer, e.g.
        during an InputStream.skip. To quickly identify when this occurs, the Recorder taints pointers returned
        from open and getEntry by making them negative--Todo support getNextEntry.
        Checking the sign is sufficient to tell if it is valid memory to operate on.
         */
        if(jzfile > 0 && jzentry > 0)
            JMVXRuntime.zipfile.$JMVX$freeEntry(jzfile, jzentry);
    }
    public default long getNextEntry(long jzfile, int i){
        return JMVXRuntime.zipfile.$JMVX$getNextEntry(jzfile, i);
    }
    public default void close(long jzfile){
        if(jzfile > 0) //Safeguard against a tainted pointer
            JMVXRuntime.zipfile.$JMVX$close(jzfile);
    }
    public default long open(String name, int mode, long lastModified, boolean usemmap) throws IOException{
        long ret = JMVXRuntime.zipfile.$JMVX$open(name, mode, lastModified, usemmap);
        return ret;
    }
    public default int getTotal(long jzfile){
        return JMVXRuntime.zipfile.$JMVX$getTotal(jzfile);
    }
    public default boolean startsWithLOC(long jzfile){
        return JMVXRuntime.zipfile.$JMVX$startsWithLOC(jzfile);
    }
    public default int read(long jzfile, long jzentry, long pos, byte[] b, int off, int len){
        return JMVXRuntime.zipfile.$JMVX$read(jzfile, jzentry, pos, b, off, len);
    }
    public default long getEntryTime(long jzentry){
        return JMVXRuntime.zipfile.$JMVX$getEntryTime(jzentry);
    }
    public default long getEntryCrc(long jzentry){
        return JMVXRuntime.zipfile.$JMVX$getEntryCrc(jzentry);
    }
    public default long getEntryCSize(long jzentry){
        return JMVXRuntime.zipfile.$JMVX$getEntryCSize(jzentry);
    }
    public default long getEntrySize(long jzentry){
        return JMVXRuntime.zipfile.$JMVX$getEntrySize(jzentry);
    }
    public default int getEntryMethod(long jzentry){
        return JMVXRuntime.zipfile.$JMVX$getEntryMethod(jzentry);
    }
    public default int getEntryFlag(long jzentry){
        return JMVXRuntime.zipfile.$JMVX$getEntryFlag(jzentry);
    }
    public default byte[] getCommentBytes(long jzfile){
        return JMVXRuntime.zipfile.$JMVX$getCommentBytes(jzfile);
    }
    public default byte[] getEntryBytes(long jzentry, int type){
        return JMVXRuntime.zipfile.$JMVX$getEntryBytes(jzentry, type);
    }
    public default String getZipMessage(long jzfile){
        return JMVXRuntime.zipfile.$JMVX$getZipMessage(jzfile);
    }

    //JarFile native method
    public default String[] getMetaInfEntryNames(JMVXJarFile jarfile) {
        return jarfile.$JMVX$getMetaInfEntryNames();
    }

    default Path createTempFile(Path dir, String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        return Files.createTempFile(dir, prefix, suffix, attrs);
    }

    default Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        return Files.createTempFile(prefix, suffix, attrs);
    }

    default String[] newProcessBuilder(String ... command) {
        return command;
    }

    default Process start(JMVXProcessBuilder pb) throws IOException {
        Process ret = pb.$JMVX$start();

        while (ret.isAlive()) {
            try {
                ret.waitFor();
            } catch (InterruptedException e) {
                continue;
            }
        }

        InputStream is = ret.getInputStream();

        if (is instanceof FilterInputStream) {
            try {
                while (true) {
                    Object o = JMVXRuntime.unsafe.getObject(is, JMVXRuntime.unsafe.objectFieldOffset(FilterInputStream.class.getDeclaredField("in")));
                    if (o instanceof ByteArrayInputStream)
                        break;
                }
            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }
        }

        return ret;
    }

    default void checkAccess(JMVXFileSystemProvider fsp, Path path, AccessMode... modes) throws IOException {
        fsp.$JMVX$checkAccess(path, modes);
    }

    default void copy(JMVXFileSystemProvider fsp, Path source, Path target, CopyOption... options) throws IOException {
        fsp.$JMVX$copy(source, target, options);
    }

    int open(UnixPath path, int flags, int mode) throws UnixException;

    void stat(UnixPath path, UnixFileAttributes attrs) throws UnixException;

    void lstat(UnixPath path, UnixFileAttributes attrs) throws UnixException;
    long opendir(UnixPath path) throws UnixException;
    byte[] readdir(long dir) throws UnixException;
    void access(UnixPath path, int amode) throws UnixException;
    void closedir(long dir) throws UnixException;
    void mkdir(UnixPath path, int mode) throws UnixException;
    int dup(int fd) throws UnixException;
    long fdopendir(int dfd) throws UnixException;
    byte[] realpath(UnixPath path) throws UnixException;

    int read(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException;
    int pread(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException;
    long readv(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException;
    int write(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException;
    int pwrite(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException;
    long writev(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException;
    long seek(JMVXFileDispatcherImpl impl, FileDescriptor fd, long offset) throws IOException;
    int force(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean metaData) throws IOException;
    int truncate(JMVXFileDispatcherImpl impl, FileDescriptor fd, long size) throws IOException;
    long size(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException;
    int lock(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException;
    void release(JMVXFileDispatcherImpl impl, FileDescriptor fd, long pos, long size) throws IOException;
    void close(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException;
    void preClose(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException;

    String getSystemTimeZoneID(String javaHome);
}
