package edu.uic.cs.jmvx.coordinate;

import org.newsclub.net.unix.AFUNIXSelectorProvider;
import org.newsclub.net.unix.AFUNIXServerSocketChannel;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.*;

public class CoordinatorSwitchingThread implements Runnable {
    @Override
    public void run() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        AFUNIXSelectorProvider provider = AFUNIXSelectorProvider.provider();
        AFUNIXServerSocketChannel server = null;
        AFUNIXSocket socket = null;
        while (Coordinator.isLeaderConnected.get()) {
            try {
                server = provider.openServerSocketChannel(Coordinator.PROMOTE_DEMOTE_ADDR);
                socket = server.accept().socket();
                System.out.println("Role switching ready");
                while(!reader.ready()) {
                    //noinspection BusyWait
                    Thread.sleep(500);
                    //Interrupted when Coordinator.leaderExit is called
                }
                String s = reader.readLine();
                //s != null check is needed to prevent a crash when there is no stdin
                //i.e., someone started the coordinator as a bg process!
                if (s != null && s.isEmpty()) {
                    OutputStream out = socket.getOutputStream();
                    out.write(1);
                    out.flush();
                    out.close();
                    System.out.println("Role switching complete");
                }
                socket.close();
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException ignored){
                //Go around the loop again and check if leader is still connected
            }
            finally {
                try {
                    if (server != null && socket != null) {
                        socket.close();
                        server.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
