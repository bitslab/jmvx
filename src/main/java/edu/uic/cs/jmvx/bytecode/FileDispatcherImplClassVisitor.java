package edu.uic.cs.jmvx.bytecode;

import edu.uic.cs.jmvx.runtime.JMVXFileDispatcherImpl;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.util.Optional;

public class FileDispatcherImplClassVisitor extends RenameClassVisitor{
    private static Class<?> FILE_DISPATCHER_IMPL_CLASS;
    private static Type FILE_DISPATCHER_IMPL_TYPE;
    private static Type JMVX_FILE_DISPATCHER_TYPE = Type.getType(JMVXFileDispatcherImpl.class);

    static {
        try {
            FILE_DISPATCHER_IMPL_CLASS = Class.forName("sun.nio.ch.FileDispatcherImpl", false, FileDispatcherImplClassVisitor.class.getClassLoader());
            FILE_DISPATCHER_IMPL_TYPE = Type.getType(FILE_DISPATCHER_IMPL_CLASS);
        }catch (Exception e){
            throw new Error("Failed to get type for FileDispatcherImpl", e);
        }
    }


    public FileDispatcherImplClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor) {
        super(loader, classVisitor, FILE_DISPATCHER_IMPL_TYPE, JMVXFileDispatcherImpl.class);
    }

    @Override
    protected boolean isType(Type t) {
        return t.equals(FILE_DISPATCHER_IMPL_TYPE);
    }

    @Override
    protected void generateMethod(String name, String descriptor) {
        /*alters type of first param when calling back to JMVX runtime
        e.g. instead of calling
        JMVXRuntime.read(FileDispatcherImpl impl, ....)
        it will call
        JMVXRuntime.read(JMVXFileDispatcherImpl impl, ...)

        This saves us from two things,
        1) removes a cast in JMVXRuntime (e.g. see JMVXRuntime(OutputStream o, ....) for an example with the cast)
        2) Prevents us from altering access of FileDispatcherImpl (this is package private and would need to be made public)
            We still have access to underlying methods via added code to support the JMVXFileDispatcherImpl interface
         */
        TYPE = JMVX_FILE_DISPATCHER_TYPE;
        super.generateMethod(name, descriptor);
        TYPE = FILE_DISPATCHER_IMPL_TYPE;
    }
}
