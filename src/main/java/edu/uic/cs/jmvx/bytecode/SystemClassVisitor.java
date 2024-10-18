package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;

public class SystemClassVisitor extends ClassVisitor implements Opcodes {

    private static final HashSet<String> JVM_WHITE_LIST = new HashSet();
    private static final HashSet<String> DENY_LIST = new HashSet();

    private static final Type SYS_TYPE = Type.getType(System.class);

    private static final Method SYS_EXIT;
    private static final String SYS_EXIT_DESC;
    private static final Method JMVX_SYSTEM_EXIT;
    private static final String JMVX_SYSTEM_EXIT_DESC;

    private static final Method SYS_TIMEMILLIS;
    private static final String SYS_TIMEMILLIS_DESC;

    private static final Method SYS_NANOTIME;
    private static final String SYS_NANOTIME_DESC;

    private static final Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);
    private static final Method JMVX_TIMEMILLIS;
    private static final String JMVX_TIMEMILLIS_DESC;

    private static final String FILE_PROP_NAME = "jmvx.file";
    private static final Boolean instrumentingJVM = System.getProperty(FILE_PROP_NAME).equals("rt.jar");

    private String className;

    static {
        try {
            SYS_EXIT = System.class.getMethod("exit", int.class);
            SYS_EXIT_DESC = Type.getMethodDescriptor(SYS_EXIT);

            JMVX_SYSTEM_EXIT = JMVXRuntime.class.getMethod("systemExit", int.class);
            JMVX_SYSTEM_EXIT_DESC = Type.getMethodDescriptor(JMVX_SYSTEM_EXIT);

            SYS_TIMEMILLIS = System.class.getMethod("currentTimeMillis");
            SYS_TIMEMILLIS_DESC = Type.getMethodDescriptor(SYS_TIMEMILLIS);

            SYS_NANOTIME = System.class.getDeclaredMethod("nanoTime");
            SYS_NANOTIME_DESC = Type.getMethodDescriptor(SYS_NANOTIME);

            JMVX_TIMEMILLIS = JMVXRuntime.class.getMethod("currentTimeMillis");
            JMVX_TIMEMILLIS_DESC = Type.getMethodDescriptor(JMVX_TIMEMILLIS);

            /**avoid org/dacapo/harness/Benchmark so we get accurate timing data
             org/sunflow/system/Timer - Methods (generate System.nanoTime events) called inside a block "guarded"
                by a double check lock, so the thread that executes the call is unpredictable.

             JVM classes:
             Date - uses System.currentTimeMillis to generate the date. fop uses this.
             */
            DENY_LIST.add("org/dacapo/harness/Benchmark");
            //DENY_LIST.add("org/sunflow/system/Timer");
            JVM_WHITE_LIST.add(Type.getInternalName(Date.class));
            JVM_WHITE_LIST.add(Type.getInternalName(System.class));
            JVM_WHITE_LIST.add(Type.getInternalName(Random.class));
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    private ClassLoader loader;
    private boolean enabled = true;

    public SystemClassVisitor(ClassLoader classLoader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = loader;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        enabled = (!instrumentingJVM || JVM_WHITE_LIST.contains(name)) && !DENY_LIST.contains(name);
        className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (!enabled)
            return super.visitMethod(access, name, descriptor, signature, exceptions);

        return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if ((name.equals("currentTimeMillis") && owner.equals(SYS_TYPE.getInternalName()) && SYS_TIMEMILLIS_DESC.equals(descriptor))
                        || (name.equals(SYS_NANOTIME.getName()) && owner.equals(SYS_TYPE.getInternalName()) && SYS_NANOTIME_DESC.equals(descriptor))) {
                    owner = JMVX_RUNTIME.getInternalName();
                    opcode = INVOKESTATIC;
                    descriptor = JMVX_TIMEMILLIS_DESC;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitCode() {
                if(className.equals(Type.getInternalName(System.class)) && name.equals("exit") && descriptor.equals(SYS_EXIT_DESC)) {
                    loadAllArgumentsToTheStack(JMVX_SYSTEM_EXIT_DESC, this);
                    visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), JMVX_SYSTEM_EXIT.getName(), JMVX_SYSTEM_EXIT_DESC, false);
                    super.visitCode();
                }
            }
        };
    }

    private void loadAllArgumentsToTheStack(String desc, MethodVisitor mv) {
        Type[] argTypes = Type.getArgumentTypes(desc);
        for (int i = 0 ; i < argTypes.length ; i++) {
            Type argType = argTypes[i];
            if (argType.getSort() == Type.ARRAY) {
                mv.visitVarInsn(ALOAD, i);
            } else {
                mv.visitVarInsn(argType.getOpcode(ILOAD), i);
            }
        }
    }
}
