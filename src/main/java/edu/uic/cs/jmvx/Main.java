package edu.uic.cs.jmvx;

import edu.uic.cs.jmvx.coordinate.Coordinator;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar jmvx.jar -instrument [source] [deploy] {additional-classpath-entries}");
            System.exit(-1);
        }
        if (args[0].equals("-instrument")) {
            if (args.length < 3) {
                System.err.println("Usage: java -jar jmvx.jar -instrument [source] [deploy] {additional-classpath-entries}");
                System.exit(-1);
            }
            new Instrumenter()._main(args[1], args[2], Arrays.stream(args).skip(2).toArray(String[]::new));
        } else if (args[0].equals("-coordinator")) {
            Coordinator._main();
        } else {
            System.err.println("Usage: java -jar jmvx.jar -instrument [source] [deploy] {additional-classpath-entries}");
            System.exit(-1);
        }
    }
}
