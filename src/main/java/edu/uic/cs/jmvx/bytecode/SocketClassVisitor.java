package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXOutputStream;
import edu.uic.cs.jmvx.runtime.JMVXSocket;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Optional;

public class SocketClassVisitor extends RenameClassVisitor {

    public SocketClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType(Socket.class), JMVXSocket.class);
    }

    @Override
    protected boolean isType(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return Socket.class.isAssignableFrom(c);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            logger.trace("I don't know if this is a Socket or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }
    }
}
