package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXJarFile;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class JarFileClassVisitor extends ClassVisitor implements Opcodes {

    private boolean enabled = false;
    private static final Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);
    private static final Type JMVX_TYPE = Type.getType(JMVXJarFile.class);
    private static final Type JARFILE_TYPE = Type.getType(JarFile.class);
    private static Method getMetaInfMethod;
    private static Type getMetaInfType;
    private static String newDescriptor;

    static {
        try {
            getMetaInfMethod = JarFile.class.getDeclaredMethod("getMetaInfEntryNames");
            getMetaInfType = Type.getType(getMetaInfMethod);
            newDescriptor = BytecodeUtils.addArgumentToMethod(JMVX_TYPE, getMetaInfType.getDescriptor());
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }

    public JarFileClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        enabled = name.equals(JARFILE_TYPE.getInternalName());
        if (enabled) {
            // Add JMVX interface to found type
            if (interfaces == null) interfaces = new String[0];
            interfaces = Stream.concat(Arrays.stream(interfaces), Stream.of(JMVX_TYPE.getInternalName())).toArray(String[]::new);
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if(!enabled || ((access & ACC_NATIVE) != 0)){
            return mv;
        }
        return new MethodVisitor(ASM7, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if(owner.equals(JARFILE_TYPE.getInternalName()) && name.equals(getMetaInfMethod.getName()) &&
                        descriptor.equals(getMetaInfType.getDescriptor())){
                    //divert call
                    opcode = INVOKESTATIC;
                    descriptor = newDescriptor;
                    owner = JMVX_RUNTIME.getInternalName();
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }

    protected void generateNewGetMetaInfMethod(String name, String descriptor) {
        //String jmvxRuntimeMethodDescriptor = addArgumentToMethod(JMVX_TYPE, descriptor);

        MethodVisitor mv;
        //ACC_PUBLIC | ACC_SYNCHRONIZED
        mv = super.visitMethod(ACC_PUBLIC, JMVXRuntime.Prefix + name, descriptor, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        BytecodeUtils.loadAllArgumentsToTheStack(descriptor, mv, false);
        mv.visitMethodInsn(INVOKESPECIAL, JARFILE_TYPE.getInternalName(), name, descriptor, false);
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));
        mv.visitMaxs(1 + getMetaInfMethod.getParameterCount(), 1);
        mv.visitEnd();
    }

    @Override
    public void visitEnd() {
        //add new method for the one we diverted
        if(enabled) {
            generateNewGetMetaInfMethod(getMetaInfMethod.getName(), getMetaInfType.getDescriptor());
        }
        super.visitEnd();
    }
}