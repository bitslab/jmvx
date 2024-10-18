package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.Main;
import edu.uic.cs.jmvx.runtime.JMVXInputStream;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class InputStreamGenerator implements Opcodes {
    private static final String PREFIX = Main.class.getPackage().getName().replace('.', '/') + "/generated/";
    private static final String SUFFIX = JMVXRuntime.Prefix + "InputStream";
    private static final Type JMVX_IFACE    = Type.getType(JMVXInputStream.class);
    private static final Type JMVX_RUNTIME  = Type.getType(JMVXRuntime.class);
    private static final Method JMVX_RUNTIME_INIT;

    static {
        try {
            JMVX_RUNTIME_INIT = JMVXRuntime.class.getDeclaredMethod("init", InputStream.class);
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }

    private String generatedClassName;

    public byte[] generateInputStreamClass(Type t) throws ClassNotFoundException {
        Class<?> c = Class.forName(t.getClassName());
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        generatedClassName = PREFIX + t.getInternalName() + SUFFIX;

        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, generatedClassName, null, t.getInternalName(), new String[]{ JMVX_IFACE.getInternalName() });

        generateConstructors(c, t, cw);
        overrideReadMethods(cw);
        generateInterfaceMethods(t, cw);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateConstructors(Class<?> c, Type t, ClassVisitor cv) {
        Constructor[] constructors = c.getDeclaredConstructors();
        for(Constructor cons : constructors) {
            Type consType = Type.getType(cons);
            Type[] consArgs = consType.getArgumentTypes();
            MethodVisitor methodVisitor = cv.visitMethod(ACC_PUBLIC, "<init>", consType.getDescriptor(), null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            int i = 1;
            for (Type consArg : consArgs) {
                if (consArg.getSort() == Type.ARRAY) {
                    methodVisitor.visitVarInsn(ALOAD, i);
                } else {
                    methodVisitor.visitVarInsn(consArg.getOpcode(ILOAD), i);
                }
                i += consArg.getSize();
            }
            methodVisitor.visitMethodInsn(INVOKESPECIAL, t.getInternalName(), "<init>", consType.getDescriptor(), false);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), JMVX_RUNTIME_INIT.getName(), Type.getMethodDescriptor(JMVX_RUNTIME_INIT), false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(i, i);
            methodVisitor.visitEnd();
        }
    }

    private void overrideReadMethods(ClassVisitor cv) {
        for (Method m : JMVXInputStream.class.getDeclaredMethods())
            overrideMethod(m, cv);
    }

    private void overrideMethod(Method method, ClassVisitor cv) {
        if (Modifier.isFinal(method.getModifiers()))
            return;

        String methodName = method.getName().replace(JMVXRuntime.Prefix, "");

        if (method.getName().equals(methodName)) {
            // Does not start with JMVXInputStream.PREFIX, skip
            return;
        }


        MethodVisitor mv;
        mv = cv.visitMethod(ACC_PUBLIC | ACC_SYNCHRONIZED, methodName, Type.getMethodDescriptor(method), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        loadAllArgumentsToTheStack(method.getParameterTypes(), mv);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), method.getName(), Type.getMethodDescriptor(method), false);
        mv.visitInsn(Type.getType(method.getReturnType()).getOpcode(IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    public File generateEmptyFile(File parentDir) {
        return new File(parentDir, this.generatedClassName.replace("/", File.separator) + ".class");
    }

    private void generateInterfaceMethods(Type t, ClassVisitor cv) {
        for (Method m : JMVXInputStream.class.getDeclaredMethods())
            generateInterfaceMethod(t, m, cv);
    }

    private void generateInterfaceMethod(Type t, Method method, ClassVisitor cv) {
        MethodVisitor mv;
        String originalName = method.getName().replace(JMVXRuntime.Prefix, "");
        mv = cv.visitMethod(ACC_PUBLIC | ACC_SYNCHRONIZED, method.getName(), Type.getMethodDescriptor(method), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        loadAllArgumentsToTheStack(method.getParameterTypes(), mv);
        mv.visitMethodInsn(INVOKESPECIAL, t.getInternalName(), originalName, Type.getMethodDescriptor(method), false);
        mv.visitInsn(Type.getType(method.getReturnType()).getOpcode(IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void loadAllArgumentsToTheStack(Class<?>[] argTypes, MethodVisitor mv) {
        for (int i = 0 ; i < argTypes.length ; i++) {
            Type argType = Type.getType(argTypes[i]);
            if (argType.getSort() == Type.ARRAY) {
                mv.visitVarInsn(ALOAD, i + 1);
            } else {
                mv.visitVarInsn(argType.getOpcode(ILOAD), i + 1);
            }
        }
    }
}
