package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXSecureClassLoader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.security.SecureClassLoader;
import java.util.Optional;

public class SecureClassLoaderClassVisitor extends RenameClassVisitor implements Opcodes {

    private static final Type SECURE_CLASS_LOADER = Type.getType(SecureClassLoader.class);

    public SecureClassLoaderClassVisitor(Optional<ClassLoader> classLoader, ClassVisitor classVisitor) {
        super(classLoader, classVisitor, SECURE_CLASS_LOADER, JMVXSecureClassLoader.class);
    }


    @Override
    protected boolean isType(Type t) {
        return SECURE_CLASS_LOADER.equals(t);
    }
}
