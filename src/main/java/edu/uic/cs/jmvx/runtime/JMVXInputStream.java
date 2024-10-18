package edu.uic.cs.jmvx.runtime;

import java.io.IOException;

public interface JMVXInputStream {
    public int $JMVX$read() throws IOException;

    public int $JMVX$read(byte[] b) throws IOException;

    public int $JMVX$read(byte[] b, int off, int len) throws IOException;

    public int $JMVX$available() throws IOException;
}
