package edu.uic.cs.jmvx.coordinate;

import org.newsclub.net.unix.AFUNIXSocket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ThreadContainer {

    private String threadName;
    private Thread coordinatorThread;
    private AFUNIXSocket sock;
    private ObjectInputStream in;
    public ObjectOutputStream out;

    public int id;

    public ThreadContainer(AFUNIXSocket sock, boolean leader) throws IOException, ClassNotFoundException {
        this.sock = sock;
        this.in = new ObjectInputStream(sock.getInputStream());
        this.out = new ObjectOutputStream(sock.getOutputStream());
        this.out.flush();
        this.threadName = (String)in.readObject();
        if (leader)
            this.id = in.readInt();
    }

    public ThreadContainer(String threadName, AFUNIXSocket sock, ObjectInputStream in) {
        this.threadName = threadName;
        this.sock = sock;
        this.in = in;
    }

    public void setCoordinatorThread(Thread coordinatorThread){
        this.coordinatorThread = coordinatorThread;
    }

    public Thread getCoordinatorThread(){
        return coordinatorThread;
    }

    @Override
    public int hashCode() {
        return this.threadName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof ThreadContainer){
            return this.threadName.equals(((ThreadContainer) obj).threadName);
        }
        return super.equals(obj);
    }

    public String getThreadName() {
        return threadName;
    }

    public AFUNIXSocket getSocket() {
        return sock;
    }

    public ObjectInputStream getObjectInputStream() {
        return in;
    }

    /*
    Coordinator has an equivalent thread for each Leader thread,
    The name of Coordinator thread, has an underscore appended to its Leader counterpart.
    Given the Coordinator thread name, this method returns the name of the corresponding Leader thread:
     */
    public static String getLeaderThreadName(String coordinatorThreadName){
        if(coordinatorThreadName.charAt(0)!='_')
            throw new Error("Failed to convert CoordinatorThread name to LeaderThread name");
        return coordinatorThreadName.substring(1);
    }
}
