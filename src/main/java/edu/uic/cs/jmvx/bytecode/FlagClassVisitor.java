package edu.uic.cs.jmvx.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.Optional;

public abstract class FlagClassVisitor extends RenameClassVisitor{

    private Type flagFor;

    public FlagClassVisitor(Optional<ClassLoader> loader, ClassVisitor classVisitor, Type type, Type flagFor, Class<?> JMVX_class) {
        super(loader, classVisitor, type, JMVX_class);
        this.flagFor = flagFor;
    }

    @Override
    protected void generateMethod(String name, String descriptor) {
        /*
        generate method adds an argument to the front of the method
        that argument is of the type of object we are instrumenting
        so it can be passed in a static call
        However, if I am using a blank interface as a flag, it will add the
        wrong type signature! E.g. SocketInputStream instead of InputStream
        This causes errors down the line (when executing the method)
        because it makes java look for a signature that doesn't exist!
         */
        Type old = TYPE;
        TYPE = this.flagFor;
        super.generateMethod(name, descriptor);
        TYPE = old;
    }
}
