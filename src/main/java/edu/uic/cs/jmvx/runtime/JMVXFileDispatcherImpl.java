package edu.uic.cs.jmvx.runtime;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Some methods, e.g. close, do not fail silently for this class
 * Throws invalid FileDescriptor exception.
 */
public interface JMVXFileDispatcherImpl {
    int $JMVX$read(FileDescriptor fd, long address, int len) throws IOException;
    int $JMVX$pread(FileDescriptor fd, long address, int len, long position) throws IOException;
    long $JMVX$readv(FileDescriptor fd, long address, int len) throws IOException;
    int $JMVX$write(FileDescriptor fd, long address, int len) throws IOException;
    int $JMVX$pwrite(FileDescriptor fd, long address, int len, long position) throws IOException;
    long $JMVX$writev(FileDescriptor fd, long address, int len) throws IOException;
    long $JMVX$seek(FileDescriptor fd, long offset) throws IOException;
    int $JMVX$force(FileDescriptor fd, boolean metaData) throws IOException;
    int $JMVX$truncate(FileDescriptor fd, long size) throws IOException;
    long $JMVX$size(FileDescriptor fd) throws IOException;
    int $JMVX$lock(FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException;
    void $JMVX$release(FileDescriptor fd, long pos, long size) throws IOException;
    void $JMVX$close(FileDescriptor fd) throws IOException; //this is the only method called from Dacapo's Harness
    void $JMVX$preClose(FileDescriptor fd) throws IOException;

    /**
     * Epoll and some selectors may use
     * closeIntFd, which is a package private native method that does not have a wrapper
     * Jython may trigger this, should watch for it.
     */
}
