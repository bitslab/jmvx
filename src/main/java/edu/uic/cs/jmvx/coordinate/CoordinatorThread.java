package edu.uic.cs.jmvx.coordinate;

import edu.uic.cs.jmvx.runtime.FileDescriptorUtils;
import edu.uic.cs.jmvx.runtime.SwitchRolesEvent;
import edu.uic.cs.jmvx.runtime.strategy.Leader;
import org.newsclub.net.unix.AFUNIXSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class CoordinatorThread implements Runnable {

    public static final String exitMessage = "RTE";
    public static final String finalExitMessage = "FRTE";
    public static final String startMessage = "StartSwitching";
    private final int clockIndex;

    private AFUNIXSocket leaderSocket;
    private AFUNIXSocket followerSocket;

    private ObjectInputStream  fromLeader;
    private ObjectOutputStream toFollower;

    private ObjectInputStream fromFollower;
    private ObjectOutputStream toLeader;

    private boolean lockstep = false;
    private boolean isRunning = true;

    private static final boolean fastExit = System.getProperty("fastExit", "true").equals("true");
    /*public CoordinatorThread(AFUNIXSocket leaderSocket, AFUNIXSocket followerSocket) {
        this.leaderSocket = leaderSocket;
        this.followerSocket = followerSocket;

    }*/

    public CoordinatorThread(ThreadContainer leader, ThreadContainer follower, AtomicInteger clockIndex) {
        this.leaderSocket = leader.getSocket();
        try {
            this.leaderSocket.setSoTimeout(0);
        } catch (SocketException e) {
            throw new Error(e);
        }
        //leader can receive fds!
        this.leaderSocket.setAncillaryReceiveBufferSize(1024);
        this.fromLeader = leader.getObjectInputStream();
        this.toLeader = leader.out;

        this.followerSocket = follower.getSocket();
        try {
            this.followerSocket.setSoTimeout(0);
        } catch (SocketException e) {
            throw new Error(e);
        }
        this.fromFollower = follower.getObjectInputStream();
        this.toFollower = follower.out;

        this.clockIndex = leader.id;
    }

    public void init() throws IOException {
        //fromLeader   = new ObjectInputStream(leaderSocket.getInputStream());
//        toLeader     = new ObjectOutputStream(leaderSocket.getOutputStream());
//        toLeader.writeInt(this.clockIndex);
//        toLeader.flush();

//        toFollower   = new ObjectOutputStream(followerSocket.getOutputStream());
        toFollower.writeInt(this.clockIndex);
        toFollower.flush();
        //fromFollower = new ObjectInputStream(followerSocket.getInputStream());


        boolean leaderLockstepMode   = fromLeader.readBoolean();
        boolean followerLockstepMode = fromFollower.readBoolean();

        if (leaderLockstepMode != followerLockstepMode) {
            throw new Error("Incompatible modes detected!\n" + "Leader Lockstep Mode: " + leaderLockstepMode + " Follower Lockstep Mode: " + followerLockstepMode);
        }

        // We're ready to start
//        toLeader.writeBoolean(true);
//        toLeader.flush();
        toFollower.writeBoolean(true);
        toFollower.flush();
    }

    protected void processOneEvent() throws IOException {
        /*
        //attempt at letting the threads asynchronously terminate...
        //requires more thought because this does not prevent
        //code in lockstep() from executing...
        Object r = null;
        try{
            if(leaderSocket.isConnected()){
                r = fromFollower.readObject();
            }
        }catch (IOException | ClassNotFoundException e){}


        try{
            if(r != null) {
                toFollower.writeObject(r);
                toFollower.flush();
                toFollower.reset();
            }else{
                //mainly to block, follower will kill socket and this will cause an exception
                fromFollower.readObject();
            }
        }catch (IOException | ClassNotFoundException e){
            isRunning = false;
        }*/

        Object r = exitMessage; //default is the leader has exited, overwritten if leader is alive
        FileDescriptor[] fds = null;
        if(leaderSocket.isConnected()) {
            try {
                r = fromLeader.readObject();

                //need to check for and write ancillary data here!
                fds = leaderSocket.getReceivedFileDescriptors();
                if (fds != null) {
                    followerSocket.setOutboundFileDescriptors(fds);
                }
            }catch(ClassNotFoundException e){
                throw new Error(e);
            } //leader may break connection early
        }

        try{
            if(r instanceof String) {
                String msg = (String) r;

                //exitMessage: One thread on the leader is Ready To Exit, but other threads still exist
                //finalExitMessage: The last thread on the leader is Ready To Exit
                if (msg.equals(exitMessage) || msg.equals(finalExitMessage)) {
                    //wait for the follower to be prepared to exit
                    Object s = fromFollower.readObject();
                    if(!fastExit){
                        //send ACK if not using fastExit
                        toLeader.writeObject(s);
                        toLeader.flush();
                    }
                    isRunning = false;

                    if(finalExitMessage.equals(msg)) //Last leader thread is ready to exit
                        Coordinator.leaderExit();

                    return;
                }

                if(startMessage.equals(msg)) { //First thread on the leader has started
                    Coordinator.isLeaderConnected.set(true);
                    CoordinatorSwitchingThread switching = new CoordinatorSwitchingThread();
                    Coordinator.coordinatorSwitchingThread = new Thread(switching);
                    Coordinator.coordinatorSwitchingThread.start();
                    toLeader.writeObject("ACK");
                    toLeader.flush();
                    return;
                }

                if(msg.equals("BufferInitialized")){
                    toFollower.writeObject(msg);
                    toFollower.flush();
                    toFollower.reset();

//                    toLeader.writeObject(fromFollower.readObject());
//                    toLeader.flush();
//                    toLeader.reset();
//
//                    toFollower.writeObject(fromLeader.readObject());
//                    toFollower.flush();
//                    toFollower.reset();

                    return;
                }
            }

            while (true) {
                try {
                    toFollower.writeObject(r);
                    break;
                } catch (SocketTimeoutException e) {
                    continue;
                }
            }
            toFollower.flush();
            toFollower.reset();

            if(fds != null) {
                for (int i = 0; i < fds.length; i++) {
                    FileDescriptorUtils.closeFd(fds[i]);
                }
            }

            // if instance of FileDelete
            if (r instanceof Leader.FileDelete){
                toFollower.writeObject(fromLeader.readObject());
                toFollower.flush();
                toFollower.reset();

                // synced
                toLeader.writeObject(fromFollower.readObject());
                toLeader.flush();
                toLeader.reset();

                // continue
                toFollower.writeObject(fromLeader.readObject());
                toFollower.flush();
                toFollower.reset();
            } else if (r instanceof SwitchRolesEvent) {
                this.swapLeaderFollower();
                return;
            }
        } catch (SocketTimeoutException e) {
            throw e; //Propagate
        } catch (ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    /**
     * swap the reference to the connection
     */
    private void swapLeaderFollower() {
        AFUNIXSocket socketCopy = leaderSocket;
        ObjectInputStream inputStreamCopy = fromLeader;
        ObjectOutputStream outputStreamCopy = toLeader;

        leaderSocket = followerSocket;
        fromLeader   = fromFollower;
        toLeader     = toFollower;

        followerSocket = socketCopy;
        fromFollower   = inputStreamCopy;
        toFollower     = outputStreamCopy;
        System.out.println("swapping reference completed for thread: " + Thread.currentThread().getName());
    }

    protected void lockstep() throws IOException {
        if (!this.lockstep)
            return;

        boolean followerReady = false;
        while (!followerReady) {
            followerReady = fromFollower.readBoolean();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e){
                isRunning = false;
                try {
                    leaderSocket.close();
                } catch (IOException ex) { }
                try {
                    followerSocket.close();
                } catch (IOException ex) { }
            }
        }
        toLeader.writeBoolean(true);
        toLeader.flush();
        toLeader.reset();
    }

    @Override
    public void run() {
        try {
            this.init();

            while (isRunning && Coordinator.isLeaderConnected.get()) {
                try {
                    this.processOneEvent();
                    this.lockstep();
                } catch (SocketTimeoutException ignored){ /*Try again*/ }
            }
        } catch (IOException e) {
            try {
                leaderSocket.close();
            } catch (IOException ex) { }
            try {
                followerSocket.close();
            } catch (IOException ex) { }
        } finally {
            //Finished execution:
            String key = ThreadContainer.getLeaderThreadName(Thread.currentThread().getName());
            synchronized (Coordinator.class){
                Coordinator.leaderThreads.remove(key);
            }
        }
    }
}
