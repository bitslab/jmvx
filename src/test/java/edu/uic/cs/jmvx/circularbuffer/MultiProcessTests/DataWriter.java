package edu.uic.cs.jmvx.circularbuffer.MultiProcessTests;

import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;

import java.time.Duration;
import java.util.Random;

public class DataWriter {

    Random rand;
    long seed, writeCount, operationCount;
    private static int capacity = Integer.parseInt(System.getProperty("bufferCapacity", "4088"));

    public DataWriter() {
        this.rand = new Random();
        this.writeCount = 0;
        this.operationCount = Long.parseLong(System.getProperty("iterationCount", "100000"));
    }

    public DataWriter(long seed) {
        this.rand =  new Random(seed);
        this.seed = seed;
        this.writeCount = 0;
        this.operationCount = Long.parseLong(System.getProperty("iterationCount", "100000"));
    }

    private void displayBuffer(byte[] data) {
        int len = data.length;
        for(int i = 0; i < len; i++) {
            System.out.print(data[i] + " ");
        }
        System.out.println();
    }
    private int getRandomNumber(int min, int max) {
        return this.rand.nextInt((max - min) + 1) + min;
    }

    private byte[] generateRandomData(int size) {
        byte[] data = new byte[size];
        this.rand.nextBytes(data);
        return data;
    }

    private boolean writeDataToBuffer(CircularBuffer buffer) {

        int size;
        byte[] data;
        boolean status = false;

        size = getRandomNumber(1, 40);
        data = generateRandomData(size);

        while(!status) {
            status = buffer.writeData(data, true);
        }
        this.writeCount++;
        displayBuffer(data);

        return true;
    }

    private boolean verifyData(byte[] readBuffer, byte[] writeBuffer) {
        int len = readBuffer.length;
        for(int i =0; i < len; i++) {
            if(readBuffer[i] != writeBuffer[i]) {
                System.out.println("Diff at position " + i);
                for(int j = -3; j < 10; j++) {
                    System.out.print(writeBuffer[i + j] + " ");
                }
                System.out.println();
                for(int j = -3; j < 10; j++) {
                    System.out.print(readBuffer[i + j] + " ");
                }
                System.out.println();
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {

        long startTime, stopTime;
        CircularBuffer bufferObject = new CircularBuffer(capacity);
        bufferObject.initializeOnBufferData();
        DataWriter writerObject = new DataWriter();

        writerObject.seed = writerObject.rand.nextLong();
        writerObject.rand.setSeed(writerObject.seed);
        System.out.println("Random seed is " + writerObject.seed);
        System.out.println("Operation count is " + writerObject.operationCount);

        startTime = System.nanoTime();
        while(writerObject.writeCount < writerObject.operationCount) {
            writerObject.writeDataToBuffer(bufferObject);
        }
        stopTime = System.nanoTime();
        Duration duration = Duration.ofNanos(stopTime - startTime);

        System.out.println("Ending writes");
        System.out.println("Total number of writes: " + writerObject.writeCount);
        System.out.println("Start time is " + startTime);
        System.out.println("Number of CAS Failures is " + bufferObject.getCASFailCount());
        System.out.println("Total execution time is " + duration);

    }

}
