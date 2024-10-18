package edu.uic.cs.jmvx.switchroles;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/*
    Simply creates a bunch of threads. Half of the threads read from a file and the other half write to another file.
    The threads are started gradually, with a tiny interval between each start.
    This way, at any point in execution, some threads will be created, but not started.

    TODO:
    Add something to indicate if role switching was successful.
 */

public class SwitchRolesTest {

    private static final int NUMBER_OF_THREADS=1000;
    private static final String READ_FILE_NAME = "ReadFile.txt";
    private static final String WRITE_FILE_NAME = System.getenv("JMVX_DIR")+"/src/test/java/edu/uic/cs/jmvx/switchroles/res/WriteFile.txt";
    private static final String READ_FILE_CONTENTS = "Leroy Jenkins.";

    public static void main(String[] args) {

        //Clear existing contents of WriteFile
        setupWriteFile();

        DoSomething[] threads = new DoSomething[NUMBER_OF_THREADS];

        System.out.println("Creating " + threads.length + " threads.");
        System.out.println("Switch roles using coordinator.");

        //Initialize threads:
        for (int i = 0; i < threads.length; i++)
            threads[i] = new DoSomething(i);

        for (int i = 0; i < threads.length; i++) {
            threads[i].run();
            try {
                Thread.sleep(10); //This will cause a gap between initializing a thread and executing it.
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if ((i+1) % 100 == 0)
                System.out.println("Started executing " + (i + 1) + " threads.");
        }
    }

    private static void setupWriteFile() {
        try {
            File writeFile = new File(WRITE_FILE_NAME);
            FileWriter fileWriter = new FileWriter(writeFile);
            fileWriter.write("");
            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class DoSomething implements Runnable {
        int threadNumber;

        public DoSomething(int threadNumber) {
            this.threadNumber = threadNumber;
        }

        public void run() {
            int task = this.threadNumber % 2;
            //noinspection EnhancedSwitchMigration
            switch (task) {
                case 0:
                    String readData = readFromFile();
                    if (!READ_FILE_CONTENTS.equals(readData))
                        System.err.println("Invalid text read: " + readData);
                    break;
                case 1:
                    String toWrite = "Thread number " + this.threadNumber + " is writing.\n";
                    writeToFile(toWrite);
                    break;
                default:
                    throw new Error("Impossible case.");
            }
        }

        String readFromFile() {
            ClassLoader classLoader = getClass().getClassLoader();
            InputStream is = classLoader.getResourceAsStream(READ_FILE_NAME);

            if (is == null)
                throw new Error(READ_FILE_NAME + " not found.");

            StringBuilder fileContents = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null)
                    fileContents.append(line);
                reader.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return fileContents.toString();
        }

        void writeToFile(String data) {
            try {
                File writeFile = new File(WRITE_FILE_NAME);
                BufferedWriter writer = new BufferedWriter(new FileWriter(writeFile, true));
                writer.append(data);
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}