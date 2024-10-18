package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.runtime.*;
import sun.misc.Unsafe;
import sun.nio.fs.UnixException;
import sun.nio.fs.UnixFileAttributes;
import sun.nio.fs.UnixNativeDispatcher;
import sun.nio.fs.UnixPath;
import java.util.TimeZone;

import java.io.*;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class SingleLeaderWithoutCoordinator implements JMVXStrategy {

    @Override
    public void main() {
        // Empty, no coordinator
    }

    @Override
    public int read(JMVXInputStream jmvxis) throws IOException { return jmvxis.$JMVX$read(); }

    @Override
    public int read(JMVXInputStream jmvxis, byte[] b) throws IOException { return jmvxis.$JMVX$read(b); }

    @Override
    public int read(JMVXInputStream jmvxis, byte[] b, int off, int len) throws IOException { return jmvxis.$JMVX$read(b, off, len); }

    @Override
    public int available(JMVXInputStream jmvxis) throws IOException { return jmvxis.$JMVX$available(); }

    @Override
    public void close(JMVXOutputStream jmvxos) throws IOException { jmvxos.$JMVX$close(); }

    @Override
    public void flush(JMVXOutputStream jmvxos) throws IOException { jmvxos.$JMVX$flush(); }

    @Override
    public void write(JMVXOutputStream jmvxos, int b) throws IOException { jmvxos.$JMVX$write(b); }

    @Override
    public void write(JMVXOutputStream jmvxos, byte[] b) throws IOException { jmvxos.$JMVX$write(b); }

    @Override
    public void write(JMVXOutputStream jmvxos, byte[] b, int off, int len) throws IOException { jmvxos.$JMVX$write(b, off, len); }

    @Override
    public boolean canRead(JMVXFile jmvxf) { return jmvxf.$JMVX$canRead(); }

    @Override
    public boolean canWrite(JMVXFile jmvxf) { return jmvxf.$JMVX$canWrite(); }

    @Override
    public boolean createNewFile(JMVXFile jmvxf) throws IOException { return jmvxf.$JMVX$createNewFile(); }

    @Override
    public boolean delete(JMVXFile jmvxf) {
        return jmvxf.$JMVX$delete();
    }

    @Override
    public boolean exists(JMVXFile jmvxf) {
        return jmvxf.$JMVX$exists();
    }

    @Override
    public File getAbsoluteFile(JMVXFile jmvxf) {
        return jmvxf.$JMVX$getAbsoluteFile();
    }

    @Override
    public String getAbsolutePath(JMVXFile jmvxf) { return jmvxf.$JMVX$getAbsolutePath(); }

    @Override
    public File getCanonicalFile(JMVXFile jmvxf) throws IOException { return jmvxf.$JMVX$getCanonicalFile(); }

    @Override
    public String getCanonicalPath(JMVXFile jmvxf) throws IOException { return jmvxf.$JMVX$getCanonicalPath(); }

    @Override
    public String getName(JMVXFile jmvxf) { return jmvxf.$JMVX$getName(); }

    @Override
    public File getParentFile(JMVXFile jmvxf) {
        return jmvxf.$JMVX$getParentFile();
    }

    @Override
    public String getPath(JMVXFile f) { return ((File)f).getPath(); } //f.$JMVX$getPath(); }

    @Override
    public boolean isDirectory(JMVXFile jmvxf) {
        return jmvxf.$JMVX$isDirectory();
    }

    @Override
    public long length(JMVXFile jmvxf) {
        return jmvxf.$JMVX$length();
    }

    @Override
    public String[] list(JMVXFile jmvxf) {
        return jmvxf.$JMVX$list();
    }

    @Override
    public String[] list(JMVXFile f, FilenameFilter filter) { return f.$JMVX$list(filter); }

    @Override
    public File[] listFiles(JMVXFile jmvxf) {
        return jmvxf.$JMVX$listFiles();
    }

    @Override
    public File[] listFiles(JMVXFile f, FilenameFilter filter) { return f.$JMVX$listFiles(filter); }

    @Override
    public boolean mkdir(JMVXFile jmvxf) {
        return jmvxf.$JMVX$mkdir();
    }

    @Override
    public boolean mkdirs(JMVXFile jmvxf) { return jmvxf.$JMVX$mkdirs(); }

    @Override
    public boolean renameTo(JMVXFile jmvxf, File dest) {
        return jmvxf.$JMVX$renameTo(dest);
    }

    @Override
    public boolean setReadOnly(JMVXFile jmvxf) {
        return jmvxf.$JMVX$setReadOnly();
    }

    @Override
    public URL toURL(JMVXFile jmvxf) throws MalformedURLException { return jmvxf.$JMVX$toURL(); }

    @Override
    public long lastModified(JMVXFile f) { return f.$JMVX$lastModified(); }

    @Override
    public boolean isFile(JMVXFile f) { return f.$JMVX$isFile(); }

    @Override
    public void open(JMVXFileOutputStream jmvxfos, String name, boolean append) throws FileNotFoundException { jmvxfos.$JMVX$open(name, append); }

    @Override
    public void open(JMVXFileInputStream jmvxfis, String name) throws FileNotFoundException { jmvxfis.$JMVX$open(name); }

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
        fd.sync();
    }

    @Override
    public void connect(JMVXSocket jmvxsock, SocketAddress endpoint, int timeout) throws IOException{
        jmvxsock.$JMVX$connect(endpoint, timeout);
    }

    @Override
    public void bind(JMVXServerSocket jmvxserv, SocketAddress endpoint, int backlog) throws IOException {
        jmvxserv.$JMVX$bind(endpoint, backlog);
    }

    @Override
    public Socket accept(JMVXServerSocket jmvxserv) throws IOException {
        return jmvxserv.$JMVX$accept();
    }

    private static final Unsafe unsafe;
    private static final String UNSAFE_FIELD_NAME = "theUnsafe";

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

    @Override
    public void monitorenter(Object o) {
        unsafe.monitorEnter(o);;
    }

    @Override
    public void monitorexit(Object o) {
        unsafe.monitorExit(o);;
    }

    @Override
    public void wait(Object o, long timeout, int nanos) throws InterruptedException {
        o.wait(timeout, nanos);
    }

    @Override
    public void threadStart(Runnable r) {

    }

    @Override
    public void threadExit(Runnable r) {

    }

    @Override
    public void exitLogic() {

    }

    @Override
    public int read(JMVXRandomAccessFile jmvxraf) throws IOException { return jmvxraf.$JMVX$read(); }

    @Override
    public int read(JMVXRandomAccessFile jmvxraf, byte[] b) throws IOException { return jmvxraf.$JMVX$read(b); }

    @Override
    public int read(JMVXRandomAccessFile jmvxraf, byte[] b, int off, int len) throws IOException { return jmvxraf.$JMVX$read(b, off, len); }

    @Override
    public void open(JMVXRandomAccessFile f, String name, int mode) throws FileNotFoundException {
        f.$JMVX$open(name, mode);
    }

    @Override
    public void close(JMVXRandomAccessFile f) throws IOException {
        f.$JMVX$close();
    }

    @Override
    public void write(JMVXRandomAccessFile f, int b) throws IOException {
        f.$JMVX$write(b);
    }

    @Override
    public void write(JMVXRandomAccessFile f, byte[] b) throws IOException {
        f.$JMVX$write(b);
    }

    @Override
    public void write(JMVXRandomAccessFile f, byte[] b, int off, int len) throws IOException {
        f.$JMVX$write(b, off, len);
    }

    @Override
    public long length(JMVXRandomAccessFile f) throws IOException {
        return ((RandomAccessFile)f).length();
    }

    @Override
    public void setLength(JMVXRandomAccessFile f, long newLength) throws IOException {
        ((RandomAccessFile)f).setLength(newLength);
    }

    @Override
    public void seek(JMVXRandomAccessFile f, long pos) throws IOException {
        f.$JMVX$seek(pos);
    }

    @Override
    public int read(JMVXFileChannel c, ByteBuffer dst) throws IOException {
        return c.$JMVX$read(dst);
    }

    @Override
    public long size(JMVXFileChannel c) throws IOException {
        return c.$JMVX$size();
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public int open(UnixPath path, int flags, int mode) throws UnixException {
        return UnixNativeDispatcher.open(path, flags, mode);
    }

    @Override
    public void stat(UnixPath path, UnixFileAttributes attrs) throws UnixException{
        UnixNativeDispatcher.stat(path, attrs);
    }

    @Override
    public void lstat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        UnixNativeDispatcher.lstat(path, attrs);
    }
    @Override
    public long opendir(UnixPath path) throws UnixException{
        return UnixNativeDispatcher.opendir(path);
    }
    @Override
    public byte[] readdir(long dir) throws UnixException{
        return UnixNativeDispatcher.readdir(dir);
    }
    @Override
    public void access(UnixPath path, int amode) throws UnixException {
        UnixNativeDispatcher.access(path, amode);
    }
    @Override
    public void closedir(long dir) throws UnixException{
        UnixNativeDispatcher.closedir(dir);
    }
    @Override
    public void mkdir(UnixPath path, int mode) throws UnixException {
        UnixNativeDispatcher.mkdir(path, mode);
    }
    @Override
    public int dup(int fd) throws UnixException {
        return UnixNativeDispatcher.dup(fd);
    }
    @Override
    public long fdopendir(int dfd) throws UnixException {
        return UnixNativeDispatcher.fdopendir(dfd);
    }

    @Override
    public byte[] realpath(UnixPath path) throws UnixException {
        return UnixNativeDispatcher.realpath(path);
    }

    @Override
    public int read(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException{
        return impl.$JMVX$read(fd, address, len);
    }
    @Override
    public int pread(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException{
        return impl.$JMVX$pread(fd, address, len, position);
    }
    @Override
    public long readv(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException{
        return impl.$JMVX$readv(fd, address, len);
    }
    @Override
    public int write(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException{
        return impl.$JMVX$write(fd, address, len);
    }
    @Override
    public int pwrite(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException{
        return impl.$JMVX$pwrite(fd, address, len, position);
    }
    @Override
    public long writev(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException{
        return impl.$JMVX$writev(fd, address, len);
    }
    @Override
    public long seek(JMVXFileDispatcherImpl impl, FileDescriptor fd, long offset) throws IOException{
        return impl.$JMVX$seek(fd, offset);
    }
    @Override
    public int force(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean metaData) throws IOException{
        return impl.$JMVX$force(fd, metaData);
    }
    @Override
    public int truncate(JMVXFileDispatcherImpl impl, FileDescriptor fd, long size) throws IOException{
        return impl.$JMVX$truncate(fd, size);
    }
    @Override
    public long size(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException{
        return impl.$JMVX$size(fd);
    }
    @Override
    public int lock(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException{
        return impl.$JMVX$lock(fd, blocking, pos, size, shared);
    }
    @Override
    public void release(JMVXFileDispatcherImpl impl, FileDescriptor fd, long pos, long size) throws IOException{
        impl.$JMVX$release(fd, pos, size);
    }
    @Override
    public void close(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException{
        impl.$JMVX$close(fd);
    }
    @Override
    public void preClose(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException{
        impl.$JMVX$preClose(fd);
    }

    @Override
    public String getSystemTimeZoneID(String javaHome) {
        return TimeZone.getSystemTimeZoneID(javaHome);
    }
}
