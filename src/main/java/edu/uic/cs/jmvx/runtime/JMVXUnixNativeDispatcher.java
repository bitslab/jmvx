package edu.uic.cs.jmvx.runtime;
/*
We need to separate the instrumenter and JMVX
If we do that, we can build with our changes in mind
e.g., we make change the access on these classes so we can
use them directly
import sun.nio.fs.UnixException;
import sun.nio.fs.UnixFileAttributes;
import sun.nio.fs.UnixPath; */

import sun.nio.fs.UnixPath;
import sun.nio.fs.UnixException;
import sun.nio.fs.UnixFileAttributes;

public class JMVXUnixNativeDispatcher {
    public static int $JMVX$open(UnixPath path, int flags, int mode) throws UnixException{
        return JMVXRuntime.getStrategy().open(path, flags, mode);
    }
    public static void $JMVX$stat(UnixPath path, UnixFileAttributes attrs) throws UnixException{
        JMVXRuntime.getStrategy().stat(path, attrs);
    }
    public static void $JMVX$lstat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        JMVXRuntime.getStrategy().lstat(path, attrs);
    }
    public static long $JMVX$opendir(UnixPath path) throws UnixException{
        return JMVXRuntime.getStrategy().opendir(path);
    }
    public static byte[] $JMVX$readdir(long dir) throws UnixException{
        return JMVXRuntime.getStrategy().readdir(dir);
    }
    public static void $JMVX$access(UnixPath path, int amode) throws UnixException {
        JMVXRuntime.getStrategy().access(path, amode);
    }
    public static void $JMVX$closedir(long dir) throws UnixException{
        JMVXRuntime.getStrategy().closedir(dir);
    }
    public static void $JMVX$mkdir(UnixPath path, int mode) throws UnixException {
        JMVXRuntime.getStrategy().mkdir(path, mode);
    }
    public static int $JMVX$dup(int fd) throws UnixException{
        return JMVXRuntime.getStrategy().dup(fd);
    }
    public static long $JMVX$fdopendir(int dfd) throws UnixException {
        return JMVXRuntime.getStrategy().fdopendir(dfd);
    }
    public static byte[] $JMVX$realpath(UnixPath path) throws UnixException {
        return JMVXRuntime.getStrategy().realpath(path);
    }
    /*Maybe should instrument?
    void $JMVX$rename(Path var0, Path var1) throws Exception;
    void $JMVX$rmdir(Path var0) throws Exception;
    byte[] $JMVX$realpath(Path var0) throws Exception;
    int $JMVX$read(int var0, long var1, int var3) throws Exception;
    int $JMVX$write(int var0, long var1, int var3) throws Exception;*/
}
