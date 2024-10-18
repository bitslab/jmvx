package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;

/**
 * Used to make methods that aren't synchronized synchronized.
 * Used for two purposes: fix (some instances of double check locking and make caches trackable
 *
 * NOTE: this does not handle the double check locking in Sunflow.
 * In Sunflow, the double check locking is fine is it happens out of order because
 * Geometry.intersect (the method that uses double check) does not cause loggable events
 * That issue is handled via the deny list in SynchronizedClassVisitor
 */
public class MakeSynchronizedClassVisitor extends ClassVisitor implements Opcodes {
    private String methodName;
    private Boolean inTargetClass = false;

    private static final HashMap<String, String> toFix = new HashMap<>();

    static {
        //lazy initializer of static variables.
        toFix.put("org/apache/xml/serializer/OutputPropertiesFactory", "getDefaultMethodProperties");
        //race to put data in the cache (in Xalan). First thread has different operations than all others after
        toFix.put("org/apache/xml/serializer/CharInfo", "getCharInfo");
        //ensure tasks finish in the same order as leader/recording
        toFix.put("java/util/concurrent/ExecutorCompletionService$QueueingFuture", "done");
    }

    public MakeSynchronizedClassVisitor(org.objectweb.asm.ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        methodName = toFix.get(name);
        inTargetClass = methodName != null;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        //should check descriptor, but I don't think any other variant of this method exists
        if(inTargetClass && name.equals(methodName)){
            access += ACC_SYNCHRONIZED; //acc sync flag
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
