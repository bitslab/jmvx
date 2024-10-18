package edu.uic.cs.jmvx.runtime;

import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;

import java.io.IOException;
import java.io.InputStream;

public class CircularBufferInputStream extends InputStream {

    private CircularBuffer buffer;

    public CircularBufferInputStream(CircularBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException {
        throw new Error("Not implemented");
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int dataLen = buffer.readData(b, off, len);

        if (len < dataLen) {
            // Requested less data than available
            throw new Error("Not supported");
        }

       return dataLen;
    }
}
