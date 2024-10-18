package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXSocketInputStream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Optional;

public class SocketInputStreamClassVisitor extends FlagClassVisitor{

    public SocketInputStreamClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType("Ljava/net/SocketInputStream;"), Type.getType(InputStream.class), JMVXSocketInputStream.class);
    }

    @Override
    protected boolean isType(Type t) {
        return t.getClassName().equals("java.net.SocketInputStream");
        /*Code below tends to lead to deadlock in h2, could be because of the benchmark's server being multithreaded.
        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return Class.forName("java.net.SocketInputStream").isAssignableFrom(c);
        }catch (ClassNotFoundException | LinkageError e){
            logger.trace("I don't know if this is an SocketInputStream or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }*/
    }

}
