package edu.uic.cs.jmvx.md5;

import java.nio.file.*;
import java.security.MessageDigest;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.Arrays;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class MD5Test {

    public static File createFile(int size, String directoryPath) throws IOException {

        File file = new File(Paths.get(directoryPath, "temp-test.bin").toString());
        FileOutputStream fout = new FileOutputStream(file);

        int mb = size;
        Random random = new Random();
        final int oneMB = 1024 * 1024;
        byte[] bytes = new byte[oneMB];
        for (int i=0; i<bytes.length; i++) {
            bytes[i] = (byte)0;
        }
        int totalBytes = mb * oneMB;
        for (int i=0; i<totalBytes; i += oneMB) {
            random.nextBytes(bytes);
            fout.write(bytes);
        }
        fout.close();
        return file;
    }

    public static void md5Test(File file, int bufferLength) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            FileInputStream fs = new FileInputStream(file);
            byte[] b = new byte[bufferLength];

            while (fs.available() > 0) {
                fs.read(b);
                md.update(b);
            }
            byte[] result = md.digest();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static long[] runMd5Test(int reps, int bufferLength, File file) throws IOException {

        final int N = reps;
        long[] execTimes = new long[N];

        for (int i=0; i<N; i++) {
            long startTime = System.nanoTime();
            md5Test(file, bufferLength);
            long stopTime = System.nanoTime();
            execTimes[i] = stopTime - startTime;
        }
        return execTimes;
    }


    public static void main(String args[]) throws IOException {
        if (args.length < 3 || args.length > 4) {
            System.out.println("Usage: MD5Test <file-size> <repetitions> <buffer-length> [<directory-path>]");
            System.exit(1);
        }

        int fileSize = 100; // in MB
        int reps = 5;
        int bufferLength = 8192;
        String directoryPath = "";

        try {
            fileSize = Integer.parseInt(args[0]);
            reps = Integer.parseInt(args[1]);
            bufferLength = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("First 3 arguments must be an integers");
            System.exit(1);
        }

        if (args.length == 4) {
            directoryPath = args[3];
        }

        File f = createFile(fileSize, directoryPath);

        long [] execTimes = runMd5Test(reps, bufferLength, f);

        DescriptiveStatistics stats = new DescriptiveStatistics();

        for (long execTime: execTimes) {
            Long l = new Long(execTime);
            double d = l.doubleValue();
            stats.addValue(d);
        }

        double mean = stats.getMean();
        double std = stats.getStandardDeviation();
        double median = stats.getPercentile(50);

       System.out.println("==================================================================================");
       System.out.println("Time taken to compute the MD5 of a " + fileSize + " MB file:");
       for (int i=0; i<execTimes.length; i++) {
           System.out.println("Execution time " + (i + 1) + ": " + execTimes[i]);
       }
       System.out.println("Average execution time: " + mean);
       System.out.println("Median execution time: " + median);
       System.out.println("Standard deviation of execution time: " + std);
       System.out.println("==================================================================================");

       if (!f.delete()) {
           System.out.println("Couldn't delete file");
       }
    }
}
