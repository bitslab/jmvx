package edu.uic.cs.jmvx.runtime;

import java.io.IOException;

public interface JMVXZipFile {
    public void $JMVX$initIDs();
    public long $JMVX$getEntry(long jzfile, byte[] name, boolean addSlash);
    public void $JMVX$freeEntry(long jzfile, long jzentry);
    public long $JMVX$getNextEntry(long jzfile, int i);
    public void $JMVX$close(long jzfile);
    public long $JMVX$open(String name, int mode, long lastModified, boolean usemmap) throws IOException;
    public int $JMVX$getTotal(long jzfile);
    public boolean $JMVX$startsWithLOC(long jzfile);
    public int $JMVX$read(long jzfile, long jzentry, long pos, byte[] b, int off, int len);
    public long $JMVX$getEntryTime(long jzentry);
    public long $JMVX$getEntryCrc(long jzentry);
    public long $JMVX$getEntryCSize(long jzentry);
    public long $JMVX$getEntrySize(long jzentry);
    public int $JMVX$getEntryMethod(long jzentry);
    public int $JMVX$getEntryFlag(long jzentry);
    public byte[] $JMVX$getCommentBytes(long jzfile);
    public byte[] $JMVX$getEntryBytes(long jzentry, int type);
    public String $JMVX$getZipMessage(long jzfile);
}
