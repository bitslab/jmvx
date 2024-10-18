package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.apache.log4j.Logger;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.FileOutputStream;
import java.util.Optional;

public class FileOutputStreamConstructorClassVisitor extends ClassVisitor implements Opcodes {
    private static final Logger logger = Logger.getLogger(FileOutputStreamConstructorClassVisitor.class);

    private static final String FILE_OUTPUT_STREAM_SUBCLASS = "edu/uic/cs/jmvx/runtime/FileOutputStream$$FileOutputStream";

    private final ClassLoader loader;
    private boolean enabled = false;
    private Type thisType;
    private boolean found = false;
    private String name;

    public FileOutputStreamConstructorClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = loader.orElse(FileOutputStreamConstructorClassVisitor.class.getClassLoader());
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.thisType = Type.getObjectType(name);

        enabled = this.isFileOutputStreamOrSub(this.thisType);
        this.name = name;

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

        if (enabled)
            return super.visitMethod(access, name, descriptor, signature, exceptions);

        return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                Type returnType = Type.getMethodType(descriptor).getReturnType();
                Type ownerType = Type.getObjectType(owner);

                if (isFileOutputStream(ownerType) && opcode == Opcodes.INVOKESPECIAL && returnType.equals(Type.VOID_TYPE) && name.equals("<init>")) {
                    logger.info("Found file output stream");
                    found = true;
                    super.visitMethodInsn(opcode, FILE_OUTPUT_STREAM_SUBCLASS, "<init>", descriptor, false);
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                Type ownerType = Type.getObjectType(type);

                if (isFileOutputStream(ownerType) && opcode == Opcodes.NEW)
                    super.visitTypeInsn(opcode, FILE_OUTPUT_STREAM_SUBCLASS);
                else
                    super.visitTypeInsn(opcode, type);
            }
        };
    }

    private boolean isFileOutputStreamOrSub(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return FileOutputStream.class.isAssignableFrom(c);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            logger.trace("I don't know if this is a FileOutputStream or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }
    }

    private boolean isFileOutputStream(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return FileOutputStream.class.equals(c);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            logger.trace("I don't know if this is a FileOutputStream or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }
    }
}
