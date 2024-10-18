package edu.uic.cs.jmvx.runtime;

import java.io.FileNotFoundException;
import java.io.IOException;

public interface JMVXRandomAccessFile {
    public int $JMVX$read() throws IOException;

    public int $JMVX$read(byte[] b) throws IOException;

    public int $JMVX$read(byte[] b, int off, int len) throws IOException;

    public void $JMVX$open(String name, int mode) throws FileNotFoundException;

    public void $JMVX$close() throws IOException;

    public void $JMVX$write(int b) throws IOException;

    public void $JMVX$write(byte[] b) throws IOException;

    public void $JMVX$write(byte[] b, int off, int len) throws IOException;

    public void $JMVX$seek(long pos) throws IOException;

    //find a way to add the length method here? It is instrumented but in a different way b/c it is native
}
