package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXServerSocket;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.net.ServerSocket;
import java.util.Optional;

public class ServerSocketClassVisitor extends RenameClassVisitor{

    public ServerSocketClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType(ServerSocket.class), JMVXServerSocket.class);
    }

    @Override
    protected boolean isType(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return ServerSocket.class.isAssignableFrom(c);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            logger.trace("I don't know if this is a ServerSocket or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }
    }
}
