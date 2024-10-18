package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXFileInputStream;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class FileInputStreamClassVisitor extends ClassVisitor implements Opcodes {
    private static final Logger logger = Logger.getLogger(FileInputStreamClassVisitor.class);
    private static final Type JMVX_STREAM  = Type.getType(JMVXFileInputStream.class);
    private static final Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);

    private static final Type FILE_INPUT_STREAM = Type.getType(FileInputStream.class);

    private final ClassLoader loader;
    private boolean enabled = false;
    private Type thisType;
    private Set<MethodNode> methodsToGenerate = new HashSet<>();

    public FileInputStreamClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = loader.orElse(FileInputStreamClassVisitor.class.getClassLoader());
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.thisType = Type.getObjectType(name);

        enabled = this.isFileInputStream(this.thisType);

        if (enabled) {
            logger.info("Found file input stream: " + name);
            // Add JMVXFileInputStream interface to found FileInputStream
            if (interfaces == null) interfaces = new String[0];
            interfaces = Stream.concat(Arrays.stream(interfaces), Stream.of(JMVX_STREAM.getInternalName())).toArray(String[]::new);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

        if (!enabled)
            return super.visitMethod(access, name, descriptor, signature, exceptions);

        final String methodName = renameMethod(name, descriptor, this.thisType, access);

        if (!methodName.equals(name)) {
            // Method was renamed, we need to generate the original method
            this.methodsToGenerate.add(new MethodNode(0, name, descriptor, null, null));
            access = (access & (~ACC_PRIVATE) | ACC_PUBLIC);
        }

        return super.visitMethod(access, methodName, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        if (enabled) {
            // Generate intercepted methods that go through JMVXRuntime
            for (MethodNode mn : methodsToGenerate)
                generateMethod(mn.name, mn.desc);
        }

        super.visitEnd();
    }

    private void generateMethod(String name, String descriptor) {
        String jmvxRuntimeMethodDescriptor = addArgumentToMethod(FILE_INPUT_STREAM, descriptor);

        MethodVisitor mv;
        mv = super.visitMethod(ACC_PUBLIC | ACC_SYNCHRONIZED, name, descriptor, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        loadAllArgumentsToTheStack(descriptor, mv);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME.getInternalName(), name, jmvxRuntimeMethodDescriptor, false);
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private String addArgumentToMethod(Type toAdd, String desc) {
        Type[] argTypes = Stream.concat(Stream.of(toAdd), Arrays.stream(Type.getArgumentTypes(desc))).toArray(Type[]::new);

        return Type.getMethodDescriptor(Type.getReturnType(desc), argTypes);
    }

    private void loadAllArgumentsToTheStack(String desc, MethodVisitor mv) {
        Type[] argTypes = Type.getArgumentTypes(desc);
        for (int i = 0 ; i < argTypes.length ; i++) {
            Type argType = argTypes[i];
            if (argType.getSort() == Type.ARRAY) {
                mv.visitVarInsn(ALOAD, i + 1);
            } else {
                mv.visitVarInsn(argType.getOpcode(ILOAD), i + 1);
            }
        }
    }

    private String renameMethod(String originalName, String descriptor, Type owner, int access) {

        // Method does not belong to FileInputStream
        if (!this.isFileInputStream(owner))
            return originalName;

        for (Method m : JMVXFileInputStream.class.getDeclaredMethods()) {
            String jmvxName = m.getName().replace(JMVXRuntime.Prefix, "");
            String jmvxDescriptor = Type.getMethodDescriptor(m);

            if (originalName.equals(jmvxName) && descriptor.equals(jmvxDescriptor)) {
                if ((access & ACC_NATIVE) != 0)
                    throw new Error("Method " + owner + ":" + originalName + " is native");
                return m.getName();
            }
        }

        return originalName;
    }

    private boolean isFileInputStream(Type t) {
        if (t.getSort() != Type.OBJECT)
            return false;

        try {
            Class<?> c = Class.forName(t.getClassName(), false, loader);
            return FileInputStream.class.isAssignableFrom(c);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            logger.trace("I don't know if this is a FileInputStream or not: " + t.getClassName() + " because " + e.getMessage() );
            return false;
        }
    }
}
