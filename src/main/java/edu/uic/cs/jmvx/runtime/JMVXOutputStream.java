package edu.uic.cs.jmvx.runtime;

import java.io.IOException;

public interface JMVXOutputStream {
    public void $JMVX$close() throws IOException;

    public void $JMVX$flush() throws IOException;

    public void $JMVX$write(int b) throws IOException;

    public void $JMVX$write(byte[] b) throws IOException;

    public void $JMVX$write(byte[] b, int off, int len) throws IOException;
}
