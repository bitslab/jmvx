package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;

public class MonitorClassVisitor extends ClassVisitor implements Opcodes {
    private static final Logger logger = Logger.getLogger(MonitorClassVisitor.class);
    private static final Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);
    private static final Method JMVX_MONITORENTER;
    private static final String JMVX_MONITORENTER_DESC;
    private static final Method JMVX_MONITOREXIT;
    private static final String JMVX_MONITOREXIT_DESC;

    private static final String WAIT_DESC;
    private static final String WAIT_TIMEOUT_DESC;
    private static final String WAIT_TIMEOUT_NANOS_DESC;
    private static final String JMVX_WAIT_DESC;
    private static final String JMVX_WAIT_TIMEOUT_DESC;
    private static final String JMVX_WAIT_TIMEOUT_NANOS_DESC;

    private static final Type OBJECT_TYPE = Type.getType(Object.class);

    private ClassLoader loader;

    public static HashSet<String> JVM_WHITE_LIST = new HashSet<>();
    private static final String FILE_PROP_NAME = "jmvx.file";
    private static final Boolean enabled = !System.getProperty(FILE_PROP_NAME).equals("rt.jar");

    private String className;

    static {
        try {
            JMVX_MONITORENTER = JMVXRuntime.class.getDeclaredMethod("monitorenter", Object.class);
            JMVX_MONITORENTER_DESC = Type.getMethodDescriptor(JMVX_MONITORENTER);
            JMVX_MONITOREXIT = JMVXRuntime.class.getDeclaredMethod("monitorexit", Object.class);
            JMVX_MONITOREXIT_DESC = Type.getMethodDescriptor(JMVX_MONITOREXIT);

            WAIT_DESC = Type.getMethodDescriptor(Object.class.getMethod("wait"));
            WAIT_TIMEOUT_DESC = Type.getMethodDescriptor(Object.class.getMethod("wait", long.class));
            WAIT_TIMEOUT_NANOS_DESC = Type.getMethodDescriptor(Object.class.getMethod("wait", long.class, int.class));

            JMVX_WAIT_DESC = Type.getMethodDescriptor(JMVXRuntime.class.getMethod("wait", Object.class));
            JMVX_WAIT_TIMEOUT_DESC = Type.getMethodDescriptor(JMVXRuntime.class.getMethod("wait", Object.class, long.class));
            JMVX_WAIT_TIMEOUT_NANOS_DESC = Type.getMethodDescriptor(JMVXRuntime.class.getMethod("wait", Object.class, long.class, int.class));

            //JVM_WHITE_LIST.add(Type.getInternalName(Class.forName("java.lang.UNIXProcess", false, MonitorClassVisitor.class.getClassLoader())));
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    public MonitorClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = loader.orElse(MonitorClassVisitor.class.getClassLoader());
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (!enabled && !JVM_WHITE_LIST.contains(className))
            return super.visitMethod(access, name, descriptor, signature, exceptions);

//        if ((access & ACC_SYNCHRONIZED) != 0) {
//            return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
//                @Override
//                public void visitCode() {
//                    super.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
//                    super.visitLdcInsn("MonitorClassVisitor: " + className + "." + name + "()");
//                    super.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
//                    super.visitCode();
//                }
//            };
//        }

        return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (name.equals("wait") && owner.equals(OBJECT_TYPE.getInternalName())) {
                    owner = JMVX_RUNTIME.getInternalName();
                    opcode = INVOKESTATIC;

                    if (WAIT_DESC.equals(descriptor))
                        descriptor = JMVX_WAIT_DESC;
                    else if (WAIT_TIMEOUT_DESC.equals(descriptor))
                        descriptor = JMVX_WAIT_TIMEOUT_DESC;
                    else if (WAIT_TIMEOUT_NANOS_DESC.equals(descriptor))
                        descriptor = JMVX_WAIT_TIMEOUT_NANOS_DESC;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.MONITORENTER) {
                    super.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), "monitorenter", JMVX_MONITORENTER_DESC, false);
                    return;
                }
                if (opcode == Opcodes.MONITOREXIT) {
                    super.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), "monitorexit", JMVX_MONITOREXIT_DESC, false);
                    return;
                }
                super.visitInsn(opcode);
            }
        };
    }


}
