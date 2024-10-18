package edu.uic.cs.jmvx.runtime;

import java.io.IOException;
import java.net.SocketAddress;

public interface JMVXProcessBuilder {
    Process $JMVX$start() throws IOException;
}
