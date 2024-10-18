package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXFileSystemProvider;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.nio.file.spi.FileSystemProvider;
import java.util.Optional;

public class FileSystemProviderClassVisitor extends RenameClassVisitor {

    public FileSystemProviderClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType(FileSystemProvider.class), JMVXFileSystemProvider.class);
    }

    protected boolean isType(Type t) {
        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return FileSystemProvider.class.isAssignableFrom(c);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            return false;
        }
    }
}
