package edu.uic.cs.jmvx.bytecode;


import edu.uic.cs.jmvx.runtime.JMVXNativeDetector;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;

public class NativeMethodIntercepter extends ClassVisitor implements Opcodes {
    private static final String JMVX_RUNTIME_TYPE = Type.getType(JMVXNativeDetector.class).getInternalName();
    private static final String VOID_VOID_DESC = Type.getMethodDescriptor(Type.VOID_TYPE);
    private static final String JMVX_NATIVE_PREFIX = "$JMVX$native$";
    private MethodNode[] nativeMethods;
    private String className;

    /*
    This class visitor finds callsites of private or package private native methods
    redirects the call to a wrapper method, $JMVX$native$methodName.

    The wrapper method is added to the class and looks like the following:

    int $JMVX$native$methodName(args){
        try{
            JMVXNatives.beforeNativeCall();
            int methodName(args); //actual native call
        }finally{
            JMVXNatives.afterNativeCall();
        }
     }

     This is used to trace system calls. The general idea:
     beforeNative call grabs a lock, clears a flag, and the stack trace of the program
     if the native method called results in a system call, we have strace send the jvm SIGUSR2
     A signal handler, in JMVXNatives, will set the flag
     afterNative call will log the saved stack trace if the flag was set (and is unique by checking a hash code)
     */
    public NativeMethodIntercepter(ClassNode cn, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        nativeMethods = cn.methods.stream()
                .filter(mn -> (mn.access & ACC_NATIVE) != 0)
//              .filter(mn -> (mn.access & ACC_PUBLIC) == 0) //not public //this seg-faulted. Not sure why
                .filter(mn -> (mn.access & ACC_PRIVATE) != 0 || //private or 
                              (mn.access & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED)) == 0) //package private
                .toArray(MethodNode[]::new);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (className.startsWith("java/lang") ||
                    className.startsWith("java/security") ||
                    className.startsWith("java/util/concurrent") ||
                    className.startsWith("sun/reflect") ||
                    className.startsWith("sun/misc/VM") ||
                    className.startsWith("sun/misc/Unsafe") ||
                    false
                ) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    return;
                }

                if (owner.equals(className)) {
                    boolean isNativeCall = Arrays.stream(nativeMethods)
                            .filter(nm -> nm.name.equals(name))
                            .filter(nm -> nm.desc.equals(descriptor))
                            .findFirst()
                            .isPresent();

                    if (isNativeCall) {
                        super.visitMethodInsn(opcode, owner, JMVX_NATIVE_PREFIX + name, descriptor, isInterface);
                        return;
                    }
                }

                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }

    @Override
    public void visitEnd() {
        // Generate all native methods
        for (MethodNode mn : this.nativeMethods) {
            MethodVisitor mv = this.cv.visitMethod(mn.access & ~ACC_NATIVE, JMVX_NATIVE_PREFIX + mn.name, mn.desc, mn.signature, mn.exceptions.toArray(new String[0]));

            //stack for instance methods: 0 = this, 1..n-1 = arguments, n=exception if thrown
            //for static methods 0..n-1, n = exception if thrown. Need to load class object for monitorenter in this case
            int argAndRetSizes = Type.getArgumentsAndReturnSizes(mn.desc);
            //see docs for getArgumentsAndReturnSizes to make sense of the unpacking...
            int argSize = argAndRetSizes >> 2;
            int returnSize = argAndRetSizes & 3;
            Type argTypes[] = Type.getArgumentTypes(mn.desc);
            int returnLoc = argSize;
            int execpLoc = returnLoc + returnSize;
            boolean isStatic = (mn.access & ACC_STATIC) != 0;

            mv.visitCode();
            Label l0 = new Label();
            Label l1 = new Label();
            Label l2 = new Label();
            mv.visitTryCatchBlock(l0, l1, l2, null);
            Label l3 = new Label();
            mv.visitLabel(l3);
            if(isStatic){
                returnLoc--;
                execpLoc--;
            }

            mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_TYPE, "beforeNativeCall", VOID_VOID_DESC);

            mv.visitLabel(l0);

            if(!isStatic) {
                mv.visitVarInsn(ALOAD, 0);
            }

            //load any other arguments
            BytecodeUtils.loadAllArgumentsToTheStack(mn.desc, mv, isStatic);

            int opcode = isStatic ? INVOKESTATIC : INVOKESPECIAL;
            mv.visitMethodInsn(opcode, this.className, mn.name, mn.desc, false);

            mv.visitLabel(l1);

            mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_TYPE, "afterNativeCall", VOID_VOID_DESC);
            mv.visitInsn(Type.getReturnType(mn.desc).getOpcode(IRETURN));

            mv.visitLabel(l2);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
            mv.visitVarInsn(ASTORE, execpLoc);
            mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_TYPE, "afterNativeCall", VOID_VOID_DESC);
            mv.visitVarInsn(ALOAD, execpLoc);
            mv.visitInsn(ATHROW);

            Label l7 = new Label();
            mv.visitLabel(l7);

            //allocates extra space if static b/c I assume a "this"
            mv.visitMaxs(argSize + returnSize + 1, argTypes.length + 1);
            mv.visitEnd();

        }
        super.visitEnd();
    }
}
