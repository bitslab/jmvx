package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.Serializable;

public class UnixFileAttributesClassVisitor extends ClassVisitor implements Opcodes {

    private static final Type UNIX_FILE_ATTR;
    private static final Type SERIALIZABLE = Type.getType(Serializable.class);
    private boolean enabled = false;

    static {
        try {
            ClassLoader cl = UnixFileAttributesClassVisitor.class.getClassLoader();
            Class<?> fileAttr = Class.forName("sun.nio.fs.UnixFileAttributes", false, cl);
            UNIX_FILE_ATTR = Type.getType(fileAttr);
        }catch (ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    public UnixFileAttributesClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        enabled = UNIX_FILE_ATTR.getInternalName().equals(name);
        if(enabled) {
            access |= ACC_PUBLIC;
            interfaces = BytecodeUtils.addInterface(interfaces, SERIALIZABLE);
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        //we want access to all the fields
        if(enabled) {
            access &= ~(ACC_PRIVATE | ACC_PROTECTED); //remove private or protected
            access |= ACC_PUBLIC; //make public
        }
        return super.visitField(access, name, descriptor, signature, value);
    }
}
