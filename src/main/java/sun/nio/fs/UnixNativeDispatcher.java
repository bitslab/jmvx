package sun.nio.fs;

public class UnixNativeDispatcher {
    public static int open(UnixPath path, int flags, int mode) throws UnixException {
        throw new Error("Should never be called");
    }

    public static void stat(UnixPath p, UnixFileAttributes a) throws UnixException {
        throw new Error("Should never be called");
    }

    
    public static void lstat(UnixPath path, UnixFileAttributes attrs) throws UnixException {
        throw new Error("Should never be called");
    }
    
    public static long opendir(UnixPath path) throws UnixException{
        throw new Error("Should never be called");
    }
    
    public static byte[] readdir(long dir) throws UnixException{
        throw new Error("Should never be called");
    }
    
    public static void access(UnixPath path, int amode) throws UnixException {
        throw new Error("Should never be called");
    }
    
    public static void closedir(long dir) throws UnixException{
        throw new Error("Should never be called");
    }
    
    public static void mkdir(UnixPath path, int mode) throws UnixException {
        throw new Error("Should never be called");
    }

    public static int dup(int fd) throws UnixException {
        throw new Error("Should never be called");
    }

    public static long fdopendir(int dfd) throws UnixException {
        throw new Error("Should never be called");
    }

    public static byte[] realpath(UnixPath path) throws UnixException {
        throw new Error("Should never be called");
    }
}