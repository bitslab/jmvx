package sun.nio.fs;

import java.io.Serializable;

public class UnixFileAttributes implements Serializable {
    public int st_mode;
    public long st_ino;
    public long st_dev;
    public long st_rdev;
    public int st_nlink;
    public int st_uid;
    public int st_gid;
    public long st_size;
    public long st_atime_sec;
    public long st_atime_nsec;
    public long st_mtime_sec;
    public long st_mtime_nsec;
    public long st_ctime_sec;
    public long st_ctime_nsec;
    public long st_birthtime_sec;
}
