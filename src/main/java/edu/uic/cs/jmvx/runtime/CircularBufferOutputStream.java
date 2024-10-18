package edu.uic.cs.jmvx.runtime;

import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;

import java.io.IOException;
import java.io.OutputStream;

public class CircularBufferOutputStream extends OutputStream {

    private CircularBuffer buffer;

    public CircularBufferOutputStream(CircularBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void write(int b) throws IOException {
        throw new Error("Not supported");
    }

    @Override
    public void write(byte[] b) throws IOException {
        buffer.writeData(b, true);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        buffer.writeData(b, off, len);
    }
}
