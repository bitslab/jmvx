package edu.uic.cs.jmvx.circularbuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Random;

public class BufferPerformanceTests {
    CircularBuffer buffer;
    long readCount, writeCount, readFails, writeFails, seed;
    ByteArrayOutputStream readArray = new ByteArrayOutputStream();
    ByteArrayOutputStream writeArray = new ByteArrayOutputStream();
    Random rand;
    private static long iterationCount = Long.parseLong(System.getProperty("iterationCount", "100000"));
    private static int bufferCapacity = Integer.parseInt(System.getProperty("bufferCapacity", "4088"));

    public BufferPerformanceTests(int capacity) throws Exception {
        buffer = new CircularBuffer(capacity);
        buffer.initializeOnBufferData();
        this.readCount = 0;
        this.writeCount = 0;
        this.rand = new Random();
    }

    public BufferPerformanceTests(int capacity, long seed) throws Exception {
        buffer = new CircularBuffer(capacity);
        buffer.initializeOnBufferData();
        this.readCount = 0;
        this.writeCount = 0;
        this.rand = new Random(seed);
        this.seed = seed;
    }

    private int getRandomNumber(int min, int max) {
        return this.rand.nextInt((max - min) + 1) + min;
    }

    private byte[] generateRandomData(int size) {
        byte[] data = new byte[size];
        this.rand.nextBytes(data);
        return data;
    }

    private void displayBuffer(byte[] data) {
        int len = data.length;
        for(int i = 0; i < len; i++) {
            System.out.print(data[i] + " ");
        }
        System.out.println();
    }

    private boolean writeDataToBuffer() throws IOException {

        int size;
        byte[] data;

        size = getRandomNumber(0, 40);
        data = generateRandomData(size);
        writeCount++;

        boolean status = buffer.writeData(data, false);
        if(!status) {
            writeFails++;
        }
        else {
            writeArray.write(data);
        }
        return status;

    }

    private boolean readDataFromBuffer() throws IOException {
        boolean readSuccessful = false;
        byte[] data = buffer.readData(false);
        if(data != null) {
            readSuccessful = true;
            readArray.write(data);
        }
        else {
            readFails++;
        }

        readCount++;
        return readSuccessful;
    }

    private boolean verifyData(byte[] readBuffer, byte[] writeBuffer) {
        int len = readBuffer.length;
        boolean status = true;
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
                status = false;
            }
        }
        return status;
    }

    private boolean bufferEquals(byte[] buf1, byte[] buf2) {
        int i, len;
        len = buf1.length;

        for (i = 0; i < len; i++) {
            if (buf1[i] != buf2[i]) {
                return false;
            }
        }
        return true;

    }

    public static void main(String args[]) throws Exception {
        long startTime, stopTime;

        startTime = System.nanoTime();

        BufferPerformanceTests testBuffer = new BufferPerformanceTests(bufferCapacity);

        testBuffer.seed = testBuffer.rand.nextLong();
        testBuffer.rand.setSeed(testBuffer.seed);
        System.out.println("Random seed is " + testBuffer.seed);
        for(int i = 0; i < iterationCount; i++) {
            if(testBuffer.rand.nextDouble() >= 0.5) {
                testBuffer.writeDataToBuffer();
            }
            else {
                testBuffer.readDataFromBuffer();
            }
        }

        stopTime = System.nanoTime();
        Duration duration = Duration.ofNanos(stopTime - startTime);

        byte[] readByteArray = testBuffer.readArray.toByteArray();
        byte[] writeByteArray = testBuffer.writeArray.toByteArray();
        if(!testBuffer.verifyData(readByteArray, writeByteArray)) {
            System.out.println("Data does not match!");
        }

        System.out.println("Read Count is " + testBuffer.readCount);
        System.out.println("Write Count is " + testBuffer.writeCount);
        System.out.println("Read Fails is " + testBuffer.readFails);
        System.out.println("Write Fails is " + testBuffer.writeFails);
        System.out.println("Total execution time is " + duration);
    }

}
