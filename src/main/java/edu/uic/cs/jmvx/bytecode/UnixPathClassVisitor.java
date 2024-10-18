package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;

public class UnixPathClassVisitor extends ClassVisitor implements Opcodes {

    private final String UNIX_PATH_INTERNAL_NAME = "sun/nio/fs/UnixPath";
    private static final String[] methods = {"toUnixPath"};

    boolean inClass = false;

    public UnixPathClassVisitor(ClassVisitor classVisitor) throws ClassNotFoundException {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        inClass = UNIX_PATH_INTERNAL_NAME.equals(name);
        if(inClass)
            access |= ACC_PUBLIC;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        //should maybe check the descriptors too...
        if (inClass && instrumentMethodCall(name))
                access |= ACC_PUBLIC;
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    static boolean instrumentMethodCall(String name){
        return Arrays.stream(methods).anyMatch((mthd) -> mthd.equals(name));
    }
}
