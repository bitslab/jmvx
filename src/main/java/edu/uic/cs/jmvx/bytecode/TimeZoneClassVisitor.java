package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.util.TimeZone;

import java.lang.reflect.Method;

public class TimeZoneClassVisitor extends ClassVisitor implements Opcodes {

    static final Type TIME_ZONE_TYPE = Type.getType(TimeZone.class);
    static final Type JMVX_RUNTIME_TYPE = Type.getType(JMVXRuntime.class);
    static Method methodToInst;
    static Type methodToInstType;

    boolean enabled = false;

    static {
        try {
            methodToInst = TimeZone.class.getDeclaredMethod("getSystemTimeZoneID", String.class);
            methodToInstType = Type.getType(methodToInst);
        } catch (NoSuchMethodException e) {
            throw new Error("Can't find getSystemTimeZoneID method", e);
        }
    }

    public TimeZoneClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        enabled = name.equals(TIME_ZONE_TYPE.getInternalName());
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if(enabled){
            if(isMethodToInstrument(name, descriptor)){
                access &= ~(ACC_PRIVATE | ACC_PROTECTED); //remove private or protected
                access |= ACC_PUBLIC; //make public
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }else {
                //method we care about is private, so we only look at call sites in the TimeZone class
                return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (owner.equals(TIME_ZONE_TYPE.getInternalName()) && isMethodToInstrument(name, descriptor))
                            owner = JMVX_RUNTIME_TYPE.getInternalName(); //redirect call to JMVX

                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }else{ //out of TimeZone class, just pass through
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    boolean isMethodToInstrument(String name, String descriptor){
        return name.equals(methodToInst.getName()) && descriptor.equals(methodToInstType.getDescriptor());
    }
}
