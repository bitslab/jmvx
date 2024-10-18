package edu.uic.cs.jmvx.runtime;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class FileOutputStream$$FileOutputStream extends FileOutputStream {

    public FileOutputStream$$FileOutputStream(String name) throws FileNotFoundException {
        super(JMVXRuntime.fileOutputStream(name));
    }

    public FileOutputStream$$FileOutputStream(String name, boolean append) throws FileNotFoundException {
        super(JMVXRuntime.fileOutputStream(name), JMVXRuntime.fileOutputStream(append));
    }

    public FileOutputStream$$FileOutputStream(File file) throws FileNotFoundException {
        super(JMVXRuntime.fileOutputStream(file));
    }

    public FileOutputStream$$FileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(JMVXRuntime.fileOutputStream(file), JMVXRuntime.fileOutputStream(append));
    }

    public FileOutputStream$$FileOutputStream(FileDescriptor fdObj) {
        super(JMVXRuntime.fileOutputStream(fdObj));
    }
}
