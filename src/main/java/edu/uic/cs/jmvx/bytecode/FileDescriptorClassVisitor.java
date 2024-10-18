package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXFile;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class FileDescriptorClassVisitor extends ClassVisitor implements Opcodes {
    private static final Logger logger = Logger.getLogger(FileDescriptorClassVisitor.class);
    private static final Type JMVX_FILE  = Type.getType(JMVXFile.class);
    private static final Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);
    private static final Type FILE_DESCRIPTOR = Type.getType(FileDescriptor.class);

    private static final String SYNC_DESCRIPTOR;

    static {
        try {
            SYNC_DESCRIPTOR = Type.getMethodDescriptor(JMVXRuntime.class.getMethod("sync", FileDescriptor.class));
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }

    private final ClassLoader loader;
    private boolean enabled = true;
    private Type thisType;
    private Set<MethodNode> methodsToGenerate = new HashSet<>();

    public FileDescriptorClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = loader.orElse(FileDescriptorClassVisitor.class.getClassLoader());
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

        if (!enabled)
            return super.visitMethod(access, name, descriptor, signature, exceptions);

        return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == INVOKEVIRTUAL && owner.equals(FILE_DESCRIPTOR.getInternalName()) && name.equals("sync")) {
                    opcode = INVOKESTATIC;
                    owner = JMVX_RUNTIME.getInternalName();
                    descriptor = SYNC_DESCRIPTOR;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }
}
