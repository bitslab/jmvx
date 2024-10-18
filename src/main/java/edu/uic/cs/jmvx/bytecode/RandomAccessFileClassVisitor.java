package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRandomAccessFile;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.RandomAccessFile;
import java.util.Optional;

public class RandomAccessFileClassVisitor extends RenameClassVisitor {

    private static String LENGTH_DESCRIPTOR;
    private static String JMVX_LENGTH_DESCRIPTOR;
    private static String SET_LENGTH_DESCRIPTOR;
    private static String JMVX_SET_LENGTH_DESCRIPTOR;
    private static final Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);
    private static final Type RANDOM_ACCESS_FILE = Type.getType(RandomAccessFile.class);

    static {
        try {
            LENGTH_DESCRIPTOR = Type.getMethodDescriptor(RandomAccessFile.class.getMethod("length"));
            JMVX_LENGTH_DESCRIPTOR = Type.getMethodDescriptor(JMVXRuntime.class.getMethod("length", RandomAccessFile.class));

            SET_LENGTH_DESCRIPTOR = Type.getMethodDescriptor(RandomAccessFile.class.getMethod("setLength", long.class));
            JMVX_SET_LENGTH_DESCRIPTOR = Type.getMethodDescriptor(JMVXRuntime.class.getMethod("setLength", RandomAccessFile.class, long.class));
        }catch (ReflectiveOperationException e){
            throw new Error(e);
        }
    }

    public RandomAccessFileClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, Type.getType(RandomAccessFile.class), JMVXRandomAccessFile.class);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(ASM7, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                //TODO, create a data structure that stores descriptors to find and replace (e.g. hashmap)
                if(name.equals("length") && isType(Type.getObjectType(owner)) && descriptor.equals(LENGTH_DESCRIPTOR)) {
                    //owner.equals(RANDOM_ACCESS_FILE.getInternalName())
                    owner = JMVX_RUNTIME.getInternalName();
                    opcode = INVOKESTATIC;
                    descriptor = JMVX_LENGTH_DESCRIPTOR;
                }else if(name.equals("setLength") && isType(Type.getObjectType(owner)) && descriptor.equals(SET_LENGTH_DESCRIPTOR)){
                    owner = JMVX_RUNTIME.getInternalName();
                    opcode = INVOKESTATIC;
                    descriptor = JMVX_SET_LENGTH_DESCRIPTOR;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }

    @Override
    protected boolean isType(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return RandomAccessFile.class.isAssignableFrom(c);
        }catch (ClassNotFoundException | LinkageError e){
            return false;
        }
    }

}
