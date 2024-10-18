package edu.uic.cs.jmvx.runtime;

import sun.nio.fs.UnixFileAttributes;

import java.lang.reflect.Field;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;

public class UnixFileAttributesUtil {
    /**
     * Hand coded for now, should add a copy constructor in the bytecode or something
     * @param src
     * @param dest
     */
    public static void copy(UnixFileAttributes src, UnixFileAttributes dest){
        dest.st_mode = src.st_mode;
        dest.st_ino = src.st_ino;
        dest.st_dev = src.st_dev;
        dest.st_rdev = src.st_rdev;
        dest.st_nlink = src.st_nlink;
        dest.st_uid = src.st_uid;
        dest.st_gid = src.st_gid;
        dest.st_size = src.st_size;
        dest.st_atime_sec = src.st_atime_sec;
        dest.st_atime_nsec = src.st_atime_nsec;
        dest.st_mtime_sec = src.st_mtime_sec;
        dest.st_mtime_nsec = src.st_mtime_nsec;
        dest.st_ctime_sec = src.st_ctime_sec;
        dest.st_ctime_nsec = src.st_ctime_nsec;
        dest.st_birthtime_sec = src.st_birthtime_sec;
        //bottom three are not serializable yet?
        /*dest.owner = src.owner; //null
        dest.group = src.group; //null
        dest.key = src.key; //null*/
    }
}
