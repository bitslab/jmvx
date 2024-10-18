package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;

/**
 * This class visitor finds classes that need to be compiled with a newer bytecode version
 * The only criterion (so far) is the presence of a static synchronized method.
 * Some bytecode versions do not allow ldc to work on a class constant, which would break our instrumentation
 * of static sync'ed methods. Empirically, we determined that upgrading to version 1.5 (Java 5) works.
 *
 * IMPORTANT: this class only populates a set. SynchronizedClassVisitor performs the version upgrade.
 * This is because this class visitor must specify a version before examining if the version must be bumped.
 * Thus the bumping must come in a second pass (placed in SynchronizedClassVisitor).
 *
 * Note: classes should not be downgraded.
 */
public class ByteCodeVersionBumpClassVisitor extends ClassVisitor implements Opcodes {

    public static HashSet<String> classes = new HashSet<>();

    private boolean enabled;
    private String internalName;
    public static final int UPGRADE_VERSION = V1_5;

    public ByteCodeVersionBumpClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        internalName = name;
        enabled = version < UPGRADE_VERSION || (version == V1_1 && UPGRADE_VERSION != V1_1); //V1_1 is a higher number for some reason
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if(enabled && (access & (ACC_STATIC + ACC_SYNCHRONIZED)) != 0){ //static sync'ed
            classes.add(internalName);
        }
        //don't do any modification now...
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
