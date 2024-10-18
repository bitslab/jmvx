package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.Method;

public class InterceptMainMethodClassVisitor extends ClassVisitor implements Opcodes {

    private String internalName;
    private final String funcPrefix = "$JMVX$old$";
    private static final String JMVX_RUNTIME_INTERNAL_NAME = org.objectweb.asm.Type.getInternalName(JMVXRuntime.class);

    public InterceptMainMethodClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    //taken from SynchronizedClassVisitor and modified slightly...
    private void addWrappedMethod(int access, String name, String descriptor) throws NoSuchMethodException {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, null, null);
        Method mainEnter = Method.getMethod(JMVXRuntime.class.getDeclaredMethod("$JMVX$main"));
        Method mainExit = Method.getMethod(JMVXRuntime.class.getDeclaredMethod("exitMain"));

        //using the int because Object doesn't have an OBJECT_TYPE like the rest...
        //so comparison to an object reference has to be done using the sort/int
        int argAndRetSizes = Type.getArgumentsAndReturnSizes(descriptor);
        //see docs for getArgumentsAndReturnSizes to make sense of the unpacking...
        int argSize = argAndRetSizes >> 2;
        int returnSize = argAndRetSizes & 3;
        int returnType = Type.getReturnType(descriptor).getSort();
        Type argTypes[] = Type.getArgumentTypes(descriptor);
        int returnLoc = argSize;// = argTypes.length + 1; //need to save this
        int execpLoc = returnLoc + returnSize;// = returnLoc+1;
        boolean isStatic = (access & ACC_STATIC) != 0;
        //used to track the position of local vars
        int m = 1;
        Type classObj = Type.getType("L" + internalName + ";");

        mv.visitCode();
        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        Label ret = new Label();
        mv.visitTryCatchBlock(l0, l1, l2, null);
        Label l3 = new Label();
        mv.visitLabel(l3);

        if(isStatic){ //can probably remove the if and just set m, we know we are in main
            m = 0;
        }

        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, mainEnter.getName(), mainEnter.getDescriptor(), false);

        mv.visitLabel(l0);

        if(!isStatic) {
            mv.visitVarInsn(ALOAD, 0);
        }

        //load any other arguments
        for(int i = 0; i < argTypes.length; i++){
            switch(argTypes[i].getSort()){
                case Type.ARRAY:
                    //fallthrough
                case Type.OBJECT:
                    //ALOAD
                    //i starts at 0, but should go from 1 on
                    mv.visitVarInsn(ALOAD, m);
                    break;
                case Type.LONG:
                    mv.visitVarInsn(LLOAD, m);
                    break;
                case Type.DOUBLE:
                    mv.visitVarInsn(DLOAD, m);
                    break;
                case Type.FLOAT:
                    mv.visitVarInsn(FLOAD, m);
                    break;
                default:
                    //ILOAD
                    mv.visitVarInsn(ILOAD, m);
            }
            m += argTypes[i].getSize();
        }

        if(isStatic){
            mv.visitMethodInsn(INVOKESTATIC, internalName, funcPrefix + name, descriptor, false);
        }else{
            mv.visitMethodInsn(INVOKEVIRTUAL, internalName, funcPrefix + name, descriptor, false);
        }
        //return
        //try switching to store again?
        switch(returnType){
            case Type.VOID:
                //mv.visitInsn(RETURN);
                //execpLoc = m;
                break;
            case Type.ARRAY:
                //fallthrough
            case Type.OBJECT:
                mv.visitVarInsn(ASTORE, returnLoc);
                //execpLoc = returnLoc + 1;
                //mv.visitInsn(ARETURN);
                break;
            case Type.LONG:
                mv.visitVarInsn(LSTORE, returnLoc);
                //execpLoc = returnLoc + 2;
                //mv.visitInsn(LRETURN);
                break;
            case Type.DOUBLE:
                mv.visitVarInsn(DSTORE, returnLoc);
                break;
            case Type.FLOAT:
                mv.visitVarInsn(FSTORE, returnLoc);
                break;
            default:
                mv.visitVarInsn(ISTORE, returnLoc);
        }

        mv.visitLabel(l1);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, mainExit.getName(), mainExit.getDescriptor(), false);

        //goto return stmt
        mv.visitJumpInsn(GOTO, ret);

        mv.visitLabel(l2);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
        mv.visitVarInsn(ASTORE, execpLoc); //don't want to inc, we have to reuse later
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, mainExit.getName(), mainExit.getDescriptor(), false);
        mv.visitVarInsn(ALOAD, execpLoc);
        mv.visitInsn(ATHROW);

        mv.visitLabel(ret);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        switch(returnType){
            case org.objectweb.asm.Type.VOID:
                mv.visitInsn(RETURN);
                break;
            case org.objectweb.asm.Type.ARRAY:
                //fallthrough
            case org.objectweb.asm.Type.OBJECT:
                mv.visitVarInsn(ALOAD, returnLoc);
                mv.visitInsn(ARETURN);
                break;
            case org.objectweb.asm.Type.LONG:
                mv.visitVarInsn(LLOAD, returnLoc);
                mv.visitInsn(LRETURN);
                break;
            case org.objectweb.asm.Type.DOUBLE:
                mv.visitVarInsn(DLOAD, returnLoc);
                mv.visitInsn(DRETURN);
                break;
            case org.objectweb.asm.Type.FLOAT:
                mv.visitVarInsn(FLOAD, returnLoc);
                mv.visitInsn(FRETURN);
                break;
            default:
                mv.visitVarInsn(ILOAD, returnLoc);
                mv.visitInsn(IRETURN);
        }

        Label l7 = new Label();
        mv.visitLabel(l7);

        m = 0;
        if(!isStatic) {
            mv.visitLocalVariable("this", "L" + internalName + ";", null, l3, l7, 0);
            m = 1;
        }

        for(int i = 0; i < argTypes.length; i++){
            mv.visitLocalVariable("v" + Integer.toString(i + 1), argTypes[i].getDescriptor(), null, l3, l7, m);
            m += argTypes[i].getSize();
        }
        //allocates extra space if static b/c I assume a "this"
        mv.visitMaxs(argSize + returnSize + 1, argTypes.length + 1);
        mv.visitEnd();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        internalName = name;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv;// = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (this.isMainMethod(access, name, descriptor)) {
            //do the renaming
            mv = super.visitMethod(access, funcPrefix + name, descriptor, signature, exceptions);
            try {
                addWrappedMethod(access, name, descriptor);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }else{
            mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        return mv;
    }

    private boolean isMainMethod(int access, String name, String descriptor) {
        //An entry point must be public static void main(String)
        return (access & (ACC_PUBLIC | ACC_STATIC)) != 0
                && name.equals("main")
                && descriptor.equals("([Ljava/lang/String;)V");
    }
}
