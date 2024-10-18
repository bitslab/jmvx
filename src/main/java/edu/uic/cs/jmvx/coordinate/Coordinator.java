package edu.uic.cs.jmvx.coordinate;

import org.newsclub.net.unix.*;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class Coordinator {
    // Don't make this a final field because the java compiler is stupid and inlines it IN THE BYTECODE
    public static int getCoordinatorLeaderPort()   { return Integer.parseInt(System.getProperty("COORD_L_PORT", "1234")); }
    public static int getCoordinatorFollowerPort() { return Integer.parseInt(System.getProperty("COORD_F_PORT", "1235")); }

    private static final String              LEADER_COMMS_FILENAME, FOLLOWER_COMMS_FILENAME, PROMOTE_DEMOTE_FILENAME;
    public  static final AFUNIXSocketAddress LEADER_COMMS_ADDR,     FOLLOWER_COMMS_ADDR,     PROMOTE_DEMOTE_ADDR;

    //probably should just use a set and iterate over it
    public static final HashMap<String, byte[]> clinits = new HashMap<>();

    static Map<String, ThreadContainer> leaderThreads;
    static Map<String, ThreadContainer> followerThreads;
    public static Thread coordinatorSwitchingThread;

    /*default*/ static AtomicBoolean isLeaderConnected;

    static {
        LEADER_COMMS_FILENAME   = System.getenv("ROOT") + "/leaderComms";
        FOLLOWER_COMMS_FILENAME = System.getenv("ROOT") + "/followerComms";
        PROMOTE_DEMOTE_FILENAME = System.getenv("ROOT") + "/promoteDemote";

        isLeaderConnected = new AtomicBoolean();

        try {
            LEADER_COMMS_ADDR   = AFUNIXSocketAddress.of(new File(LEADER_COMMS_FILENAME));
            FOLLOWER_COMMS_ADDR = AFUNIXSocketAddress.of(new File(FOLLOWER_COMMS_FILENAME));
            PROMOTE_DEMOTE_ADDR = AFUNIXSocketAddress.of(new File(PROMOTE_DEMOTE_FILENAME));
        } catch (SocketException e) {
            throw new Error(e);
        }
    }

    @Deprecated
    private static void startClinitThreads(ThreadContainer leader, ThreadContainer follower){
        //threads to handle storing clinits
        Thread clinitsIn = new Thread(() -> {
            try {
                AFUNIXSocket sock = leader.getSocket();
                ObjectInputStream fromLeader = leader.getObjectInputStream();//new ObjectInputStream(sock.getInputStream());
                while(true){
                    Object n = fromLeader.readObject();
                    if(!(n instanceof String)){
                        throw new Error("Non String sent to clinitIn when class name expected");
                    }
                    Object b = fromLeader.readObject();
                    if(!(b instanceof byte[])){
                        throw new Error("Non byte[] sent to clinitIn when byte[] expected");
                    }
                    synchronized (clinits) {
                        clinits.putIfAbsent((String)n, (byte[])b);
                        clinits.notify();
                    }
                }
            }catch (IOException | ClassNotFoundException e){
                e.printStackTrace();
            }
        });

        Thread clinitsOut = new Thread(() -> {
            try {
                AFUNIXSocket sock = follower.getSocket();
                ObjectInputStream fromFollower = follower.getObjectInputStream();//new ObjectInputStream(sock.getInputStream());
                ObjectOutputStream toFollower = new ObjectOutputStream(sock.getOutputStream());
                while(true){
                    Object o = fromFollower.readObject();
                    if(!(o instanceof String)){
                        throw new Error("Non string (clinit class name) object sent to clinitOut thread");
                    }
                    while(true) {
                        synchronized (clinits) {
                            byte[] dat = clinits.get(o);
                            //Leader.Clinit dat = clinits.get(o);
                            if (dat == null) {
                                clinits.wait();
                                continue;
                            } else {
                                toFollower.writeObject(dat);
                                toFollower.flush();
                                toFollower.reset();
                                break;
                            }
                        }
                    }
                }
            }catch (IOException | ClassNotFoundException | InterruptedException e){
                //may want to move interrupted exception...
                e.printStackTrace();
            }
        });

        clinitsIn.setName("clinitsIn");
        clinitsIn.setDaemon(true);
        clinitsOut.setName("clinitsOut");
        clinitsOut.setDaemon(true);

        clinitsIn.start();
        clinitsOut.start();
    }

    private static Thread startThread(ThreadContainer leader, ThreadContainer follower, AtomicInteger clockIndex){
        if(leader.getThreadName().equals("clinit")){
            throw new Error("Should be disabled");
            /*
            startClinitThreads(leader, follower);
            return null; //TODO: Get thread list from startClinitThreads and return
            */
        }else {
            //threads for program logic
            Thread t = new Thread(new CoordinatorThread(leader, follower, clockIndex));
            t.setName("_" + leader.getThreadName()); //for debugging purposes :> (appending _ to differentiate "main" threads)
            t.setDaemon(true); // This causes the main program NOT to wait for t to end before exiting
            t.start();
            return t;
        }
    }

    private static AFUNIXSocket[] connectLeaderFollower(AFUNIXServerSocket leader, AFUNIXServerSocket follower) {

        AFUNIXSocket leaderSocket;
        AFUNIXSocket followerSocket;

        try {
            leaderSocket   = leader.accept();
        } catch (IOException e) {
            return new AFUNIXSocket[0];
        }

        try {
            followerSocket = follower.accept();
        } catch (IOException e) {
            try {
                leaderSocket.close();
            } catch (IOException ex) {
                // Do not care
            }
            return new AFUNIXSocket[0];
        }

        return new AFUNIXSocket[]{ leaderSocket, followerSocket };
    }

    public static void selectors() {
//        AFUNIXServerSocket leaderServerSocket;
//        AFUNIXServerSocket followerServerSocket;
//
//        try {
//            File leaderCommsFile = new File(leaderCommsFilename);
//            leaderServerSocket = AFUNIXServerSocket.newInstance();
//            leaderServerSocket.bind(AFUNIXSocketAddress.of(leaderCommsFile));
//        } catch (IOException e) {
//            throw new Error(e);
//        }
//
//        try {
//            File followerCommsFile = new File(followerCommsFilename);
//            followerServerSocket = AFUNIXServerSocket.newInstance();
//            followerServerSocket.bind(AFUNIXSocketAddress.of(followerCommsFile));
//        } catch (IOException e) {
//            throw new Error(e);
//        }

        AFUNIXSelectorProvider provider = AFUNIXSelectorProvider.provider();
        AFUNIXServerSocketChannel leaderCh, followerCh;

        Selector acceptSelector;
        SelectionKey leaderKey, followerKey;
        AtomicInteger clockIndex = new AtomicInteger(1); //1 b/c slot 0 is clinit

        try {
            leaderCh = provider.openServerSocketChannel(LEADER_COMMS_ADDR);
            followerCh = provider.openServerSocketChannel(FOLLOWER_COMMS_ADDR);

            leaderCh.configureBlocking(false);
            followerCh.configureBlocking(false);

            acceptSelector = provider.openSelector();

            leaderKey   = leaderCh.register(acceptSelector,   SelectionKey.OP_ACCEPT);
            followerKey = followerCh.register(acceptSelector, SelectionKey.OP_ACCEPT);

        } catch (IOException e) {
            throw new Error(e);
        }


        Map<String, ThreadContainer> leaderThreads = new HashMap<>();
        Map<String, ThreadContainer> followerThreads = new HashMap<>();

        try {
            while (true) {

                acceptSelector.select();
                Set<SelectionKey> keys = acceptSelector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();

                while(iter.hasNext()){
                    SelectionKey key = iter.next();
                    AFUNIXSocket s;

                    if(key.equals(leaderKey)){
                        //trying to avoid IllegalBlockingModeException
                        //new socket channel, and thus socket, will be in blocking mode by default
                        s = leaderCh.accept().socket();
                        ThreadContainer leaderContainer = new ThreadContainer(s, true);
                        leaderThreads.put(leaderContainer.getThreadName(), leaderContainer);
                        ThreadContainer followerContainer = followerThreads.get(leaderContainer.getThreadName());
                        if(followerContainer != null){
                            startThread(leaderContainer, followerContainer, clockIndex);
                        }
                    }else{
                        s = followerCh.accept().socket();
                        ThreadContainer followerContainer = new ThreadContainer(s, false);
                        followerThreads.put(followerContainer.getThreadName(), followerContainer);
                        ThreadContainer leaderContainer = leaderThreads.get(followerContainer.getThreadName());
                        if(leaderContainer != null){
                            startThread(leaderContainer, followerContainer, clockIndex);
                        }
                    }
                    iter.remove();
                }
            }
        }catch (IOException | ClassNotFoundException e){
            throw new Error(e);
        }
    }

    public static void blocking() {
        AFUNIXSelectorProvider provider = AFUNIXSelectorProvider.provider();
        AFUNIXServerSocketChannel leaderCh, followerCh;

        try {
            leaderCh = provider.openServerSocketChannel(LEADER_COMMS_ADDR);
            followerCh = provider.openServerSocketChannel(FOLLOWER_COMMS_ADDR);
        } catch (IOException e) {
            throw new Error(e);
        }

        leaderThreads = new HashMap<>();
        followerThreads = new HashMap<>();
        AtomicInteger clockIndex = new AtomicInteger(1);

        Thread leaderAcceptor   = new Thread(() -> {
            while (true) {
                try {
                    AFUNIXSocket leaderSocket = leaderCh.accept().socket();
                    ThreadContainer leaderContainer = new ThreadContainer(leaderSocket, true);
                    synchronized (Coordinator.class) {
                        leaderThreads.put(leaderContainer.getThreadName(), leaderContainer);
                        isLeaderConnected.set(true);
                        Coordinator.class.notifyAll();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    throw new Error(e);
                }
            }
        });
        leaderAcceptor.setDaemon(true);

        Thread followerAcceptor = new Thread(() -> {
            while (true) {
                try {
                    AFUNIXSocket followerSocket = followerCh.accept().socket();
                    ThreadContainer followerContainer = new ThreadContainer(followerSocket, false);
                    synchronized (Coordinator.class) {
                        followerThreads.put(followerContainer.getThreadName(), followerContainer);
                        Coordinator.class.notifyAll();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    throw new Error(e);
                }
            }
        });
        followerAcceptor.setDaemon(true);

        leaderAcceptor.start();
        followerAcceptor.start();

        while (true) {
            synchronized (Coordinator.class) {
                for (Map.Entry<String, ThreadContainer> leader : leaderThreads.entrySet()) {
                    ThreadContainer follower = followerThreads.remove(leader.getKey());
                    if (follower != null) {
                        leader.getValue().setCoordinatorThread(startThread(leader.getValue(), follower, clockIndex));
                    }
                }

                try {
                    Coordinator.class.wait(500);
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }
    }

    public static void _main() {
        blocking();
    }

    /*default*/ static void leaderExit() {
        isLeaderConnected.set(false);
        //CoordinatorSwitchingThread is likely within Thread.sleep(), busy waiting for the reader to be ready
        coordinatorSwitchingThread.interrupt();
        System.out.println("Leader finished executing");
    }
}
