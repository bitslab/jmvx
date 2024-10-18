package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXFileChannel;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.io.File;
import java.nio.channels.FileChannel;
import java.util.Optional;

/**
 * FileChannel is abstract and does not have any native methods. Child FileChannelImpl eventually calls native
 * methods in FileDispatcherImpl. If we ever need to get closer to native calls, look there.
 */
public class FileChannelClassVisitor extends RenameClassVisitor{

    public FileChannelClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType(FileChannel.class), JMVXFileChannel.class);
    }

    @Override
    protected boolean isType(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return FileChannel.class.isAssignableFrom(c);// && !FileChannel.class.equals(c);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            logger.trace("I don't know if this is a FileChannel or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }
    }
}
