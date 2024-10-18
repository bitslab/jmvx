package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import edu.uic.cs.jmvx.runtime.JMVXZipFile;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
    This class visitor a few things:
    1. Makes 2 new copies of every "private static native" method.
        private static staticMethodName -> see bullet #3
        public static $JMVX$MethodName -> methods for JMVXZipFile(#2), allows us to call methods outside of ZipFile
    2. Makes ZipFile implement JMVXZipFile
    3. Converts calls to private static native methods to call private static methods we added (see psuedo code below)


    In code, this looks like:

    private static native getEntry(...); //stays as is
    We add:
    private static staticgetEntry(...) {
        if(JMVXRuntime.initialized)
            return JMVXRuntime.getEntry(...);
        else
            return getEntry(...); //private static native
    }

    //handle to call the private static native from somewhere else, e.g., a strategy
    //NOT DIRECTLY CALLED IN ZipFile -- JMVXRuntime.zipfile exists so we can call this method elsewhere
    public static $JMVX$getEntry(...){
        return getEntry(...); //private static native
    }

    We change call sites of private static native methods too
    int jzentry = getEntry(...); //private static native
        ==>
    int jzentry = staticgetEntry(...); //our private static, which can redirect to JMVX
*/
public class ZipFileClassVisitor extends ClassVisitor implements Opcodes {

    private boolean enabled = false;
    private static final Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);
    private static final Type JMVX_TYPE = Type.getType(JMVXZipFile.class);
    private static final Type ZIPFILE_TYPE = Type.getType(ZipFile.class);
    private static final HashMap<String, Method> redirect = new HashMap<>();

    static {
        for(Method m: JMVXZipFile.class.getMethods()){
            redirect.put(m.getName().replace(JMVXRuntime.Prefix, ""), m);
        }
    }

    public ZipFileClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        enabled = Type.getObjectType(name).equals(ZIPFILE_TYPE);

        if (enabled) {
            // Add JMVX interface to found type
            if (interfaces == null) interfaces = new String[0];
            interfaces = Stream.concat(Arrays.stream(interfaces), Stream.of(JMVX_TYPE.getInternalName())).toArray(String[]::new);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        //replace calls to private native methods
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if(enabled && (ACC_NATIVE & access) == 0){ //enabled and not in a native call
            return new MethodVisitor(ASM7, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if(opcode == INVOKESTATIC && owner.equals(ZIPFILE_TYPE.getInternalName())){
                        Method methodToCall = redirect.get(name);
                        if(methodToCall != null && Type.getMethodDescriptor(methodToCall).equals(descriptor)){
                            super.visitMethodInsn(INVOKESTATIC, ZIPFILE_TYPE.getInternalName(), "static" + methodToCall.getName(), descriptor, false);
                            return;
                        }
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }
        return mv;
    }

    @Override
    public void visitEnd() {
        //add new methods
        if(enabled) {
            for (Method m : JMVXZipFile.class.getMethods()) {
                //the backdoor to call private static native methods
                generateMethod(m.getName(), Type.getMethodDescriptor(m));
                //the method that mimics the private native method, but has conditional logic
                generateStaticMethod(m.getName(), Type.getMethodDescriptor(m));
            }
        }
        super.visitEnd();
    }

    protected void generateMethod(String name, String descriptor) {
        //slightly modified from other generateMethod implementations
        //String jmvxRuntimeMethodDescriptor = addArgumentToMethod(TYPE, descriptor);

        MethodVisitor mv;
        mv = super.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
        int nargs = Type.getArgumentTypes(descriptor).length;
        String methodName = name.replace(JMVXRuntime.Prefix, "");
        mv.visitCode();
        BytecodeUtils.loadAllArgumentsToTheStack(descriptor, mv, false);
        mv.visitMethodInsn(INVOKESTATIC, ZIPFILE_TYPE.getInternalName(), methodName, descriptor, false);
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));
        mv.visitMaxs(nargs, 0);
        mv.visitEnd();
    }

    protected void generateStaticMethod(String name, String descriptor) {
        //slightly modified from other generateMethod implementations
        //String jmvxRuntimeMethodDescriptor = addArgumentToMethod(TYPE, descriptor);

        MethodVisitor mv;
        mv = super.visitMethod(ACC_PRIVATE + ACC_STATIC, "static" + name, descriptor, null, null);
        int nargs = Type.getArgumentTypes(descriptor).length;
        String methodName = name.replace(JMVXRuntime.Prefix, "");
        mv.visitCode();
        Label start = new Label();
        Label thenBranch = new Label();
        Label error = new Label();
        //super.visitLdcInsn(JMVXRuntime.class);
        mv.visitTryCatchBlock(start, error, error, null);
        mv.visitLabel(start);
        BytecodeUtils.loadAllArgumentsToTheStack(descriptor, mv, true);
        mv.visitFieldInsn(GETSTATIC, JMVX_RUNTIME.getInternalName(), "initialized", Type.BOOLEAN_TYPE.getDescriptor());
        mv.visitJumpInsn(IFEQ, thenBranch);
        //JMVX is initialized, divert call
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), methodName, descriptor, false);
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));
        //JMVX is not initialized, let call go through
        mv.visitLabel(thenBranch);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        mv.visitMethodInsn(INVOKESTATIC, ZIPFILE_TYPE.getInternalName(), methodName, descriptor, false);
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));

        //error handler (for open only really)
        mv.visitLabel(error);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
        mv.visitInsn(ATHROW);

        mv.visitMaxs(nargs, 0);
        mv.visitEnd();
    }
}