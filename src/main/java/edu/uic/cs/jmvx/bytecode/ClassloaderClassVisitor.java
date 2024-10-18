package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXClassLoader;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.*;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassloaderClassVisitor extends ClassVisitor implements Opcodes {
    private static final Logger log = Logger.getLogger(ClassloaderClassVisitor.class);
    private static final Set<String> DENY_PACKAGES = Stream.of("sun.misc").collect(Collectors.toSet());

    private static String LOADCLASS_NAME    = "loadClass";
    private static String LOADCLASS_RENAMED = "$JMVX$loadClass";

    private static final Type JMVX_RUNTIME     = Type.getType(JMVXRuntime.class);
    private static final Type JMVX_CLASSLOADER = Type.getType(JMVXClassLoader.class);

    private static final String LOADCLASS_STRING_DESC;
    private static final String LOADCLASS_STRING_BOOLEAN_DESC;

    private static final String JMVX_LOADCLASS_STRING_DESC;
    private static final String JMVX_LOADCLASS_STRING_BOOLEAN_DESC;

    static {
        try {

            JMVX_LOADCLASS_STRING_DESC         = Type.getMethodDescriptor(JMVXRuntime.class.getDeclaredMethod(LOADCLASS_NAME, JMVXClassLoader.class, String.class));
            JMVX_LOADCLASS_STRING_BOOLEAN_DESC = Type.getMethodDescriptor(JMVXRuntime.class.getDeclaredMethod(LOADCLASS_NAME, JMVXClassLoader.class, String.class, boolean.class));

            LOADCLASS_STRING_DESC         = Type.getMethodDescriptor(ClassLoader.class.getDeclaredMethod(LOADCLASS_NAME, String.class));
            LOADCLASS_STRING_BOOLEAN_DESC = Type.getMethodDescriptor(ClassLoader.class.getDeclaredMethod(LOADCLASS_NAME, String.class, boolean.class));

        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }

    }

    private boolean enabled = false;
    private final ClassLoader loader;

    public ClassloaderClassVisitor(Optional<ClassLoader> cl, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = cl.orElse(ClassloaderClassVisitor.class.getClassLoader());
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {

        Type t = Type.getObjectType(name);

        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            if (ClassLoader.class.isAssignableFrom(c)) {
                boolean isDenied = DENY_PACKAGES.stream().anyMatch(t.getClassName()::startsWith);
                if (isDenied) {
                    log.info("Not processing due to deny list: " + t.getClassName());
                } else {
                    this.enabled = true;
                    log.info("Found classloader " + c.getName());
                }
            }
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            this.enabled = false;
            log.trace("Not sure if this is a classloader: " + t.getClassName());
        }

        if (this.enabled) {
            interfaces = Stream.concat(Arrays.stream(interfaces), Stream.of(JMVX_CLASSLOADER.getInternalName())).toArray(String[]::new);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (!enabled || ((access & ACC_NATIVE) != 0)) {
            return mv;
        }

        if (LOADCLASS_NAME.equals(name)) {
            if (LOADCLASS_STRING_DESC.equals(descriptor)) {
                generateLoadClassString(mv);
            } else if (LOADCLASS_STRING_BOOLEAN_DESC.equals(descriptor)) {
                generateLoadClassStringBoolean(mv);
            } else {
                return mv;
            }

            int public_access = ((access | ACC_PUBLIC) & (~ACC_PROTECTED));
            mv = super.visitMethod(public_access, LOADCLASS_RENAMED, descriptor, signature, exceptions);
        }

        return new MethodVisitor(ASM7, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == INVOKESPECIAL && LOADCLASS_NAME.equals(name)) {
                    name = LOADCLASS_RENAMED;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }

    private void generateLoadClassString(MethodVisitor mv) {
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), LOADCLASS_NAME, JMVX_LOADCLASS_STRING_DESC, false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateLoadClassStringBoolean(MethodVisitor mv) {
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), LOADCLASS_NAME, JMVX_LOADCLASS_STRING_BOOLEAN_DESC, false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

}
