package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.tree.MethodNode;
import org.apache.log4j.Logger;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ThreadClassVisitor extends ClassVisitor implements Opcodes {
    private static final Logger log = Logger.getLogger(ThreadClassVisitor.class);
    private static final Method RUNNABLE_RUN;
    private static final String RUNNABLE_RUN_DESC;

    private static final Type   JMVX_RUNTIME = Type.getType(JMVXRuntime.class);
    private static final Method JMVX_RUNNABLE_RUN;
    private static final String JMVX_RUNNABLE_RUN_DESC;

    private String internalName;

    private static final Set<Type> DENY_LIST = new HashSet<>();/*Arrays.asList(
            Type.getObjectType("java/lang/ref/Reference$ReferenceHandler"),
            Type.getObjectType("java/lang/ref/Finalizer$FinalizerThread"),
            Type.getObjectType("java/lang/ApplicationShutdownHooks$1")
    ));*/

    private ClassLoader loader;

    static {
        try {
            RUNNABLE_RUN = Runnable.class.getDeclaredMethod("run");
            RUNNABLE_RUN_DESC = Type.getMethodDescriptor(RUNNABLE_RUN);

            JMVX_RUNNABLE_RUN = JMVXRuntime.class.getDeclaredMethod("run", Runnable.class);
            JMVX_RUNNABLE_RUN_DESC = Type.getMethodDescriptor(JMVX_RUNNABLE_RUN);

        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    public ThreadClassVisitor(ClassLoader loader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = loader;
    }

    private boolean enabled = false;

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        internalName = name;
        try {
            Class<?> c = Class.forName(Type.getObjectType(name).getClassName(), false, loader);
            if (Runnable.class.isAssignableFrom(c) && !DENY_LIST.contains(Type.getObjectType(name))) {
                enabled = true;
                log.info("Found thread " + c.getName());
            }
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            // TODO log error
            log.trace("Not sure if this is a thread: " + name);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv;

        if (enabled && "run".equals(name) && RUNNABLE_RUN_DESC.equals(descriptor)) {
            mv = super.visitMethod(access, JMVXRuntime.Prefix + name, descriptor, signature, exceptions);
            //new method visitor that incorporates the name change
            mv = new MethodVisitor(ASM7, mv) {
                /*@Override
                public void visitCode() {
                    super.visitCode();
                    super.visitVarInsn(ALOAD, 0);
                    super.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), "run", JMVX_RUNNABLE_RUN_DESC, false);
                }*/

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if(name.equals("run") && owner.equals(internalName) && opcode == INVOKEVIRTUAL) {
                        name = JMVXRuntime.Prefix + name;
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }else{
            //name unchanged
            mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        return mv;
    }
}
