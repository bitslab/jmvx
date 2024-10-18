package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.apache.log4j.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.Method;

import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class SynchronizedClassVisitor extends ClassVisitor implements Opcodes {

    private static final Logger log = Logger.getLogger(SynchronizedClassVisitor.class);
    private ClassLoader loader;
    private Class<?> currentClass;
    private String internalName;
    private static final String JMVX_RUNTIME_INTERNAL_NAME = Type.getInternalName(JMVXRuntime.class);

    //classes to not instrument
    private static final HashSet<String> DENY_LIST = new HashSet();
    private static HashSet<String> JVM_WHITE_LIST = new HashSet<>();

    private static final String FILE_PROP_NAME = "jmvx.file";
    private static final Boolean instrumentingJVM = System.getProperty(FILE_PROP_NAME).equals("rt.jar");
    private boolean enabled = !instrumentingJVM;

    /**
    Reasons for skipping classes:
    org/sunflow/core/Geometry - the synchronized methods are initializers. The <i>intersect<\i> method
        uses double check locking. This class will always run in an arbitrary order,
        but the order doesn't really matter, because the methods are initializers.
        <b>Sunflow will typically deadlock if this class is instrumented</b>
     org/sunflow/system/UI - this class doesn't actually do anything in the benchmark--by default, the UI is turned off
        nor does it print anything to the command line. Tracking the monitor for this class just adds overhead.
     org/python/core/PyType and org/python/core/PyJavaType - put and get data from a CustomConcurrentHashMap that uses
     weak entries which can be GC'ed at anytime. If the JVM's differ on GC, then the programs diverge because these
     methods will take different routes and generate different sequences of monitorenter/exit's.

     Reasons for instrumenting specific JVM classes:
     ThreadPoolExecutor - Thread pools assign tasks to threads as the threads are available. This can break thread
         operation/event matching because two threads could be doing different tasks (even if those tasks are correct!).
         ThreadPoolExecutorClassVisitor makes getTask, the method responsible for assigning a task to a thread, synchronized
         and this class visitor forces it to adhere to a specific ordering, thereby creating the same task to thread
         assignments.
    */
    static {
        /*DENY_LIST.add("org/sunflow/core/Geometry");
        DENY_LIST.add("org/sunflow/system/UI");*/
        /*DENY_LIST.add("org/python/core/PyType");
        DENY_LIST.add("org/python/core/PyJavaType");*/
        /*DENY_LIST.add("org/apache/xml/dtm/ref/DTMManagerDefault");
        DENY_LIST.add("org/apache/xpath/axes/IteratorPool");
        DENY_LIST.add("org/apache/xml/utils/ObjectPool");*/
	//DENY_LIST.add("org/h2/message/TraceSystem");
        JVM_WHITE_LIST.add(Type.getInternalName(ThreadPoolExecutor.class));
        // This is a private class, so I have to list it manually
        JVM_WHITE_LIST.add("java/util/concurrent/ExecutorCompletionService$QueueingFuture");
        // poll needs to return in the same order on the follower/replayer
        JVM_WHITE_LIST.add(Type.getInternalName(ConcurrentLinkedQueue.class));
    }

    public SynchronizedClassVisitor(ClassLoader loader, ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
        this.loader = loader;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        //if the class contains a static sync'ed method, bump up its version
        //See comment in ByteCodeVersionBumpClassVisitor for details on why this is necessary
        if(!instrumentingJVM && ByteCodeVersionBumpClassVisitor.classes.contains(name)){
            version = ByteCodeVersionBumpClassVisitor.UPGRADE_VERSION;
        }
        super.visit(version, access, name, signature, superName, interfaces);
        internalName = name;
        enabled = (!instrumentingJVM && !DENY_LIST.contains(internalName)) || (instrumentingJVM && JVM_WHITE_LIST.contains(internalName));
        /*try {

            currentClass = Class.forName(Type.getObjectType(name).getClassName(), false, loader);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessError e) {
            // TODO log error
            log.trace("Not sure if this is a thread: " + name);
        }*/
    }

    public static void saveReturnVal(Type returnType, int returnLoc, MethodVisitor mv){
        if(returnType.getSort() == Type.VOID)
            return;

        mv.visitVarInsn(returnType.getOpcode(ISTORE), returnLoc);
    }

    public static void loadAndReturnVal(Type returnType, int returnLoc, MethodVisitor mv){
        if(returnType.getSort() == Type.VOID){
            mv.visitInsn(RETURN);
            return;
        }

        mv.visitVarInsn(returnType.getOpcode(ILOAD), returnLoc);
        mv.visitInsn(returnType.getOpcode(IRETURN));
    }

    public static void loadAllArgumentsToTheStack(boolean isStatic, String desc, MethodVisitor mv) {
        int memLoc = 1;

        if(isStatic){
            memLoc = 0;
        }

        Type[] argTypes = Type.getArgumentTypes(desc);
        for (int i = 0 ; i < argTypes.length ; i++) {
            Type argType = argTypes[i];
            mv.visitVarInsn(argType.getOpcode(ILOAD), memLoc);
            memLoc += argType.getSize();
        }
    }

    private void addWrappedMethod(int access, String name, String descriptor, String signature, String[] exceptions) throws NoSuchMethodException {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        Method monEnter = Method.getMethod(JMVXRuntime.class.getDeclaredMethod("monitorenter", Object.class));
        Method monExit = Method.getMethod(JMVXRuntime.class.getDeclaredMethod("monitorexit", Object.class));

        //stack for instance methods: 0 = this, 1..n-1 = arguments, n=exception if thrown
        //for static methods 0..n-1, n = exception if thrown. Need to load class object for monitorenter in this case
        int argAndRetSizes = Type.getArgumentsAndReturnSizes(descriptor);
        //see docs for getArgumentsAndReturnSizes to make sense of the unpacking...
        int argSize = argAndRetSizes >> 2;
        int returnSize = argAndRetSizes & 3;
        Type returnType = Type.getReturnType(descriptor);
        Type argTypes[] = Type.getArgumentTypes(descriptor);
        int returnLoc = argSize;
        int execpLoc = returnLoc + returnSize;
        boolean isStatic = (access & ACC_STATIC) != 0;
        Type classObj = Type.getType("L" + internalName + ";");

        mv.visitCode();
        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitTryCatchBlock(l0, l1, l2, null);
        Label l3 = new Label();
        mv.visitLabel(l3);
        if(isStatic){
            mv.visitLdcInsn(classObj);
            returnLoc--;
            execpLoc--;
        }else {
            mv.visitVarInsn(ALOAD, 0); //load 0 if not static, load handle to class object if static
        }
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, monEnter.getName(), monEnter.getDescriptor(), false);

        mv.visitLabel(l0);

        if(!isStatic) {
            mv.visitVarInsn(ALOAD, 0);
        }

        //load any other arguments
        loadAllArgumentsToTheStack(isStatic, descriptor, mv);

        if(isStatic){
            mv.visitMethodInsn(INVOKESTATIC, internalName, "$JMVX$sync$" + name, descriptor, false);
        }else{
            mv.visitMethodInsn(INVOKEVIRTUAL, internalName, "$JMVX$sync$" + name, descriptor, false);
        }

        saveReturnVal(returnType, returnLoc, mv);

        mv.visitLabel(l1);

        if(isStatic){
            mv.visitLdcInsn(classObj);
        }else {
            mv.visitVarInsn(ALOAD, 0); //load 0 if not static, load handle to class object if static
        }
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, monExit.getName(), monExit.getDescriptor(), false);

        loadAndReturnVal(returnType, returnLoc, mv);

        mv.visitLabel(l2);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
        mv.visitVarInsn(ASTORE, execpLoc);
        if(isStatic){
            mv.visitLdcInsn(classObj);
        }else {
            mv.visitVarInsn(ALOAD, 0);
        }
        mv.visitMethodInsn(INVOKESTATIC, JMVX_RUNTIME_INTERNAL_NAME, monExit.getName(), monExit.getDescriptor(), false);
        mv.visitVarInsn(ALOAD, execpLoc);
        mv.visitInsn(ATHROW);

        Label l7 = new Label();
        mv.visitLabel(l7);
        int m = 0; //used to track location of locals on the stack
        if(!isStatic) {
            mv.visitLocalVariable("this", "L" + internalName + ";", null, l3, l7, 0);
            m = 1;
        }

        for(int i = 0; i < argTypes.length; i++){
            mv.visitLocalVariable(JMVXRuntime.Prefix + "v" + Integer.toString(i + 1), argTypes[i].getDescriptor(), null, l3, l7, m);
            m += argTypes[i].getSize();
        }

        if(returnType.getSort() != Type.VOID){
            mv.visitLocalVariable(JMVXRuntime.Prefix + "ret", Type.getReturnType(descriptor).getDescriptor(), null, l3, l7, returnLoc);
        }
        //allocates extra space if static b/c I assume a "this"
        mv.visitMaxs(argSize + returnSize + 1, argTypes.length + 1);
        mv.visitEnd();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        //Skip when: not enabled, or not sync'ed, or native
        if(!enabled || (access & ACC_SYNCHRONIZED) == 0 || (access & ACC_NATIVE) != 0 || name.contains("loadClass"))
            return super.visitMethod(access, name, descriptor, signature, exceptions);

        //now we need to rename the method and create a new one!
        int newAccess = access - ACC_SYNCHRONIZED;
        try {
            addWrappedMethod(newAccess, name, descriptor, signature, exceptions);
        } catch (NoSuchMethodException e) {
            log.trace("Failed to wrap method...");
            e.printStackTrace();
        }
        //modify access to the old function so that it is private
        if((newAccess & ACC_PUBLIC) != 0){
            newAccess -= ACC_PUBLIC;
        }else if((newAccess & ACC_PROTECTED) != 0){
            newAccess -= ACC_PROTECTED;
        }
        newAccess = newAccess | ACC_PRIVATE; //add private flag
        return super.visitMethod(newAccess, "$JMVX$sync$" + name, descriptor, signature, exceptions);
    }
}
