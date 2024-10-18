package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPoolExecutorClassVisitor extends ClassVisitor implements Opcodes {

    //methods to instrument. Map method names to method descriptors (both as strings)
    private static final HashMap<String, String> mthds = new HashMap<>();
    private static Type THREAD_POOL_EXECUTOR = Type.getType(ThreadPoolExecutor.class);
    private static Type CONC_LINK_QUEUE = Type.getType(ConcurrentLinkedQueue.class);
    private static Type EXECUTORS = Type.getType(Executors.class);
    private static Type JMVX_RUNTIME = Type.getType(JMVXRuntime.class);
    private static boolean enabled = false;

    static {
        try {
            /**
             * Instrument 2 methods: getTask and runWorker
             * getTask ensures that tasks are assigned to the same threads between leader/follower
             * getTask is not used for the first task a thread gets! runWorker has some logic to get the first
             * task that avoids this method. To avoid missing this task, runWorker is also synchronized.
             * SynchronizedClassVisitor will be applied later and (should) force these methods to use the vector clock.
             */
            Method GET_TASK_MTHD = ThreadPoolExecutor.class.getDeclaredMethod("getTask");
            Type GET_TASK = Type.getType(GET_TASK_MTHD);

            //ThreadPoolExecutor.Worker.class is a private class so we can't reference it normally
            Class<?> worker = Class.forName(ThreadPoolExecutor.class.getName() + "$Worker");
            Method RUN_WORKER_MTHD = ThreadPoolExecutor.class.getDeclaredMethod("runWorker", worker);
            Type RUN_WORKER = Type.getType(RUN_WORKER_MTHD);

            mthds.put(GET_TASK_MTHD.getName(), GET_TASK.getDescriptor());

            Method POLL_MTHD = ConcurrentLinkedQueue.class.getDeclaredMethod("poll");
            Type POLL_TYPE = Type.getType(POLL_MTHD);
            mthds.put(POLL_MTHD.getName(), POLL_TYPE.getDescriptor());
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public ThreadPoolExecutorClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        enabled = THREAD_POOL_EXECUTOR.getInternalName().equals(name) || CONC_LINK_QUEUE.getInternalName().equals(name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    public boolean shouldInstrumentMethod(String name, String descriptor){
        String desc = mthds.get(name);
        return desc != null && desc.equals(descriptor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if(enabled && shouldInstrumentMethod(name, descriptor)){
            access+=ACC_SYNCHRONIZED;
        }

        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(ASM7, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == INVOKESTATIC && owner.equals(EXECUTORS.getInternalName()) && name.equals("newCachedThreadPool")) {
                    owner = JMVX_RUNTIME.getInternalName();
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }
}
