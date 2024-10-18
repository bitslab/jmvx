package edu.uic.cs.jmvx.runtime;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

public interface JMVXServerSocket {
    public void $JMVX$bind(SocketAddress endpoint, int backlog) throws IOException;

    public Socket $JMVX$accept() throws IOException;
}
