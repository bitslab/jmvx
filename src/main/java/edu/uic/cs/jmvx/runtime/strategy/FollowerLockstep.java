package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.runtime.JMVXInputStream;
import edu.uic.cs.jmvx.runtime.JMVXOutputStream;

import java.io.IOException;

public class FollowerLockstep extends Follower{

    private void notifyCoordinator() throws IOException {
        //Currently the same as sendMode but serves a different purpose
        //May need to change in future
        toCoordinator.writeBoolean(true);
        toCoordinator.flush();
        toCoordinator.reset();
    }

    @Override
    protected void sendMode() throws IOException {
        //Value is compared with lockstepMode on Coordinator
        toCoordinator.writeBoolean(true);
        toCoordinator.flush();
        toCoordinator.reset();
    }

    @Override
    public int read(JMVXInputStream is) throws IOException {
        int ret = super.read(is);
        notifyCoordinator();
        return ret;
    }

    @Override
    public int read(JMVXInputStream is, byte[] bytes) throws IOException {
        int ret = super.read(is, bytes);
        notifyCoordinator();
        return ret;
    }

    @Override
    public int read(JMVXInputStream is, byte[] bytes, int off, int len) throws IOException {
        int ret = super.read(is, bytes, off, len);
        notifyCoordinator();
        return ret;
    }

    @Override
    public void write(JMVXOutputStream os, int b) throws IOException {
        super.write(os, b);
        notifyCoordinator();
    }

    @Override
    public void write(JMVXOutputStream os, byte[] bytes) throws IOException {
        super.write(os, bytes);
        notifyCoordinator();
    }

    @Override
    public void write(JMVXOutputStream os, byte[] bytes, int off, int len) throws IOException {
        super.write(os, bytes, off, len);
        notifyCoordinator();
    }
}
