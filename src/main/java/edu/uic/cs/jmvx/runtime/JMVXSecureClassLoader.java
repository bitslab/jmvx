package edu.uic.cs.jmvx.runtime;

import java.security.CodeSource;

public interface JMVXSecureClassLoader {
    Class<?> $JMVX$defineClass(String name, byte[] b, int off, int len, CodeSource cs);
    Class<?> $JMVX$defineClass(String name, java.nio.ByteBuffer b, CodeSource cs);
}
