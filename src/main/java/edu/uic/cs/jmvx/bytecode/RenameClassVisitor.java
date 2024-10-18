package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXInputStream;
import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public abstract class RenameClassVisitor extends ClassVisitor implements Opcodes {
    protected static final Logger logger = Logger.getLogger(RenameClassVisitor.class);
    protected Class<?> JMVX_CLASS;
    protected Type JMVX_TYPE;
    private static final Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);

    protected Type TYPE;

    protected final ClassLoader loader;
    protected boolean enabled = false;
    protected Type thisType;
    private Set<MethodNode> methodsToGenerate = new HashSet<>();

    public RenameClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor, Type type, Class<?> JMVX_class) {
        super(ASM7, classVisitor);
        this.JMVX_CLASS = JMVX_class;
        this.JMVX_TYPE = Type.getType(JMVX_class);
        this.TYPE = type;
        this.loader = loader.orElse(this.getClass().getClassLoader());
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.thisType = Type.getObjectType(name);

        enabled = this.isType(this.thisType);

        if (enabled) {
            logger.info("Found: " + name);
            // Add JMVX interface to found type
            if (interfaces == null) interfaces = new String[0];
            interfaces = Stream.concat(Arrays.stream(interfaces), Stream.of(JMVX_TYPE.getInternalName())).toArray(String[]::new);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

        if (!enabled || (access & ACC_ABSTRACT) != 0)
            return super.visitMethod(access, name, descriptor, signature, exceptions);

        final String methodName = renameMethod(name, descriptor, this.thisType, access);

        if (!methodName.equals(name)) {
            // Method was renamed, we need to generate the original method
            this.methodsToGenerate.add(new MethodNode(0, name, descriptor, null, null));
            access = (access & (~ACC_PRIVATE) & (~ACC_PROTECTED) | ACC_PUBLIC);
        }

        return super.visitMethod(access, methodName, descriptor, signature, exceptions);
        /*return new MethodVisitor(ASM7, super.visitMethod(access, methodName, descriptor, signature, exceptions)) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == INVOKESPECIAL) {
                    String renamed = renameMethod(name, descriptor, Type.getObjectType(owner), 0);
                    name = renamed;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };*/
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

    protected void generateMethod(String name, String descriptor) {
        String jmvxRuntimeMethodDescriptor = addArgumentToMethod(TYPE, descriptor);

        MethodVisitor mv;
        //ACC_PUBLIC | ACC_SYNCHRONIZED
        mv = super.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
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
        int pos = 1; //0 = this
        for (int i = 0 ; i < argTypes.length ; i++) {
            Type argType = argTypes[i];
            if (argType.getSort() == Type.ARRAY) {
                mv.visitVarInsn(ALOAD, pos);
            } else {
                mv.visitVarInsn(argType.getOpcode(ILOAD), pos);
            }
            pos += argType.getSize();
        }
    }

    private String renameMethod(String originalName, String descriptor, Type owner, int access) {
        // Method does not belong to <Type>
        if (!this.isType(owner))
            return originalName;

        /*
        Use getMethods because some interfaces are "flags"
        E.g., JMVXSocketOutputStream is empty and is meant to signal a socket stream (in an instanceof check)
        It implements JMVXOutputStream to relay the methods to instrument.

        getMethods will return methods defined in both interfaces, which is what we want
        getDeclaredMethods will return nothing for JMVXSocketOutputStream because no methods are declared.
         */
        for (Method m : JMVX_CLASS.getMethods()) {
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

    protected abstract boolean isType(Type t);

}

