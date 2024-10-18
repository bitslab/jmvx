package edu.uic.cs.jmvx.circularbuffer.MultiProcessTests;

import edu.uic.cs.jmvx.circularbuffer.CircularBuffer;

import java.time.Duration;
import java.util.Random;

public class DataReader {

    long readCount, operationCount, seed;
    private static int capacity = Integer.parseInt(System.getProperty("bufferCapacity", "4088"));

    Random rand;

    public DataReader() {
        this.rand = new Random();
        this.readCount = 0;
        this.operationCount = Long.parseLong(System.getProperty("iterationCount", "100000"));
    }

    public DataReader(long seed) {
        this.rand = new Random(seed);
        this.readCount = 0;
        this.operationCount = Long.parseLong(System.getProperty("iterationCount", "100000"));
        this.seed = seed;
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

    private boolean readDataFromBuffer(CircularBuffer buffer) {
        boolean readSuccessful = false;

        while(!readSuccessful) {
            byte[] data = buffer.readData(true);
            if(data != null) {
                readSuccessful = true;
                this.readCount++;
                displayBuffer(data);
            }
        }

        return true;
    }

    private boolean verifyData(byte[] readBuffer, byte[] writeBuffer) {
        int len;
        if(readBuffer.length < writeBuffer.length) {
            len = readBuffer.length;
        }
        else {
            len = writeBuffer.length;
        }
        for(int i =0; i < len; i++) {
            if(readBuffer[i] != writeBuffer[i]) {
                System.out.println("Diff at position " + i);
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {

        long startTime, stopTime;

        CircularBuffer bufferObject = new CircularBuffer(capacity);
        DataReader readerObject = new DataReader();
        System.out.println("Random seed is " + readerObject.seed);
        System.out.println("Operation count is " + readerObject.operationCount);

        startTime = System.nanoTime();
        while(readerObject.readCount < readerObject.operationCount) {
            if(!bufferObject.bufferIsEmpty()) {
                readerObject.readDataFromBuffer(bufferObject);
            }
        }
        stopTime = System.nanoTime();
        Duration duration = Duration.ofNanos(stopTime - startTime);

        System.out.println("Ending reads");
        System.out.println("Total number of reads: " + readerObject.readCount);
        System.out.println("Stop time is " + stopTime);
        System.out.println("Number of CAS Failures is " + bufferObject.getCASFailCount());
        System.out.println("Total execution time is " + duration);
    }
}
