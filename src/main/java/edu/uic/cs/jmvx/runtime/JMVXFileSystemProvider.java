package edu.uic.cs.jmvx.runtime;

import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.Path;

public interface JMVXFileSystemProvider {
    void $JMVX$checkAccess(Path path, AccessMode... modes) throws IOException;

    void $JMVX$copy(Path source, Path target, CopyOption... options) throws IOException;
}
