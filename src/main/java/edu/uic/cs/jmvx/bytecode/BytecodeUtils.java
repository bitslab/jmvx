package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.stream.Stream;

public class BytecodeUtils implements Opcodes {
    public static void loadAllArgumentsToTheStack(String desc, MethodVisitor mv, boolean isStatic) {
        Type[] argTypes = Type.getArgumentTypes(desc);
        int pos = 1; //0 = this
        if(isStatic)
            pos = 0;
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

    public static String addArgumentToMethod(Type toAdd, String desc) {
        Type[] argTypes = Stream.concat(Stream.of(toAdd), Arrays.stream(Type.getArgumentTypes(desc))).toArray(Type[]::new);
        return Type.getMethodDescriptor(Type.getReturnType(desc), argTypes);
    }

    public static String[] addInterface(String[] interfaces, Type toAdd) {
        if (interfaces == null) interfaces = new String[0];
        interfaces = Stream.concat(Arrays.stream(interfaces), Stream.of(toAdd.getInternalName())).toArray(String[]::new);
        return interfaces;
    }
}
