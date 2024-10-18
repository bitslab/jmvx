package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.*;

import java.util.Optional;

public class clinitClassVisitor extends ClassVisitor implements Opcodes {

    private ClassLoader loader;
    String internalName = "";

    //copied from MonitorClassVisitor
    //we have been okay with JVM's clinits, so only enable for dacapo
    private static final String FILE_PROP_NAME = "jmvx.file";
    private boolean enabled = true;
    private boolean isInterface = false;

    private String[] deny_list = { "java/lang", "java/util", "java/io", "java/security", "sun/reflect", "sun/misc" };

    private static final String JMVX_RUNTIME_INTERNAL_NAME = Type.getInternalName(JMVXRuntime.class);
    private static String JMVX_INIT_DESCRIPTOR;

    static {
        try {
            JMVX_INIT_DESCRIPTOR = Type.getMethodDescriptor(JMVXRuntime.class.getMethod("$JMVX$clinitStart", String.class));
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public clinitClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor){
        super(ASM7, classVisitor);
        this.loader = loader.orElse(this.getClass().getClassLoader());
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        for (String s : deny_list)
            if (name.startsWith(s))
                enabled = false;

        internalName = name;
        isInterface = (access & ACC_INTERFACE) != 0;
    }

    private void addWrappedMethod(int access, String newName, String descriptor, String signature, String[] exceptions){
        MethodVisitor mv = super.visitMethod(access, "<clinit>", descriptor, signature, exceptions);

        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();

        mv.visitTryCatchBlock(l0, l1, l2, null);

        mv.visitCode();
        mv.visitLdcInsn(internalName);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, "$JMVX$clinitStart", JMVX_INIT_DESCRIPTOR, false);

        mv.visitLabel(l0);
        mv.visitMethodInsn(INVOKESTATIC, internalName, newName, descriptor, false);

        mv.visitLabel(l1);
        mv.visitLdcInsn(internalName);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, "$JMVX$clinitEnd", JMVX_INIT_DESCRIPTOR, false);
        mv.visitInsn(RETURN);

        mv.visitLabel(l2);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
        mv.visitVarInsn(ASTORE, 0);

        mv.visitLdcInsn(internalName);
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, "$JMVX$clinitEnd", JMVX_INIT_DESCRIPTOR, false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ATHROW);

        mv.visitMaxs(1,1);
        mv.visitEnd();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if(enabled && !isInterface && name.equals("<clinit>")){
            name = JMVXRuntime.Prefix + "clinit";
            addWrappedMethod(access, name, descriptor, signature, exceptions);
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
