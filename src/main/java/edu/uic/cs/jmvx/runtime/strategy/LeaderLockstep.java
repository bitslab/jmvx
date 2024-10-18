package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.runtime.JMVXInputStream;
import edu.uic.cs.jmvx.runtime.JMVXOutputStream;

import java.io.IOException;

public class LeaderLockstep extends Leader{

    private void waitOnCoordinator() {
        boolean coordinatorReady = false;
        while (!coordinatorReady) {
            try {
                coordinatorReady = fromCoordinator.readBoolean();
                Thread.sleep(10);
            } catch (InterruptedException | IOException ie) {
                ie.printStackTrace();
            }
        }
    }

    @Override
    protected void sendMode() throws IOException {
        //Value is stored in Boolean lockstepMode on Coordinator
        toCoordinator.writeBoolean(true);
        toCoordinator.flush();
        toCoordinator.reset();
    }

    @Override
    public int read(JMVXInputStream is) throws IOException {
        int ret = super.read(is);
        waitOnCoordinator();
        return ret;
    }

    @Override
    public int read(JMVXInputStream is, byte[] bytes) throws IOException {
        int ret = super.read(is, bytes);
        waitOnCoordinator();
        return ret;
    }

    @Override
    public int read(JMVXInputStream is, byte[] bytes, int off, int len) throws IOException {
        int ret = super.read(is, bytes, off, len);
        waitOnCoordinator();
        return ret;
    }

    @Override
    public void write(JMVXOutputStream os, int b) throws IOException {
        super.write(os, b);
        waitOnCoordinator();
    }

    @Override
    public void write(JMVXOutputStream os, byte[] bytes) throws IOException {
        super.write(os, bytes);
        waitOnCoordinator();
    }

    @Override
    public void write(JMVXOutputStream os, byte[] bytes, int off, int len) throws IOException {
        super.write(os, bytes, off, len);
        waitOnCoordinator();
    }
}
