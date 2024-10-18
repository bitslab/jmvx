package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashSet;

/**
 * This class instruments reflection. Main issue with reflection is that the results are unordered
 * This class visitor will redirect reflection calls to JMVX.Runtime methods that will perform the reflection
 * and then order the results.
 */
public class ReflectionClassVisitor extends ClassVisitor implements Opcodes {

    private final static String CLASS_INTERNAL_NAME = Type.getInternalName(Class.class);
    private String internalName;
    private final static HashSet<String> WHITE_LIST = new HashSet<>();
    private final static String JMVX_RUNTIME_INTERNAL_NAME = Type.getInternalName(JMVXRuntime.class);
    //private final static String NEW_DESCRIPTOR;

    static{
        WHITE_LIST.add("getMethods");
        WHITE_LIST.add("getDeclaredMethods");
        WHITE_LIST.add("getFields");
        WHITE_LIST.add("getConstructors");
        /*try {
            NEW_DESCRIPTOR = Type.getMethodDescriptor(JMVXRuntime.class.getMethod());
        }catch (ReflectiveOperationException e){
            e.printStackTrace();
        }*/
    }

    private static final String FILE_PROP_NAME = "jmvx.file";
    private static final Boolean enabled = !System.getProperty(FILE_PROP_NAME).equals("rt.jar");

    public ReflectionClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        internalName = name;
    }

    private static String newMethodDescriptor(String oldDescriptor){
        return oldDescriptor.replace("()", "(L" + CLASS_INTERNAL_NAME + ";)");
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if(!enabled) {
            return mv;
        }
        return new MethodVisitor(ASM7, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if(owner.equals(CLASS_INTERNAL_NAME) && WHITE_LIST.contains(name)){
                    opcode = INVOKESTATIC;
                    owner = JMVX_RUNTIME_INTERNAL_NAME;
                    descriptor = newMethodDescriptor(descriptor);
                    isInterface = false;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }
}
