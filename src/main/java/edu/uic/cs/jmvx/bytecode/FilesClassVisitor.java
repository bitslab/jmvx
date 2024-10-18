package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;

public class FilesClassVisitor extends ClassVisitor implements Opcodes {

    private static final Type FILES_TYPE = Type.getType(Files.class);
    private static final Type JMVX_TYPE = Type.getType(JMVXRuntime.class);

    public FilesClassVisitor(ClassVisitor cv) {
        super(ASM7, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        return new MethodVisitor(ASM7, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == INVOKESTATIC && owner.equals(FILES_TYPE.getInternalName()) && name.equals("createTempFile"))
                    owner = JMVX_TYPE.getInternalName();

                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }
}
