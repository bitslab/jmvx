package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXOutputStream;
import edu.uic.cs.jmvx.runtime.JMVXSocketOutputStream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.util.Optional;

public class SocketOutputStreamClassVisitor extends FlagClassVisitor{

    public SocketOutputStreamClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType("Ljava/net/SocketOutputStream;"), Type.getType(OutputStream.class), JMVXSocketOutputStream.class);
    }

    @Override
    protected boolean isType(Type t) {
        return t.getClassName().equals("java.net.SocketOutputStream");
        /*Code below tends to lead to deadlock in h2, could be because of the benchmark's server being multithreaded.
        NOTE: try again after we get threading working.
        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return Class.forName("java.net.SocketOutputStream").isAssignableFrom(c);
        }catch (ClassNotFoundException | LinkageError e){
            logger.trace("I don't know if this is an SocketOutputStream or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }*/
    }
}
