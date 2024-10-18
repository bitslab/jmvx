package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.NullReference;
import org.objectweb.asm.*;

import java.lang.ref.SoftReference;
import java.util.jar.JarFile;

/**
 * This disables the cache in JarFile
 * Done by replacing the weak references in the cache with null references
 * null references are a weak reference whose get method always returns null
 * This simulates a cache miss.
 */
public class JarFileCacheClassVisitor extends ClassVisitor implements Opcodes {

    private static final Type JAR_FILE_TYPE = Type.getType(JarFile.class);
    private static final Type SOFT_REF_TYPE = Type.getType(SoftReference.class);
    private static final Type NULL_REF_TYPE = Type.getType(NullReference.class);
    private boolean enabled = false;

    public JarFileCacheClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        enabled = JAR_FILE_TYPE.getInternalName().equals(name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        //replace softrefs with nullrefs
        if(enabled && SOFT_REF_TYPE.getDescriptor().equals(descriptor)) {
            //field is not init'ed in JarFile, so we can just change descriptor (sig needs to change!)
            String newSig = "L" + NULL_REF_TYPE.getInternalName() + signature.substring(signature.indexOf('<'));
            return super.visitField(access, name, NULL_REF_TYPE.getDescriptor(), newSig, value);
        }else{
            return super.visitField(access, name, descriptor, signature, value);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        //modify body of funcs to replace calls to "new softref" with "new nullrefs"
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if(enabled){
            return new MethodVisitor(ASM7, mv) {
                @Override
                public void visitTypeInsn(int opcode, String type) { //type is internal name not descriptor
                    if(opcode == NEW && SOFT_REF_TYPE.getInternalName().equals(type)){
                        super.visitTypeInsn(opcode, NULL_REF_TYPE.getInternalName());
                    }else{
                        super.visitTypeInsn(opcode, type);
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    if(JAR_FILE_TYPE.getInternalName().equals(owner) &&
                            SOFT_REF_TYPE.getDescriptor().equals(descriptor)){
                        super.visitFieldInsn(opcode, owner, name, NULL_REF_TYPE.getDescriptor());
                    }else {
                        super.visitFieldInsn(opcode, owner, name, descriptor);
                    }
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if(opcode == INVOKESPECIAL && SOFT_REF_TYPE.getInternalName().equals(owner) && name.equals("<init>")){
                        //descriptors were made to match
                        super.visitMethodInsn(opcode, NULL_REF_TYPE.getInternalName(), name, descriptor, isInterface);
                    }else{
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                }
            };
        }else{
            return mv;
        }
    }
}
