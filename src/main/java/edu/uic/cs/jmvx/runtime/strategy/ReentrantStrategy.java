package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.runtime.*;
import sun.nio.fs.UnixException;
import sun.nio.fs.UnixFileAttributes;
import sun.nio.fs.UnixPath;

import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;

public class ReentrantStrategy implements JMVXStrategy {
    private final JMVXStrategy strategy;
    private final JMVXStrategy passthrough = new SingleLeaderWithoutCoordinator();

    private JMVXStrategy inUse;
    private int depth = 0;

    public ReentrantStrategy(JMVXStrategy strategy) {
        this.strategy = strategy;
        this.inUse = this.strategy;
    }

    @Override
    public void main() {
        this.strategy.main();
    }

    @Override
    public int read(JMVXInputStream is) throws IOException {
        try {
            enter();
            return this.inUse.read(is);
        } finally {
            exit();
        }
    }

    @Override
    public int read(JMVXInputStream is, byte[] b) throws IOException {
        try {
            enter();
            return this.inUse.read(is, b);
        } finally {
            exit();
        }
    }

    @Override
    public int read(JMVXInputStream is, byte[] b, int off, int len) throws IOException {
        try {
            enter();
            return this.inUse.read(is, b, off, len);
        } finally {
            exit();
        }
    }

    @Override
    public int available(JMVXInputStream is) throws IOException{
        try {
            enter();
            return this.inUse.available(is);
        } finally {
            exit();
        }
    }

    @Override
    public void close(JMVXOutputStream os) throws IOException {
        try {
            enter();
            this.inUse.close(os);
        } finally {
            exit();
        }
    }

    @Override
    public void flush(JMVXOutputStream os) throws IOException {
        try {
            enter();
            this.inUse.flush(os);
        } finally {
            exit();
        }
    }

    @Override
    public void write(JMVXOutputStream os, int b) throws IOException {
        try {
            enter();
            this.inUse.write(os, b);
        } finally {
            exit();
        }
    }

    @Override
    public void write(JMVXOutputStream os, byte[] b) throws IOException {
        try {
            enter();
            this.inUse.write(os, b);
        } finally {
            exit();
        }
    }

    @Override
    public void write(JMVXOutputStream os, byte[] b, int off, int len) throws IOException {
        try {
            enter();
            this.inUse.write(os, b, off, len);
        } finally {
            exit();
        }
    }

    @Override
    public boolean canRead(JMVXFile f) {
        try {
            enter();
            return this.inUse.canRead(f);
        } finally {
            exit();
        }
    }

    @Override
    public void connect(JMVXSocket sock, SocketAddress endpoint, int timeout) throws IOException{
        try {
            enter();
            this.inUse.connect(sock, endpoint, timeout);
        } finally {
            exit();
        }
    }

    @Override
    public void bind(JMVXServerSocket serv, SocketAddress endpoint, int backlog) throws IOException{
        try {
            enter();
            this.inUse.bind(serv, endpoint, backlog);
        } finally {
            exit();
        }
    }

    @Override
    public Socket accept(JMVXServerSocket serv) throws IOException {
        try {
            enter();
            return this.inUse.accept(serv);
        } finally {
            exit();
        }
    }

    @Override
    public boolean canWrite(JMVXFile f) {
        try {
            enter();
            return this.inUse.canWrite(f);
        } finally {
            exit();
        }
    }

    @Override
    public boolean createNewFile(JMVXFile f) throws IOException {
        try {
            enter();
            return this.inUse.createNewFile(f);
        } finally {
            exit();
        }
    }

    @Override
    public boolean delete(JMVXFile f) {
        try {
            enter();
            return this.inUse.delete(f);
        } finally {
            exit();
        }
    }

    @Override
    public boolean exists(JMVXFile f) {
        try {
            enter();
            return this.inUse.exists(f);
        } finally {
            exit();
        }
    }

    @Override
    public File getAbsoluteFile(JMVXFile f) {
        try {
            enter();
            return this.inUse.getAbsoluteFile(f);
        } finally {
            exit();
        }
    }

    @Override
    public String getAbsolutePath(JMVXFile f) {
        try {
            enter();
            return this.inUse.getAbsolutePath(f);
        } finally {
            exit();
        }
    }

    @Override
    public File getCanonicalFile(JMVXFile f) throws IOException {
        try {
            enter();
            return this.inUse.getCanonicalFile(f);
        } finally {
            exit();
        }
    }

    @Override
    public String getCanonicalPath(JMVXFile f) throws IOException{
        try {
            enter();
            return this.inUse.getCanonicalPath(f);
        } finally {
            exit();
        }
    }

    @Override
    public String getName(JMVXFile f) {
        try {
            enter();
            return this.inUse.getName(f);
        } finally {
            exit();
        }
    }

    @Override
    public File getParentFile(JMVXFile f) {
        try {
            enter();
            return this.inUse.getParentFile(f);
        } finally {
            exit();
        }
    }

    @Override
    public String getPath(JMVXFile f) {
        try {
            enter();
            return this.inUse.getPath(f);
        } finally {
            exit();
        }
    }

    @Override
    public boolean isDirectory(JMVXFile f) {
        try {
            enter();
            return this.inUse.isDirectory(f);
        } finally {
            exit();
        }
    }

    @Override
    public long length(JMVXFile f) {
        try {
            enter();
            return this.inUse.length(f);
        } finally {
            exit();
        }
    }

    @Override
    public String[] list(JMVXFile f) {
        try {
            enter();
            return this.inUse.list(f);
        } finally {
            exit();
        }
    }

    @Override
    public String[] list(JMVXFile f, FilenameFilter filter) {
        try{
            enter();
            return this.inUse.list(f, filter);
        }finally {
            exit();
        }
    }

    @Override
    public File[] listFiles(JMVXFile f) {
        try {
            enter();
            return this.inUse.listFiles(f);
        } finally {
            exit();
        }
    }

    @Override
    public File[] listFiles(JMVXFile f, FilenameFilter filter) {
        try{
            enter();
            return this.inUse.listFiles(f, filter);
        }finally {
            exit();
        }
    }

    @Override
    public boolean mkdir(JMVXFile f) {
        try {
            enter();
            return this.inUse.mkdir(f);
        } finally {
            exit();
        }
    }

    @Override
    public boolean mkdirs(JMVXFile f) {
        try {
            enter();
            return this.inUse.mkdirs(f);
        } finally {
            exit();
        }
    }

    @Override
    public boolean renameTo(JMVXFile f, File dest) {
        try {
            enter();
            return this.inUse.renameTo(f, dest);
        } finally {
            exit();
        }
    }

    @Override
    public boolean setReadOnly(JMVXFile f) {
        try {
            enter();
            return this.inUse.setReadOnly(f);
        } finally {
            exit();
        }
    }

    @Override
    public URL toURL(JMVXFile f) throws MalformedURLException {
        try {
            enter();
            return this.inUse.toURL(f);
        } finally {
            exit();
        }
    }

    @Override
    public long lastModified(JMVXFile f) {
        try {
            enter();
            return this.inUse.lastModified(f);
        }finally {
            exit();
        }
    }

    @Override
    public boolean isFile(JMVXFile f) {
        try{
            enter();
            return this.inUse.isFile(f);
        }finally {
            exit();
        }
    }

    @Override
    public void open(JMVXFileOutputStream fos, String name, boolean append) throws FileNotFoundException{
        try {
            enter();
            this.inUse.open(fos, name, append);
        } finally {
            exit();
        }
    }

    @Override
    public void open(JMVXFileInputStream fis, String name) throws FileNotFoundException {
        try {
            enter();
            this.inUse.open(fis, name);
        } finally {
            exit();
        }
    }

    @Override
    public String fileOutputStream(String name) {
        return this.strategy.fileOutputStream(name);
    }

    @Override
    public boolean fileOutputStream(boolean append) {
        return this.strategy.fileOutputStream(append);
    }

    @Override
    public File fileOutputStream(File file) {
        return this.strategy.fileOutputStream(file);
    }

    @Override
    public FileDescriptor fileOutputStream(FileDescriptor fdObj) {
        return this.strategy.fileOutputStream(fdObj);
    }

    @Override
    public void sync(FileDescriptor fd) throws SyncFailedException {
        try {
            enter();
            this.inUse.sync(fd);
        } finally {
            exit();
        }
    }

    @Override
    public void monitorenter(Object o) {
        try {
            enter();
            this.inUse.monitorenter(o);
        } finally {
            exit();
        }
    }

    @Override
    public void monitorexit(Object o) {
        try {
            enter();
            this.inUse.monitorexit(o);
        } finally {
            exit();
        }
    }

    @Override
    public void wait(Object o, long timeout, int nanos) throws InterruptedException {
        try {
            enter();
            this.inUse.wait(o, timeout, nanos);
        } finally {
            exit();
        }
    }

    @Override
    public void threadStart(Runnable r) {
        try {
            enter();
            this.inUse.threadStart(r);
        }finally {
            exit();
        }
    }

    @Override
    public void threadExit(Runnable r) {
        try {
            enter();
            this.inUse.threadExit(r);
        }finally {
            exit();
        }
    }

    @Override
    public void enter() {
        this.depth += 1;

        if (this.depth == 1) {
            // First call
            this.firstEnter();
        }
        if (this.depth > 1) {
            this.inUse = passthrough;
        }
    }

    @Override
    public void exit() {
        this.depth -= 1;

        if (this.depth == 0) {
            this.inUse = this.strategy;
            this.lastExit();
        }
    }

    @Override
    public void exitLogic() {
        try{
            enter();
            inUse.exitLogic();
        }finally {
            exit();
        }
    }

    @Override
    public int read(JMVXRandomAccessFile f) throws IOException {
        try {
            enter();
            return this.inUse.read(f);
        } finally {
            exit();
        }
    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] b) throws IOException {
        try {
            enter();
            return this.inUse.read(f, b);
        } finally {
            exit();
        }
    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] b, int off, int len) throws IOException {
        try {
            enter();
            return this.inUse.read(f, b, off, len);
        } finally {
            exit();
        }
    }

    @Override
    public void open(JMVXRandomAccessFile f, String name, int mode) throws FileNotFoundException {
        try{
            enter();
            this.inUse.open(f, name, mode);
        }finally {
            exit();
        }
    }

    @Override
    public void close(JMVXRandomAccessFile f) throws IOException {
        try {
            enter();
            this.inUse.close(f);
        } finally {
            exit();
        }
    }

    @Override
    public void write(JMVXRandomAccessFile f, int b) throws IOException {
        try {
            enter();
            this.inUse.write(f, b);
        } finally {
            exit();
        }
    }

    @Override
    public void write(JMVXRandomAccessFile f, byte[] b) throws IOException {
        try {
            enter();
            this.inUse.write(f, b);
        } finally {
            exit();
        }
    }

    @Override
    public void write(JMVXRandomAccessFile f, byte[] b, int off, int len) throws IOException {
        try {
            enter();
            this.inUse.write(f, b, off, len);
        } finally {
            exit();
        }
    }

    @Override
    public long length(JMVXRandomAccessFile f) throws IOException {
        try{
            enter();
            return this.inUse.length(f);
        }finally {
            exit();
        }
    }

    @Override
    public void setLength(JMVXRandomAccessFile f, long newLength) throws IOException {
        try{
            enter();
            this.inUse.setLength(f, newLength);
        }finally {
            exit();
        }
    }

    @Override
    public void seek(JMVXRandomAccessFile f, long pos) throws IOException {
        try{
            enter();
            this.inUse.seek(f, pos);
        }finally {
            exit();
        }
    }

    @Override
    public int read(JMVXFileChannel c, ByteBuffer dst) throws IOException {
        try{
            enter();
            return this.inUse.read(c, dst);
        }finally {
            exit();
        }
    }

    @Override
    public long size(JMVXFileChannel c) throws IOException {
        try{
            enter();
            return this.inUse.size(c);
        }finally {
            exit();
        }
    }

    protected void firstEnter() { /* Empty */ }

    protected void lastExit() { /* Empty */ }

    @Override
    public Class<?> loadClass(JMVXClassLoader loader, String name) throws ClassNotFoundException {
        return this.strategy.loadClass(loader, name);
    }

    @Override
    public Class<?> loadClass(JMVXClassLoader loader, String name, boolean resolve) throws ClassNotFoundException {
        return this.strategy.loadClass(loader, name, resolve);
    }

    @Override
    public void clinitStart(String cls) {
        this.strategy.clinitStart(cls);
    }

    @Override
    public void clinitEnd(String cls) {
        this.strategy.clinitEnd(cls);
    }

    @Override
    public long currentTimeMillis() {
        enter();
        try {
            return this.inUse.currentTimeMillis();
        }finally {
            exit();
        }
    }

    @Override
    public long nanoTime() {
        enter();
        try{
            return this.strategy.nanoTime();
        }finally {
            exit();
        }
    }

    @Override
    public void systemExit(int status) {
        strategy.systemExit(status);
    }

    @Override
    public void initIDs() {
        //still need enter/exit even though we always use the startegy
        //allows us to write data to jmvx without logging the log's write
        enter();
        try{
            this.inUse.initIDs();
        }finally {
            exit();
        }
    }

    @Override
    public long getEntry(long jzfile, byte[] name, boolean addSlash) {
        enter();
        try{
            return this.inUse.getEntry(jzfile, name, addSlash);
        }finally {
            exit();
        }
    }

    @Override
    public void freeEntry(long jzfile, long jzentry) {
        enter();
        try{
            this.inUse.freeEntry(jzfile, jzentry);
        }finally {
            exit();
        }
    }

    @Override
    public long getNextEntry(long jzfile, int i) {
        enter();
        try{
            return this.inUse.getNextEntry(jzfile, i);
        }finally {
            exit();
        }
    }

    @Override
    public void close(long jzfile) {
        enter();
        try{
            this.inUse.close(jzfile);
        }finally {
            exit();
        }
    }

    @Override
    public long open(String name, int mode, long lastModified, boolean usemmap) throws IOException {
        enter();
        try{
            return this.inUse.open(name, mode, lastModified, usemmap);
        }finally {
            exit();
        }
    }

    @Override
    public int getTotal(long jzfile) {
        enter();
        try{
            return this.inUse.getTotal(jzfile);
        }finally {
            exit();
        }
    }

    @Override
    public boolean startsWithLOC(long jzfile) {
        enter();
        try{
            return this.inUse.startsWithLOC(jzfile);
        }finally {
            exit();
        }
    }

    @Override
    public int read(long jzfile, long jzentry, long pos, byte[] b, int off, int len) {
        enter();
        try{
            return this.inUse.read(jzfile, jzentry, pos, b, off, len);
        }finally {
            exit();
        }
    }

    @Override
    public long getEntryTime(long jzentry) {
        enter();
        try{
            return this.inUse.getEntryTime(jzentry);
        }finally {
            exit();
        }
    }

    @Override
    public long getEntryCrc(long jzentry) {
        enter();
        try{
            return this.inUse.getEntryCrc(jzentry);
        }finally {
            exit();
        }
    }

    @Override
    public long getEntryCSize(long jzentry) {
        enter();
        try{
            return this.inUse.getEntryCSize(jzentry);
        }finally {
            exit();
        }
    }

    @Override
    public long getEntrySize(long jzentry) {
        enter();
        try{
            return this.inUse.getEntrySize(jzentry);
        }finally {
            exit();
        }
    }

    @Override
    public int getEntryMethod(long jzentry) {
        enter();
        try{
            return this.inUse.getEntryMethod(jzentry);
        }finally {
            exit();
        }
    }

    @Override
    public int getEntryFlag(long jzentry) {
        enter();
        try{
            return this.inUse.getEntryFlag(jzentry);
        }finally {
            exit();
        }
    }

    @Override
    public byte[] getCommentBytes(long jzfile) {
        enter();
        try{
            return this.inUse.getCommentBytes(jzfile);
        }finally {
            exit();
        }
    }

    @Override
    public byte[] getEntryBytes(long jzentry, int type) {
        enter();
        try{
            return this.inUse.getEntryBytes(jzentry, type);
        }finally {
            exit();
        }
    }

    @Override
    public String getZipMessage(long jzfile) {
        enter();
        try{
            return this.inUse.getZipMessage(jzfile);
        }finally {
            exit();
        }
    }

    @Override
    public String[] getMetaInfEntryNames(JMVXJarFile jarfile) {
        enter();
        try {
            return this.inUse.getMetaInfEntryNames(jarfile);
        }finally {
            exit();
        }
    }

    @Override
    public Path createTempFile(Path dir, String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        enter();
        try {
            return this.inUse.createTempFile(dir, prefix, suffix, attrs);
        } finally {
            exit();
        }
    }

    @Override
    public Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        enter();
        try {
            return this.inUse.createTempFile(prefix, suffix, attrs);
        } finally {
            exit();
        }
    }

    @Override
    public void copy(JMVXFileSystemProvider fsp, Path source, Path target, CopyOption... options) throws IOException {
        enter();
        try {
            this.inUse.copy(fsp, source, target, options);
        } finally {
            exit();
        }
    }

    @Override
    public void checkAccess(JMVXFileSystemProvider fsp, Path path, AccessMode... modes) throws IOException {
        enter();
        try {
            this.inUse.checkAccess(fsp, path, modes);
        } finally {
            exit();
        }
    }

    @Override
    public int open(UnixPath path, int flags, int mode) throws UnixException {
        enter();
        try{
            return inUse.open(path, flags, mode);
        }finally {
            exit();
        }
    }

    @Override
    public void stat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        enter();
        try{
            inUse.stat(path, attrs);
        }finally {
            exit();
        }
    }

    @Override
    public void lstat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        enter();
        try{
            inUse.lstat(path, attrs);
        }finally {
            exit();
        }
    }

    @Override
    public long opendir(UnixPath path) throws UnixException {
        enter();
        try{
            return inUse.opendir(path);
        }finally {
            exit();
        }
    }

    @Override
    public byte[] readdir(long dir) throws UnixException {
        enter();
        try{
            return inUse.readdir(dir);
        }finally {
            exit();
        }
    }

    @Override
    public void access(UnixPath path, int amode) throws UnixException {
        enter();
        try{
            inUse.access(path, amode);
        }finally {
            exit();
        }
    }

    @Override
    public void closedir(long dir) throws UnixException {
        enter();
        try{
            inUse.closedir(dir);
        }finally {
            exit();
        }
    }

    @Override
    public void mkdir(UnixPath path, int mode) throws UnixException {
        enter();
        try{
            inUse.mkdir(path, mode);
        }finally {
            exit();
        }
    }

    @Override
    public int dup(int fd) throws UnixException {
        enter();
        try{
            return inUse.dup(fd);
        }finally {
            exit();
        }
    }

    @Override
    public long fdopendir(int dfd) throws UnixException {
        enter();
        try {
            return inUse.fdopendir(dfd);
        }finally {
            exit();
        }
    }

    @Override
    public byte[] realpath(UnixPath path) throws UnixException {
        enter();
        try {
            return inUse.realpath(path);
        }finally {
            exit();
        }
    }

    @Override
    public int read(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException{
        try{
            enter();
            return this.inUse.read(impl, fd, address, len);
        }finally {
            exit();
        }
    }

    @Override
    public int pread(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException{
        try{
            enter();
            return this.inUse.pread(impl, fd, address, len, position);
        }finally {
            exit();
        }
    }

    @Override
    public long readv(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException{
        try{
            enter();
            return this.inUse.readv(impl, fd, address, len);
        }finally {
            exit();
        }
    }

    @Override
    public int write(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException{
        try{
            enter();
            return this.inUse.write(impl, fd, address, len);
        }finally {
            exit();
        }
    }

    @Override
    public int pwrite(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len, long position) throws IOException{
        try{
            enter();
            return this.inUse.pwrite(impl, fd, address, len, position);
        }finally {
            exit();
        }
    }

    @Override
    public long writev(JMVXFileDispatcherImpl impl, FileDescriptor fd, long address, int len) throws IOException{
        try{
            enter();
            return this.inUse.writev(impl, fd, address, len);
        }finally {
            exit();
        }
    }

    @Override
    public long seek(JMVXFileDispatcherImpl impl, FileDescriptor fd, long offset) throws IOException{
        try{
            enter();
            return this.inUse.seek(impl, fd, offset);
        }finally {
            exit();
        }
    }

    @Override
    public int force(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean metaData) throws IOException {
        try{
            enter();
            return this.inUse.force(impl, fd, metaData);
        }finally {
            exit();
        }
    }

    @Override
    public int truncate(JMVXFileDispatcherImpl impl, FileDescriptor fd, long size) throws IOException {
        try{
            enter();
            return this.inUse.truncate(impl, fd, size);
        }finally {
            exit();
        }
    }

    @Override
    public long size(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        try{
            enter();
            return this.inUse.size(impl, fd);
        }finally {
            exit();
        }
    }

    @Override
    public int lock(JMVXFileDispatcherImpl impl, FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException {
        try{
            enter();
            return this.inUse.lock(impl, fd, blocking, pos, size, shared);
        }finally {
            exit();
        }
    }

    @Override
    public void release(JMVXFileDispatcherImpl impl, FileDescriptor fd, long pos, long size) throws IOException {
        try{
            enter();
            this.inUse.release(impl, fd, pos, size);
        }finally {
            exit();
        }
    }

    @Override
    public void close(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        try{
            enter();
            this.inUse.close(impl, fd);
        }finally {
            exit();
        }
    }

    @Override
    public void preClose(JMVXFileDispatcherImpl impl, FileDescriptor fd) throws IOException {
        try{
            enter();
            this.inUse.preClose(impl, fd);
        }finally {
            exit();
        }
    }

    @Override
    public String getSystemTimeZoneID(String javaHome) {
        try{
            enter();
            return this.inUse.getSystemTimeZoneID(javaHome);
        }finally {
            exit();
        }
    }
}
