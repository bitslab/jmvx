package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ResourceBundle;

/**
 * Xalan uses resource bundles which are stored in a time expiring cache.
 * Benchmark uses ResourceBundle.Control.INSTANCE (the default instance)
 * The class is basically an interface to how to handle the cache and time computations
 * So the goal of this class visitor is to overwrite the default Control Instance to
 * disable to cache entirely.
 *
 * getTimeToLive -> return ResourceBundle.Control.TTL_DONT_CACHE
 */
public class ResourceBundleControlClassVisitor extends ClassVisitor implements Opcodes {

    String targetClassName = Type.getInternalName(ResourceBundle.Control.class);
    String targetMethodName = "getTimeToLive";
    //maybe I should go through the reflection API? This way it throws an error if this ever changes
    String fieldName = "TTL_DONT_CACHE";
    //String internalName;
    Boolean enabled = false;

    public ResourceBundleControlClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        //internalName = name;
        enabled = targetClassName.equals(name);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if(enabled && name.equals(targetMethodName)){
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, targetClassName, fieldName, Type.LONG_TYPE.getDescriptor());
            mv.visitInsn(Type.LONG_TYPE.getOpcode(IRETURN));
            mv.visitMaxs(1, 0);
            mv.visitEnd();
            return null;
        }
        return mv;
    }
}
