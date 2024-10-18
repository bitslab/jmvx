package edu.uic.cs.jmvx.circularbuffer;

import org.junit.Assert;
import org.junit.Test;

public class CircularBufferTests {
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

    private byte[] generateByteArray(int size) {
        byte[] data = new byte[size];
        for(int i = 1; i <= size; i++) {
            data[i-1] = (byte)i;
        }
        return data;
    }

    /**
     * Test to fill the buffer completely.
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│d│d│ . . . . . . │d│d│d│d│d│d│d│d│d│
     * └─┴─┴─┴─┴─┴─┴─┴▲┴▲┴─┴─────────────┴─┴─┴─┴─┴─┴─┴─┴─┴─┘
     *  0             │ └────── readerPosition
     *          writerPosition
     *
     * @throws Exception
     */
    @Test
    public void fillBuffer() throws Exception {
        int size = 4088;
        CircularBuffer bufferOb = new CircularBuffer(size);
        bufferOb.initializeOnBufferData();
        byte[] data = generateByteArray(size - 16);
        Assert.assertTrue(bufferOb.writeData(data, false));
    }


    /**
     * Test to ensure that an empty buffer cannot be read.
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│ │ │ . . . . . . │ │ │ │ │ │ │ │ │ │
     * └─┴─┴─┴─┴─┴─┴─┴▲┴▲┴─┴─────────────┴─┴─┴─┴─┴─┴─┴─┴─┴▲┘
     *  0             │ │                                 │
     *                │ └─── readerPosition               └────── bufferSize - 1
     *           writerPosition
     *
     * @throws Exception
     */
    @Test
    public void readEmptyBuffer() throws Exception {
        CircularBuffer bufferOb = new CircularBuffer(10);
        bufferOb.initializeOnBufferData();
        Assert.assertNull(bufferOb.readData(false));
    }


    /**
     * Test to read the complete data from a full buffer.
     * Reader should return true on successful read and null on failure.
     *
     * Here 'r' represents the bytes that were read.
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│r│r│ . . . . . . │r│r│r│r│r│r│r│r│r│
     * └─┴─┴─┴─┴─┴─┴─┴▲┴▲┴─┴─────────────┴─┴─┴─┴─┴─┴─┴─┴─┴▲┘
     *  0             │ │                                 │
     *                │ └─── readerPosition               └────── bufferSize - 1
     *          writerPosition
     *
     * @throws Exception
     */
    @Test
    public void readFullBuffer() throws Exception {
        int size = 4088;
        CircularBuffer bufferOb = new CircularBuffer(size);
        bufferOb.initializeOnBufferData();
        byte[] data = generateByteArray((size/2) - 16);
        bufferOb.writeData(data,false);
        bufferOb.writeData(data, false);
        Assert.assertTrue(bufferEquals(data, bufferOb.readData(false)));
        Assert.assertTrue(bufferEquals(data, bufferOb.readData(false)));
        Assert.assertNull(bufferOb.readData(false));
    }


    /**
     * Test to check whether writer can write to a full buffer.
     * Writer should return false on being unable to write.
     *
     * Here 'd' represents unread data.
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│d│d│ . . . . . . │d│d│d│d│d│d│d│d│d│
     * └─┴─┴─┴─┴─┴─┴─┴▲┴▲┴─┴─────────────┴─┴─┴─┴─┴─┴─┴─┴─┴─┘
     *  0             │ └────── readerPosition
     *          writerPosition
     *
     * @throws Exception
     */
    @Test
    public void writeToFullBuffer() throws Exception {
        int size = 4088;
        CircularBuffer bufferOb = new CircularBuffer(size);
        bufferOb.initializeOnBufferData();
        byte[] data = generateByteArray(size - 8);
        bufferOb.writeData(data, false);
        Assert.assertFalse(bufferOb.writeData(data, false));
    }


    /**
     * Test to check whether the writer and reader can wraparound data correctly
     * given that the buffer has free space at the beginning.
     *
     * The initial and final reader position are one ahead of the initial and final
     * writer positions respectively.
     *
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬──────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│d│d│d│d│d│d│. . . . . │d│d│d│d│d│d│d│d│d│
     * └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴▲┴─────────▲┴─┴─┴─┴─┴─┴─┴─┴─┴─┘
     *  0                         │          └────── InitialWriterPosition
     *                     FinalWriterPosition
     *
     * @throws Exception
     */
    @Test
    public void wraparoundData() throws Exception {
        int size = 4088;
        CircularBuffer bufferOb = new CircularBuffer(size);
        bufferOb.initializeOnBufferData();
        byte[] data1 = generateByteArray(size/2);
        byte[] data2 = generateByteArray(size/2);
        Assert.assertTrue(bufferOb.writeData(data1, false));
        bufferOb.readData(false);
        bufferOb.writeData(data2, false);
        Assert.assertTrue(bufferEquals(data2, bufferOb.readData(false)));
    }


    /**
     * Test to check whether the writer overwrites unread data.
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│r│r│r│r│r│. . . . │d│d│r│r│r│r│r│r│r│r│
     * └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴──▲─────┴─┴▲┴─┴─┴┴─┴─┴─┴─┴─┘
     *                              │        └────── writerPosition
     *                          readerPosition
     *
     * @throws Exception
     */
    @Test
    public void overwriteUnreadData() throws Exception {
        int size =  4088;
        CircularBuffer bufferOb = new CircularBuffer(size);
        bufferOb.initializeOnBufferData();
        byte[] data1 = generateByteArray(size - 56);
        byte[] data2 = generateByteArray(40);

        bufferOb.writeData(data1, false);
        bufferOb.readData(false);
        bufferOb.writeData(data2, false);

        Assert.assertFalse(bufferOb.writeData(data1, false));
        Assert.assertTrue(bufferOb.writeData(data2, false));
    }


    /**
     * Test to check whether the reader knows when all data has been read.
     * Reader should not read ahead into the buffer.
     *
     * Here 'r' represents data that has been read.
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬───────┬─┬─┬─┬─┬─┬─┬───────┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│r│r│ . . . │r│r│r│r│r│r│ . . . │r│r│r│
     * └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴───────┴─┴─┴─┴▲┴▲┴─┴───────┴─┴─┴▲┘
     *  0                                 │ │               └────── bufferSize - 1
     *                                    │ └─── readerPosition
     *                               writerPosition
     *
     * @throws Exception
     */
    @Test
    public void readUnwrittenData() throws Exception {
        int size = 4088;
        CircularBuffer bufferOb = new CircularBuffer(size);
        bufferOb.initializeOnBufferData();
        byte[] data1 = generateByteArray(size/2);

        bufferOb.writeData(data1, false);
        Assert.assertTrue(bufferEquals(data1, bufferOb.readData(false)));
        Assert.assertNull(bufferOb.readData(false));

    }


    /**
     * Test to check whether the writer evaluates the available size
     * before performing a write.
     *
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│r│r│r│r│r│. . . . │r│r│r│r│d│d│d│d│d│d│
     * └─┴─┴─┴─┴─┴─┴─┴▲┴─┴─┴─┴─┴─┴────────┴─┴─┴─┴─┴▲┴─┴─┴─┴─┴─┘
     *                │                            └────── readerPosition
     *          writerPosition
     *
     * @throws Exception
     */
    @Test
    public void checkOverflow() throws Exception {
        int size = 4088;
        CircularBuffer bufferOb = new CircularBuffer(size);
        bufferOb.initializeOnBufferData();
        byte[] data1 = generateByteArray((size/2) - 16);
        byte[] data2 = generateByteArray((size/2) + 8);
        Assert.assertTrue(bufferOb.writeData(data1, false));
        Assert.assertTrue(bufferOb.writeData(data1, false));
        bufferOb.readData(false);
        Assert.assertFalse(bufferOb.writeData(data2, false));
        Assert.assertTrue(bufferOb.writeData(data1, false));

    }

    /**
     * Test to check whether data of size zero can be written to the buffer.
     * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
     * │s│s│s│s│s│s│s│s│ │ │ . . . . . . │ │ │ │ │ │ │ │ │ │
     * └─┴─┴─┴─┴─┴─┴─┴▲┴▲┴─┴─────────────┴─┴─┴─┴─┴─┴─┴─┴─┴▲┘
     *  0             │ │                                 │
     *                │ └─── readerPosition               └────── bufferSize - 1
     *           writerPosition
     *
     * @throws Exception
     */
    @Test
    public void failZeroWrite() throws Exception {
        CircularBuffer bufferOb = new CircularBuffer(16);
        bufferOb.initializeOnBufferData();
        byte[] data1 = {};
        Assert.assertFalse(bufferOb.writeData(data1, false));
    }

}
