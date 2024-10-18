package sun.nio.fs;

import java.io.Serializable;
import java.nio.file.Path;

//need to make it extend AbstractPath?
public class UnixPath implements Serializable {

    public static UnixPath toUnixPath(Path path){
        throw new Error("Should not get here");
    }
}
