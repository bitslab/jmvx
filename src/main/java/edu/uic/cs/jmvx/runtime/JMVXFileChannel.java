package edu.uic.cs.jmvx.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Read and writes on a FileChannel
 */
public interface JMVXFileChannel {
    public int $JMVX$read(ByteBuffer dst) throws IOException;
    public long $JMVX$size() throws IOException;
}
