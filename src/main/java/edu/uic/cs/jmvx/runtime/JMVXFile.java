package edu.uic.cs.jmvx.runtime;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public interface JMVXFile {
    public boolean $JMVX$canRead();

    public boolean $JMVX$canWrite();

    public boolean $JMVX$createNewFile() throws IOException;

    public boolean $JMVX$delete();

    public boolean $JMVX$exists();

    public File $JMVX$getAbsoluteFile();

    public String $JMVX$getAbsolutePath();

    public File $JMVX$getCanonicalFile() throws IOException;

    public String $JMVX$getCanonicalPath() throws IOException;

    public String $JMVX$getName();

    public File $JMVX$getParentFile();

    public boolean $JMVX$isDirectory();

    public long $JMVX$length();

    public String[] $JMVX$list();

    public String[] $JMVX$list(FilenameFilter filter);

    public File[] $JMVX$listFiles();

    public File[] $JMVX$listFiles(FilenameFilter filter);

    public boolean $JMVX$mkdir();

    public boolean $JMVX$mkdirs();

    public boolean $JMVX$renameTo(File dest);

    public boolean $JMVX$setReadOnly();

    public URL $JMVX$toURL() throws MalformedURLException;

    public long $JMVX$lastModified();

    //eventually leads to [file system] fs.getBooleanAttributes call
    //which will lead to a native method call...
    public boolean $JMVX$isFile();
}
