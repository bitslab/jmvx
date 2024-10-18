package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.Type;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import sun.net.www.protocol.jar.JarURLConnection;

public class RemoveJarCacheClassVisitor extends ClassVisitor implements Opcodes {

    private static final String FILE_PROP_NAME = "jmvx.file";
    static boolean enabled = System.getProperty(FILE_PROP_NAME).equals("rt.jar");

    /**
     * Force [sun.net.www.protocol.jar] JarURLConnection.getUseCaches to return false
     * so JarFileFactory doesn't cache entries.
     */
    String internalName;
    static String targetClassName = Type.getInternalName(JarURLConnection.class);
    static String methodName = "getUseCaches";
    //static String methodDescriptor = "()Z";


    public RemoveJarCacheClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        internalName = name;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if(enabled && internalName.equals(targetClassName) && name.equals(methodName)){
            //force the method the always return false
            mv.visitCode();
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();
            return null;
        }
        return mv;
    }
}
