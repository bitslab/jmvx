package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.Optional;

public class UnixNativeDispatcherClassVisitor extends ClassVisitor implements Opcodes {

    static final Class<?> UNIX_NATIVE_DISP_CLASS;
    static final Type UNIX_NATIVE_DISP_TYPE;
    /*
    Chicken versus the egg problem
    We don't use JMVXUnixNativeDispatcher.class because that will load JMVXUnixNativeDispatcher and crash.
    JMVXUnixNativeDispatcher is compiled to use UnixNativeDispatcher after accessor modifications.
    The bytecode won't verify if loaded now.
    
    We could use ASM to read the defined methods in the class instead, and in that way we don't load it
    and get the ability to reference it via code.
     */
    static final String[] methods = {"stat", "lstat", "open", "opendir", "readdir", "closedir", "mkdir", "access", "dup", "fdopendir", "realpath"};
    static final String redirectTo = "edu/uic/cs/jmvx/runtime/JMVXUnixNativeDispatcher";

    static{
        try {
            ClassLoader cl = UnixNativeDispatcherClassVisitor.class.getClassLoader();
            UNIX_NATIVE_DISP_CLASS = Class.forName("sun.nio.fs.UnixNativeDispatcher", false, cl);
            UNIX_NATIVE_DISP_TYPE = Type.getType(UNIX_NATIVE_DISP_CLASS);
        } catch (ClassNotFoundException e) {
            throw new Error("WE BOTCHED IT", e);
        }
    }

    boolean inClass = false;

    public UnixNativeDispatcherClassVisitor(ClassVisitor classVisitor) throws ClassNotFoundException {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        inClass = UNIX_NATIVE_DISP_TYPE.getInternalName().equals(name);
        if(inClass)
            access |= ACC_PUBLIC;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        //should maybe check the descriptors too...
        if (inClass){ //in UnixNativeDispatcher, just correct accesses
            if(instrumentMethodCall(name))
                access |= ACC_PUBLIC;
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }else{ //in another class, look for calls and diver to JMVX
            //NOTE: this diverts at callsites, and will not catch calls via reflection.
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(ASM7, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if(instrumentMethodCall(owner, name)) {
                        name = JMVXRuntime.Prefix + name;
                        owner = redirectTo;//JMVX_UNIX_NATIVE_TYPE.getInternalName();
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }
    }

    static boolean instrumentMethodCall(String name){
        return Arrays.stream(methods).anyMatch((mthd) -> mthd.equals(name));
    }

    static boolean instrumentMethodCall(String owner, String name){
        return owner.equals(UNIX_NATIVE_DISP_TYPE.getInternalName()) &&
                Arrays.stream(methods).anyMatch((mthd) -> mthd.equals(name));
    }
}
