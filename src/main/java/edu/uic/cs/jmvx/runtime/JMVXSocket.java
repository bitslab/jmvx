package edu.uic.cs.jmvx.runtime;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.IllegalBlockingModeException;

public interface JMVXSocket {
    //only use this version of connect b/c the other version calls this one!
    public void $JMVX$connect(SocketAddress endpoint, int timeout) throws IOException;
            //, SocketTimeoutException, IllegalBlockingModeException, IllegalArgumentException;

    //probably should implement bind in the future....
}
