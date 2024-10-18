package edu.uic.cs.jmvx.runtime.strategy;

import edu.uic.cs.jmvx.coordinate.Coordinator;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

// This needs to extend Thread so that it doesn't go through JMVX's instrumentation for new Thread(Runnable)
public class LeaderSwitchingThread extends Thread {
    public static final String NAME = "leaderSwitchingThread";
    /*default*/ static Set<Thread> leaderThreads;

    public LeaderSwitchingThread() {
        // This needs to create a thread with a name, otherwise the counter for the name "Thread-N" on the leader diverges from the follower
        super(NAME);
        leaderThreads = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void run() {
        AFUNIXSocket socket = null;
        InputStream inputStream =  null;
        try {
            socket = AFUNIXSocket.newInstance();
            socket.setSoTimeout(500); //throws SocketException
            socket.connect(Coordinator.PROMOTE_DEMOTE_ADDR);
            inputStream = socket.getInputStream();
            while(leaderThreads.size()!=0) {
                try {
                    int val = inputStream.read();
                    if (val == 1) {
                        Leader.one.set(1);
                    }
                } catch(SocketTimeoutException ignored){ }
            }
            inputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null && inputStream != null) {
                    inputStream.close();
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void addThreads(Thread t){
        //Don't include daemon threads, as they do not exit before program execution ends
        if(!t.isDaemon())
            leaderThreads.add(t);
    }

    static void removeThreads(Thread t){
        leaderThreads.remove(t);
    }
}
