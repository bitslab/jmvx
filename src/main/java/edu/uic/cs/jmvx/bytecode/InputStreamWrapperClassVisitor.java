package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

public class InputStreamWrapperClassVisitor extends ClassVisitor implements Opcodes {
    private static final Logger logger = Logger.getLogger(InputStreamWrapperClassVisitor.class);
    private final static ClassLoader defaultLoader = InputStreamWrapperClassVisitor.class.getClassLoader();

    private Type superType;
    private final Set<Type> classesToGenerate;
    private final Optional<ClassLoader> cl;


    public InputStreamWrapperClassVisitor(Set<Type> classesToGenerate, Optional<ClassLoader> cl, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.classesToGenerate = classesToGenerate;
        this.cl = cl;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.superType = Type.getObjectType(superName);

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                Type returnType = Type.getReturnType(descriptor);

                try {
                    Class own = Class.forName(Type.getObjectType(owner).getClassName(), false, cl.orElse(defaultLoader));
                    if (isConstructor(opcode, name, descriptor) && InputStream.class.isAssignableFrom(own)) {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        //super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ReplayUtils.class), "wrap", "(Ljava/io/InputStream;)Ljava/io/InputStream;", false);
                        //super.visitTypeInsn(CHECKCAST, ownerType.getInternalName());
                        classesToGenerate.add(Type.getObjectType(owner));
                        logger.trace("CONSTRUCTOR " + owner + descriptor);
                        return;
                    }
                    switch (returnType.getSort()) {
                        case Type.VOID:
                        case Type.BOOLEAN:
                        case Type.CHAR:
                        case Type.BYTE:
                        case Type.SHORT:
                        case Type.INT:
                        case Type.FLOAT:
                        case Type.LONG:
                        case Type.DOUBLE:
                            // Primitive sort, skip
                            break;
                        case Type.OBJECT:
                            if (isClassInputStream(returnType, cl)) {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(JMVXRuntime.class), "wrap", "(Ljava/io/InputStream;)Ljava/io/InputStream;", false);
                                super.visitTypeInsn(CHECKCAST, returnType.getInternalName());
                                classesToGenerate.add(returnType);
                                logger.trace("WRAP - " + owner + " " + name + " " + descriptor);
                                return;
                            }
                            break;
                        case Type.ARRAY:
                            // TODO: Arrays of InputStream
                            break;
                        default:
                            logger.warn("Unsupported sort: " + returnType.getClassName());
                            break;

                    }
                } catch (ClassNotFoundException | LinkageError | SecurityException e) {
                    // Log error and continue processing
                    logger.trace(e.getMessage());
                }


                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };

    }

    private static boolean isConstructor(int invokeOpcode, String methodName, String methodDescriptor) {
        return Type.VOID_TYPE.equals(Type.getReturnType(methodDescriptor))
                && invokeOpcode == INVOKESPECIAL
                && "<init>".equals(methodName);
    }

    private static boolean isClassInputStream(Type type, Optional<ClassLoader> cl) throws ClassNotFoundException {
        Class<?> ret = Class.forName(type.getClassName(), false, cl.orElse(defaultLoader));
        return InputStream.class.isAssignableFrom(ret);
    }
}
