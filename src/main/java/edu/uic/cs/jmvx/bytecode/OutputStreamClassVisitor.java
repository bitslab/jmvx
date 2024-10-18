package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXOutputStream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.util.Optional;

public class OutputStreamClassVisitor extends RenameClassVisitor {

    public OutputStreamClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType(OutputStream.class), JMVXOutputStream.class);
    }

    protected boolean isType(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        if (!InputStreamClassVisitor.isInterestingPackage(t))
            return false;

        /*
        Not using Class.forName("java.net.SocketOutputStream").isAssignableFrom(c) because it caused unexpected behavior
        Checking by the class name has been the most consistent method so far. Come back to this after threads work.
         */
        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return !t.equals(Type.getType(OutputStream.class)) && OutputStream.class.isAssignableFrom(c) && !FilterOutputStream.class.isAssignableFrom(c) && !ByteArrayOutputStream.class.isAssignableFrom(c) && !c.getName().equals("java.net.SocketOutputStream");
                    //!Class.forName("java.net.SocketOutputStream").isAssignableFrom(c);
        } catch (ClassNotFoundException | LinkageError e) { //NoClassDefFoundError | IllegalAccessError e) {
            logger.trace("I don't know if this is an OutputStream or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }
    }
}
