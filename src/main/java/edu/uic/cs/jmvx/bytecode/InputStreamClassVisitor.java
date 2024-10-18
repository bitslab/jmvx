package edu.uic.cs.jmvx.bytecode;

import com.sun.imageio.plugins.common.InputStreamAdapter;
import edu.uic.cs.jmvx.runtime.JMVXInputStream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Optional;

public class InputStreamClassVisitor extends RenameClassVisitor {
    /*default*/ static final String[] paks = new String[] {"java", "com.sun", "sun"};

    /*default*/ static boolean isInterestingPackage(Type t) {
        // Only instrument input streams in certain packages
        // All other interesting input streams have to call these
        return Arrays.stream(paks).anyMatch(p -> t.getClassName().startsWith(p));
    }

    public InputStreamClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType(InputStream.class), JMVXInputStream.class);
    }

    protected boolean isType(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        if (!isInterestingPackage(t))
            return false;

        /*
        Not using Class.forName("java.net.SocketInputStream").isAssignableFrom(c) because it caused unexpected behavior
        Checking by the class name has been the most consistent method so far. Come back to this after threads work.
         */
        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return InputStream.class.isAssignableFrom(c) && !FilterInputStream.class.isAssignableFrom(c) && !ByteArrayInputStream.class.isAssignableFrom(c) && !SequenceInputStream.class.isAssignableFrom(c)
                    && !c.getName().equals("java.net.SocketInputStream") && !InputStreamAdapter.class.isAssignableFrom(c);
                    //!Class.forName("java.net.SocketInputStream").isAssignableFrom(c);
        } catch (ClassNotFoundException | LinkageError e){//NoClassDefFoundError | IllegalAccessError e) {
            logger.trace("I don't know if this is an InputStream or not: " + t.getClassName() + " because " + e.getMessage());
            return false;
        }
    }
}
