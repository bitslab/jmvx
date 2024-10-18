package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.*;

public class ReplaceTypeClassVisitor extends ClassVisitor implements Opcodes {

    public static HashMap<String, String> replacements = new HashMap<>();
    static {
        //replace hashmaps and sets with their linked variants
        replacements.put(Type.getInternalName(HashMap.class), Type.getInternalName(LinkedHashMap.class));
        replacements.put(Type.getInternalName(HashSet.class), Type.getInternalName(LinkedHashSet.class));
    }
    private boolean enabled = false;

    /*
    Might also need to change classes that extend HashSet to LinkedHashSet and etc...
     */
    public ReplaceTypeClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        //don't modify the original classes, just where they are used.
        enabled = !replacements.containsKey(name) && !replacements.containsValue(name);
        if(enabled) {
            superName = replacements.getOrDefault(superName, superName);
        }
        //change extensions
        /*if(enabled) {
            for (int i = 0; i < interfaces.length; i++) {
                interfaces[i] = replacements.getOrDefault(interfaces[i], interfaces[i]);
            }
        }*/
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        if(enabled) {
            mv = new MethodVisitor(ASM7, mv) {
                @Override
                public void visitTypeInsn(int opcode, String type) {
                    if(opcode == NEW) {
                        type = replacements.getOrDefault(type, type);
                    }
                    super.visitTypeInsn(opcode, type);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if(name.equals("<init>")){
                        owner = replacements.getOrDefault(owner, owner);
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }

        return mv;
    }
}
