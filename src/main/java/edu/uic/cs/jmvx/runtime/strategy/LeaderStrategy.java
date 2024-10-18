package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.runtime.*;
import edu.uic.cs.jmvx.runtime.JMVXFile;
import edu.uic.cs.jmvx.runtime.JMVXFileOutputStream;
import edu.uic.cs.jmvx.runtime.JMVXInputStream;
import edu.uic.cs.jmvx.runtime.JMVXOutputStream;

import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;

public class LeaderStrategy extends SingleLeaderWithoutCoordinator {
//implements JMVXStrategy {


    @Override
    public void main() {

    }

    @Override
    public int read(JMVXInputStream jmvxis) throws IOException {
        return jmvxis.$JMVX$read();
    }

    @Override
    public int read(JMVXInputStream jmvxis, byte[] b) throws IOException {
        return jmvxis.$JMVX$read(b);
    }

    @Override
    public int read(JMVXInputStream jmvxis, byte[] b, int off, int len) throws IOException {
        return jmvxis.$JMVX$read(b, off, len);
    }

    @Override
    public void close(JMVXOutputStream jmvxos) throws IOException {
        jmvxos.$JMVX$close();
    }

    @Override
    public void flush(JMVXOutputStream jmvxos) throws IOException {
        jmvxos.$JMVX$flush();
    }

    @Override
    public void write(JMVXOutputStream jmvxos, int b) throws IOException {
        jmvxos.$JMVX$write(b);
    }

    @Override
    public void write(JMVXOutputStream jmvxos, byte[] b) throws IOException {
        jmvxos.$JMVX$write(b);
    }

    @Override
    public void write(JMVXOutputStream jmvxos, byte[] b, int off, int len) throws IOException {
        jmvxos.$JMVX$write(b, off, len);
    }

    @Override
    public boolean canRead(JMVXFile jmvxf) {
        return jmvxf.$JMVX$canRead();
    }

    @Override
    public boolean canWrite(JMVXFile jmvxf) {
        return jmvxf.$JMVX$canWrite();
    }

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
    public String getAbsolutePath(JMVXFile jmvxf) {
        return jmvxf.$JMVX$getAbsolutePath();
    }

    @Override
    public File getCanonicalFile(JMVXFile jmvxf) throws IOException {
        return jmvxf.$JMVX$getCanonicalFile();
    }

    @Override
    public String getCanonicalPath(JMVXFile jmvxf) throws IOException {
        return jmvxf.$JMVX$getCanonicalPath();
    }

    @Override
    public String getName(JMVXFile jmvxf) {
        return jmvxf.$JMVX$getName();
    }

    @Override
    public File getParentFile(JMVXFile jmvxf) {
        return jmvxf.$JMVX$getParentFile();
    }

    @Override
    public String getPath(JMVXFile f) {
        return ((File)f).getPath();// return f.$JMVX$getPath();
    }

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
    public boolean mkdirs(JMVXFile jmvxf) {
        return jmvxf.$JMVX$mkdirs();
    }

    @Override
    public boolean renameTo(JMVXFile jmvxf, File dest) {
        return jmvxf.$JMVX$renameTo(dest);
    }

    @Override
    public boolean setReadOnly(JMVXFile jmvxf) {
        return jmvxf.$JMVX$setReadOnly();
    }

    @Override
    public URL toURL(JMVXFile jmvxf) throws MalformedURLException {
        return jmvxf.$JMVX$toURL();
    }

    @Override
    public long lastModified(JMVXFile f) {
        return f.$JMVX$lastModified();
    }

    @Override
    public boolean isFile(JMVXFile f) { return f.$JMVX$isFile(); }

    @Override
    public void open(JMVXFileOutputStream jmvxfos, String name, boolean append) throws FileNotFoundException {
        jmvxfos.$JMVX$open(name, append);
    }

    @Override
    public void open(JMVXFileInputStream fis, String name) throws FileNotFoundException {
        fis.$JMVX$open(name);
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
    public void sync(FileDescriptor fdObj) throws SyncFailedException {
        fdObj.sync();
    }

    @Override
    public int read(JMVXRandomAccessFile f) throws IOException {
        return f.$JMVX$read();
    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] b) throws IOException {
        return f.$JMVX$read(b);
    }

    @Override
    public int read(JMVXRandomAccessFile f, byte[] b, int off, int len) throws IOException {
        return f.$JMVX$read(b, off, len);
    }

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
    public void connect(JMVXSocket sock, SocketAddress endpoint, int timeout) throws IOException {
        sock.$JMVX$connect(endpoint, timeout);
    }

    @Override
    public void bind(JMVXServerSocket serv, SocketAddress endpoint, int backlog) throws IOException {
        serv.$JMVX$bind(endpoint, backlog);
    }

    @Override
    public Socket accept(JMVXServerSocket serv) throws IOException {
        return serv.$JMVX$accept();
    }

    @Override
    public void monitorenter(Object o) { JMVXRuntime.unsafe.monitorEnter(o); }

    @Override
    public void monitorexit(Object o) {
        JMVXRuntime.unsafe.monitorExit(o);
    }

    @Override
    public void wait(Object o, long timeout, int nanos) throws InterruptedException {
        o.wait(timeout, nanos);
    }

    @Override
    public int available(JMVXInputStream is) throws IOException { return is.$JMVX$available(); }


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
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}
