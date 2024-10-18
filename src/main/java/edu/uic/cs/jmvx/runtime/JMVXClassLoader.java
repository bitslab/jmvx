package edu.uic.cs.jmvx.runtime;

public interface JMVXClassLoader {
    public Class<?> $JMVX$loadClass(String name) throws ClassNotFoundException;
    public Class<?> $JMVX$loadClass(String name, boolean resolve) throws ClassNotFoundException;
}
