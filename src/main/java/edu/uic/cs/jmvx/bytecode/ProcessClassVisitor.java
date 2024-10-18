package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXProcessBuilder;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.util.Optional;

public class ProcessClassVisitor extends RenameClassVisitor {
    private static final Type PROCESS_BUILDER_TYPE = Type.getType(ProcessBuilder.class);

    public ProcessClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType(ProcessBuilder.class), JMVXProcessBuilder.class);
    }

    @Override
    protected boolean isType(Type t) {
        return t.equals(PROCESS_BUILDER_TYPE);
    }

//    @Override
//    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
//        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
//        if (!enabled)
//            return mv;
//
//        return new MethodVisitor(ASM7, mv) {
//            @Override
//            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
//                if (owner.equals(PROCESS_BUILDER_TYPE.getInternalName())) {
//                    if (name.equals("<init>") && descriptor.equals(PROC_BUILDER_CONS_DESC)) {
//                        super.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), NEW_PROC_BUILDER_NAME, NEW_PROC_BUILDER_DESC, false);
//                    } else if (name.equals("start")) {
//                        opcode = INVOKESTATIC;
//                        owner = JMVX_RUNTIME.getInternalName();
//                        descriptor = START_DESC;
//                        isInterface = false;
//                    }
//                }
//
//                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
//            }
//        };
//    }


}
