package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.apache.log4j.Logger;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ThreadRunClassVisitor extends ClassVisitor implements Opcodes {

    private static final Logger log = Logger.getLogger(ThreadRunClassVisitor.class);
    private boolean enabled = false;
    private ClassLoader loader;
    private Class<?> currentClass;
    private static final String JMVX_RUNTIME_INTERNAL_NAME = Type.getInternalName(JMVXRuntime.class);

    private static final Set<Type> DENY_LIST = new HashSet<>();/*Arrays.asList(
            Type.getObjectType("java/lang/ref/Reference$ReferenceHandler"),
            Type.getObjectType("java/lang/ref/Finalizer$FinalizerThread"),
            Type.getObjectType("java/lang/ApplicationShutdownHooks$1")
    ));*/

    public ThreadRunClassVisitor(ClassLoader loader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = loader;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);

        try {
            Class<?> c = Class.forName(Type.getObjectType(name).getClassName(), false, loader);
            if (Runnable.class.isAssignableFrom(c) && !DENY_LIST.contains(Type.getObjectType(name))) {
                enabled = true;
                currentClass = c;
                log.info("Found thread " + c.getName());
            }
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            // TODO log error
            log.trace("Not sure if this is a thread: " + name);
        }
    }

    private void addRun() throws NoSuchMethodException {
        Method JMVX_RUNNABLE_RUN = JMVXRuntime.class.getDeclaredMethod("run", Runnable.class);
        String JMVX_RUNNABLE_RUN_DESC = Type.getMethodDescriptor(JMVX_RUNNABLE_RUN);
        String internalName = Type.getInternalName(currentClass);
        MethodVisitor mv = super.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitTryCatchBlock(l0, l1, l2, null);
        Label l3 = new Label();
        mv.visitLabel(l3);
        //mv.visitLineNumber(10, l3);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, "run", JMVX_RUNNABLE_RUN_DESC, false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, "startThread", "(Ljava/lang/Runnable;)V", false);
        mv.visitLabel(l0);
        //mv.visitLineNumber(12, l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, internalName, "$JMVX$run", "()V", false);
        mv.visitLabel(l1);
        //mv.visitLineNumber(14, l1);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, "exitThread", "(Ljava/lang/Runnable;)V", false);
        Label l4 = new Label();
        mv.visitLabel(l4);
        //mv.visitLineNumber(15, l4);
        Label l5 = new Label();
        mv.visitJumpInsn(GOTO, l5);
        mv.visitLabel(l2);
        //mv.visitLineNumber(14, l2);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
        mv.visitVarInsn(ASTORE, 1);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, "exitThread", "(Ljava/lang/Runnable;)V", false);
        Label l6 = new Label();
        mv.visitLabel(l6);
        //mv.visitLineNumber(15, l6);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ATHROW);
        mv.visitLabel(l5);
        //mv.visitLineNumber(16, l5);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(RETURN);
        Label l7 = new Label();
        mv.visitLabel(l7);
        mv.visitLocalVariable("this", "L" + internalName + ";", null, l3, l7, 0);
        mv.visitMaxs(1, 2);
        mv.visitEnd();
    }

    @Override
    public void visitEnd() {
        if(enabled){
            try {
                addRun();
            }catch (Exception e){
                throw new Error("Failed to add run");
            }
        }
        super.visitEnd();
    }
}
