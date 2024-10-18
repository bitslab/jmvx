package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Stream;

public class UnixExceptionClassVisitor extends ClassVisitor implements Opcodes {

    private final String UNIX_EXCEPTION_INTERNAL_NAME = "sun/nio/fs/UnixException";
    private static final Type SERIALIZABLE = Type.getType(Serializable.class);

    public UnixExceptionClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if(name.equals(UNIX_EXCEPTION_INTERNAL_NAME)) {
            access |= ACC_PUBLIC;
            interfaces = BytecodeUtils.addInterface(interfaces, SERIALIZABLE);
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }
}
