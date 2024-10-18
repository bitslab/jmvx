package edu.uic.cs.jmvx.circularbuffer;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import sun.misc.Unsafe;
import sun.nio.ch.FileChannelImpl;

import java.io.RandomAccessFile;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;

public class CircularBuffer {

    private static final Method mmap;
    private static final Method unmmap;
    private static final String mappedMemoryLocation = System.getProperty("mappedMemoryLocation", "/dev/shm/");

    public static final int byteOffset, longOffset;
    private long bufferSize, bufferAddress, bufferCapacity;
    private long readerPosition, writerPosition;

    private static int DATA_LENGTH_SIZE_BYTES = 8;

    private static int BUFFER_FREE_SIZE_BYTES = 8;

    private static final int WRITER_START_POSITION = 7;

    private static final int READER_START_POSITION = 8;

    private static long casFails = 0;

    private static Method getMethod(Class<?> cls, String name, Class<?>... params) throws Exception {
        Method m = cls.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private void mapBufferToFile(boolean leader) {
        try {
            String mappedFile = this.mappedMemoryLocation + Thread.currentThread().getName().replace("/", "_");
            File mappedFileObject = new File(mappedFile);
            if (leader) mappedFileObject.createNewFile();
            final RandomAccessFile backingFile = new RandomAccessFile(mappedFile, "rw");
            if (leader) backingFile.setLength(this.bufferSize);
            final FileChannel ch = backingFile.getChannel();
            this.bufferAddress = (long) mmap.invoke(ch, 1, 0L, this.bufferSize);
            if (leader) {
                JMVXRuntime.unsafe.putLong(bufferAddress, this.bufferCapacity);
                JMVXRuntime.unsafe.putLong(bufferAddress + BUFFER_FREE_SIZE_BYTES, 0L);
            }
            ch.close();
            backingFile.close();
        }
        catch (IOException | ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    public void deleteFileOnDisk() {
        String mappedFile = this.mappedMemoryLocation + Thread.currentThread().getName().replace("/", "_");
        File mappedFileObject = new File(mappedFile);
        mappedFileObject.delete();
    }

    public void initializeOnBufferData() {
        JMVXRuntime.unsafe.putLong(this.bufferAddress, this.bufferCapacity);
        JMVXRuntime.unsafe.putLong(this.bufferAddress + BUFFER_FREE_SIZE_BYTES, 0L);
    }

    private static long roundTo4096(long i) {
        return (i + 0xfffL) & ~0xfffL;
    }

    static {
        try {
            byteOffset = JMVXRuntime.unsafe.arrayBaseOffset(byte[].class);
            longOffset = JMVXRuntime.unsafe.arrayBaseOffset(long[].class);
            mmap = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
            unmmap = getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        } catch (SecurityException e) {
            throw new Error(e);
        } catch (IllegalArgumentException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (Exception e){
            throw new Error(e);
        }
    }

    public CircularBuffer(long capacity) {
        this(capacity, true);
    }

    public CircularBuffer(long capacity, boolean leader) {
        this.bufferSize = roundTo4096(capacity + DATA_LENGTH_SIZE_BYTES);
        this.bufferCapacity = this.bufferSize - DATA_LENGTH_SIZE_BYTES;

        /**
         * The writer points to the location in the array that it just wrote to.
         * When a write occurs, the writer starts writing data from the next position
         * in the array.
         * The reader points to the location in the array that it is going to read.
         * When a read occurs, the reader starts reading the data from the position
         * it is currently pointing to in the array.
         * The first 8 bytes of the buffer are used to store the free size on the buffer.
         * Thus, initially the writer is at position 7 so that it can start writing from
         * position 8, after the free size bytes.
         *
         * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
         * │s│s│s│s│s│s│s│s│ │ │ . . . . . . │ │ │ │ │ │ │ │ │ │
         * └─┴─┴─┴─┴─┴─┴─┴▲┴▲┴─┴─────────────┴─┴─┴─┴─┴─┴─┴─┴─┴▲┘
         *  0             │ │                                 │
         *                │ └─── READER_START_POSITION        └────── bufferSize - 1
         *       WRITER_START_POSITION
         *
         *           [Figure 1: General Buffer Layout]
         */
        this.writerPosition =  WRITER_START_POSITION;
        this.readerPosition = READER_START_POSITION;
        mapBufferToFile(leader);
    }

    public static long getCASFailCount() {
        return casFails;
    }

    private long readFreeSize() {
        return(JMVXRuntime.unsafe.getLongVolatile(null, bufferAddress));
    }

    private long incrementFreeSize(long orig, long increment) {

        while(!JMVXRuntime.unsafe.compareAndSwapLong(null, bufferAddress, orig, orig + increment)) {
            casFails++;
            orig = readFreeSize();
        }

        return (orig + increment);
    }

    public boolean bufferIsEmpty() {
        long freeSize = readFreeSize();
        if((this.bufferCapacity) == freeSize) {
            return true;
        }
        return false;
    }

    /**
     * Function to get the index of importatnt locations on the buffer.
     * These positions are necessary to validate wheter a write operation
     * can be successfully executed on the buffer and also necessary
     * during the write operation
     * @param dataLength length of the data being written onto the buffer
     * @return an array of longs with the important locations.
     * The mapping between the indices and the location are:
     * long[0] : writeStartPosition
     * long[1] : writerEndPosition
     * long[2] : requiredSize
     */
    private long[] getWriteOperationPointers(long dataLength) {
        long writeStartPosition, writerEndPosition, requiredSize, bytesSkipped, padding;

        bytesSkipped = 0;

        /**
         * In the diagram below 's' represents the bytes used for size of data
         * 'd' is the data bytes, and the 'n' bytes are size of the next write.
         * The writer writes 0 to the 'n' bytes. During the next write, the size
         * of that write is written to these 'n' bytes. The 'n' bytes of the current
         * write become the 's' bytes of the next write.
         * ┌─┬─┬─┬─┬───────┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬───────┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
         * │ │ │ │ │.......│ │s│s│s│s│s│s│s│s│d│d│d│d│d│d│.......│d│d│n│n│n│n│n│n│n│n│ │ │ │ │ │
         * └─┴─┴─┴─┴───────┘▲└─┴─┴─┴─┴─┴─┴─┘▲└─┴─┴─┴─┴─┴─┴───────┴─┘▲└─┴─┴─┴─┴─┴─┴─┘▲└─┴─┴─┴─┴─┘
         *  0               │               │                       │               │
         *                  │               │                       │               │
         *            writerPosition        │                 writerEndPosition     │
         *                           writeStartPosition                   nextWriteSizeEndPosition
         *
         *                                   [Figure 2]
         */

        writeStartPosition = writerPosition + DATA_LENGTH_SIZE_BYTES;
        writerEndPosition = writeStartPosition + dataLength;

        /**
         * Move the whole data and size of next write to the start of the buffer
         * if there is not enough contiguous room available on the buffer for it.
         * bytesSkipped is the number of bytes skipped over at the end of the buffer.
         * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
         * │s│s│s│s│s│s│s│s│ │ │ . . . . . . │d│d│d│d│d│d│d│d│d│d d d d d d d
         * └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─────────────┴─┴─┴─┴─┴─┴─┴─┴─┘▲┘            ▲
         *  0             7                                   │             │
         *                                                    │             └────── writerEndPosition
         *                                               bufferSize - 1
         *
         *                       [Figure 3]
         */
        if(writerEndPosition >= this.bufferSize) {
            bytesSkipped = this.bufferSize - 1 - writeStartPosition;
            writeStartPosition = WRITER_START_POSITION;
            writerEndPosition = writeStartPosition + dataLength;
        }

        /**
         * Move writer to start of the buffer if not enough space
         * available for writing size of next write at the end.
         * bytesSkipped is the number of bytes skipped over at
         * the end of the buffer.
         *
         * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
         * │s│s│s│s│s│s│s│s│ │ │ . . . . . . │ │ │ │ │ │e│e│e│e│
         * └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─────────────┴─┴─┴─┴─┘▲└─┴─┴─┘▲┘
         *  0             7                           │       │
         *                                            │       └────── bufferSize - 1
         *                                     writerEndPosition
         *
         *                       [Figure 4]
         */
        if(writerEndPosition + DATA_LENGTH_SIZE_BYTES >= this.bufferSize) {
            bytesSkipped += this.bufferSize - 1 - writerEndPosition;
            writerEndPosition = WRITER_START_POSITION;
            padding = 0;
        }
        else {
            padding = (8 - ((writerEndPosition + 1) % 8)) % 8;
            writerEndPosition += padding;
        }

        /**
         * Total size required to complete the write operation is:
         * dataLength - length of data in bytes to be written
         * DATA_LENGTH_SIZE_BYTES - 8 bytes to represent the size of
         * the data that was just written. We also write another 8
         * bytes (size of next write) of zeros at the end of the data.
         * bytesSkipped - any extra bytes skipped at the end of the buffer
         */
        requiredSize = dataLength + bytesSkipped + (2 * DATA_LENGTH_SIZE_BYTES) + padding;

        long[] bufferWriteOperationPointers = {writeStartPosition, writerEndPosition, requiredSize};
        return bufferWriteOperationPointers;
    }

    public boolean writeData(byte[] data, boolean blocking) {
        long freeSize, dataLength, writeStartPosition, writerEndPosition, requiredSize;

        dataLength = data.length;
//        if(dataLength > this.bufferCapacity) {
//            throw new Error("Error! Size of write operation greater than buffer size");
//        }

        long[] bufferWriteOperationPointers = getWriteOperationPointers(dataLength);
        writeStartPosition = bufferWriteOperationPointers[0];
        writerEndPosition = bufferWriteOperationPointers[1];
        requiredSize = bufferWriteOperationPointers[2];
        freeSize = readFreeSize();

        /**
         * Loop if not enough space, until a read happens freeing space on the buffer
         */
        while(freeSize < requiredSize && blocking == true) {
            freeSize = readFreeSize();
        }

        if(freeSize >= requiredSize) {

            /**
             * Copy the data onto the buffer and update the free size.
             * The reduction in free size does not include the size of the next write.
             */
            JMVXRuntime.unsafe.copyMemory(data, byteOffset, null, (this.bufferAddress + writeStartPosition + 1), dataLength);

            /**
             * Write the zeros to the size of next write. If the reader reads 0, this means
             * that the data is not stale and the reader can loop here until data is available.
             */
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerEndPosition + 1, 0L);

            freeSize = incrementFreeSize(freeSize, -(requiredSize - DATA_LENGTH_SIZE_BYTES));

            /**
             * Write the size of the entry. This needs to be volatile because the
             * reader may be looping here waiting for data to be available. This
             * creates a happens-before between writing all the data and the reader
             * reading it. The reader will be able to see the data from this point onwards.
             */
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerPosition + 1, dataLength);

            /**
             * Update the writer position
             */
            writerPosition =  writerEndPosition;

            return true;
        }
        return false;
    }

    public boolean writeData(long[] data, boolean blocking) {
        long freeSize, dataLength, writeStartPosition, writerEndPosition, requiredSize;

        dataLength = data.length * Long.BYTES;
//        if(dataLength > this.bufferCapacity) {
//            throw new Error("Error! Size of write operation greater than buffer size");
//        }

        long[] bufferWriteOperationPointers = getWriteOperationPointers(dataLength);
        writeStartPosition = bufferWriteOperationPointers[0];
        writerEndPosition = bufferWriteOperationPointers[1];
        requiredSize = bufferWriteOperationPointers[2];
        freeSize = readFreeSize();

        while(freeSize < requiredSize && blocking == true) {
            freeSize = readFreeSize();
        }

        if(freeSize >= requiredSize) {
            JMVXRuntime.unsafe.copyMemory(data, longOffset, null, (this.bufferAddress + writeStartPosition + 1), dataLength);
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerEndPosition + 1, 0L);
            freeSize = incrementFreeSize(freeSize, -(requiredSize - DATA_LENGTH_SIZE_BYTES));
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerPosition + 1, dataLength);
            writerPosition =  writerEndPosition;

            return true;
        }
        return false;
    }

    public boolean writeData(int data, boolean blocking) {
        // Luis:  This method has a bug, ints are not recovered as written with readData
        if ("1".equals("1"))
            throw new Error();

        long freeSize, dataLength, writeStartPosition, writerEndPosition, requiredSize;

        dataLength = Integer.BYTES;
//        if(dataLength > this.bufferCapacity) {
//            throw new Error("Error! Size of write operation greater than buffer size");
//        }

        long[] bufferWriteOperationPointers = getWriteOperationPointers(dataLength);
        writeStartPosition = bufferWriteOperationPointers[0];
        writerEndPosition = bufferWriteOperationPointers[1];
        requiredSize = bufferWriteOperationPointers[2];
        freeSize = readFreeSize();

        while(freeSize < requiredSize && blocking == true) {
            freeSize = readFreeSize();
        }

        if(freeSize >= requiredSize) {
            JMVXRuntime.unsafe.copyMemory(data, 0, null, (this.bufferAddress + writeStartPosition + 1), dataLength);
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerEndPosition + 1, 0L);
            freeSize = incrementFreeSize(freeSize, -(requiredSize - DATA_LENGTH_SIZE_BYTES));
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerPosition + 1, dataLength);
            writerPosition =  writerEndPosition;

            return true;
        }
        return false;
    }

    /**
     * Function to specifically handle the write() function of Leader that
     * takes a byte array, integer offset and integer length as parameters.
     * NOTE: Do not use this function to write 'len' bytes of a byte array
     *       'data' starting at offset 'off' onto the circular buffer.
     * @param data byte array passed to write() as a parameter
     * @param off integer offset passed to write() as a parameter
     * @param len integer length passed to write() as a parameter
     * @param blocking
     * @return
     */
    public boolean writeData(byte[] data, int off, int len, boolean blocking) {
        long freeSize, dataLength, writeStartPosition, writerEndPosition, requiredSize;

        dataLength = Long.BYTES + data.length + Integer.BYTES + Integer.BYTES;
//        if(dataLength > this.bufferCapacity) {
//            throw new Error("Error! Size of write operation greater than buffer size");
//        }

        long[] bufferWriteOperationPointers = getWriteOperationPointers(dataLength);
        writeStartPosition = bufferWriteOperationPointers[0];
        writerEndPosition = bufferWriteOperationPointers[1];
        requiredSize = bufferWriteOperationPointers[2];
        freeSize = readFreeSize();

        while(freeSize < requiredSize && blocking == true) {
            freeSize = readFreeSize();
        }

        if(freeSize >= requiredSize) {
            JMVXRuntime.unsafe.putLong(null, this.bufferAddress + writeStartPosition + 1, data.length);
            JMVXRuntime.unsafe.copyMemory(data, byteOffset, null, this.bufferAddress + writeStartPosition + 1 + Long.BYTES, data.length);
            JMVXRuntime.unsafe.putInt(null, this.bufferAddress + writeStartPosition + 1 + Long.BYTES + data.length, off);
            JMVXRuntime.unsafe.putInt(null, this.bufferAddress + writeStartPosition + 1 + Long.BYTES + data.length + Integer.BYTES, len);

            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerEndPosition + 1, 0L);
            freeSize = incrementFreeSize(freeSize, -(requiredSize - DATA_LENGTH_SIZE_BYTES));
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerPosition + 1, dataLength);
            writerPosition =  writerEndPosition;

            return true;
        }
        return false;
    }

    /**
     * Function to handle the write operation of the
     * output stream backed by the circular buffer.
     * Function blocks until there is enough space on the
     * buffer to complete the write.
     * @param data the byte array to sent via the output stream
     * @param off offset from where the data must be written
     * @param len number of bytes to be written
     */
    public void writeData(byte[] data, int off, int len) {
        long freeSize, dataLength, writeStartPosition, writerEndPosition, requiredSize;

        dataLength = len;
//        if(dataLength > this.bufferCapacity) {
//            throw new Error("Error! Size of write operation greater than buffer size");
//        }

        long[] bufferWriteOperationPointers = getWriteOperationPointers(dataLength);
        writeStartPosition = bufferWriteOperationPointers[0];
        writerEndPosition = bufferWriteOperationPointers[1];
        requiredSize = bufferWriteOperationPointers[2];
        freeSize = readFreeSize();

        while(freeSize < requiredSize) {
            freeSize = readFreeSize();
        }

        if(freeSize >= requiredSize) {
            JMVXRuntime.unsafe.copyMemory(data, byteOffset + (off * Unsafe.ARRAY_BYTE_INDEX_SCALE), null, this.bufferAddress + writeStartPosition + 1, dataLength);
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerEndPosition + 1, 0L);
            freeSize = incrementFreeSize(freeSize, -(requiredSize - DATA_LENGTH_SIZE_BYTES));
            JMVXRuntime.unsafe.putLongVolatile(null, this.bufferAddress + writerPosition + 1, dataLength);
            writerPosition =  writerEndPosition;
        }
    }

    public byte[] readData(boolean blocking) {
        byte[] data;
        long dataLength, freeSize, readStartPosition, readerEndPosition, padding;
        int skippedBytes = 0;

        freeSize = readFreeSize();

        /**
         * Move reader to start of buffer if not enough space at the end
         * to read the size of next read. Writer would have written the
         * size at the start of the buffer.
         * skippedBytes is the number of extra bytes at the end of the buffer
         * that the writer had skipped over.
         *
         * ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─────────────┬─┬─┬─┬─┬─┬─┬─┬─┬─┐
         * │s│s│s│s│s│s│s│s│d│d│ . . . . . . │ │ │ │ │e│e│e│e│e│
         * └─┴─┴─┴─┴─┴─┴─┴─┴▲┴─┴─────────────┴─┴─┴─┴─┴▲┴─┴─┴─┘▲┘
         *  0             7 │                         │       │
         *                  │                         │       └────── bufferSize - 1
         *             newReaderPosition       oldReaderPosition
         *
         *                       [Figure 5]
         *
         */
        if((readerPosition +  DATA_LENGTH_SIZE_BYTES) > this.bufferSize) {
            skippedBytes = (int)(this.bufferSize - readerPosition);
            freeSize = incrementFreeSize(freeSize, skippedBytes);
            readerPosition = READER_START_POSITION;
            skippedBytes = 0;
        }
        else {
            padding = (8 - (readerPosition % 8)) % 8;
            freeSize = incrementFreeSize(freeSize, padding);
            readerPosition += padding;
        }

        /**
         * Get the length for the data to be read. A dataLength value of 0
         * indicates that the data is not stale and that it is the end of writes.
         * The reader loops here in blocking mode till data is available.
         */
        dataLength = 0;
        while(dataLength == 0) {
            dataLength  = JMVXRuntime.unsafe.getLongVolatile(null, bufferAddress + readerPosition);
            if(!blocking && dataLength == 0) {
                return null;
            }
        }

//        if((dataLength < 0) || (dataLength > this.bufferCapacity)) {
//            throw new Error("Invalid read size. Cannot read " + dataLength + " bytes!");
//        }

        /**
         * In the diagram below 's' represents the bytes used
         * for size of data and 'd' is the data bytes
         * ┌─┬─┬─┬─┬───────┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬───────┬─┬─┬─┬─┬─┬─┬─┐
         * │ │ │ │ │.......│s│s│s│s│s│s│s│s│d│d│d│d│d│d│d│.......│d│ │ │ │ │ │ │
         * └─┴─┴─┴─┴───────┘▲└─┴─┴─┴─┴─┴─┴─┘▲└─┴─┴─┴─┴─┴─┴───────┴─┘▲└─┴─┴─┴─┴─┘
         *  0               │               │                       │
         *                  │               │                       │
         *            readerPosition        │               readerEndPosition
         *                           readStartPosition
         *
         *                                     [Figure 6]
         */
        readStartPosition = readerPosition + DATA_LENGTH_SIZE_BYTES;
        readerEndPosition = readStartPosition + dataLength;

        /**
         * The length of data from the reader's current position extends
         * beyond the end of the buffer. In this case, the writer would have
         * written the whole data at the start of the buffer.
         */
        if(readerEndPosition > this.bufferSize) {
            skippedBytes = (int)(this.bufferSize - readStartPosition);
            readStartPosition = READER_START_POSITION;
            readerEndPosition = readStartPosition + dataLength;
        }

        /**
         * Byte array of size dataLength which holds the read data.
         */
        data = new byte[(int)dataLength];

        /**
         * Start of actual read operations.
         * Read data from buffer into our private buffer that we will return.
         */
        JMVXRuntime.unsafe.copyMemory(null, bufferAddress + readStartPosition, data, byteOffset, dataLength);

        /**
         * We set the size to zero so that the entry can be
         * reused by the writer, we've read the data at this point.
         * This needs to be volatile because the writer may be stuck here on
         * a full buffer waiting for room to be made available.
         */
        JMVXRuntime.unsafe.putLongVolatile(null, bufferAddress + readerPosition, 0L);

        /**
         * Reset reader to the start of the buffer
         * if data was written exactly till the last byte
         */
        if(readerEndPosition == this.bufferSize) {
            readerEndPosition = READER_START_POSITION;
        }

        readerPosition = readerEndPosition;
        freeSize = incrementFreeSize(freeSize, dataLength + DATA_LENGTH_SIZE_BYTES  + skippedBytes);

        return data;
    }

    /***
     * Function to use when memory to copy data into is already allocated.
     * @param b the byte array into which the data is copied
     * @param off offset into byte array b, starting at which data is copied
     * @param len length of the byte array b
     * @return returns the number of bytes read during this operation
     */
    public int readData(byte[] b, int off, int len) {
        long dataLength, freeSize, readStartPosition, readerEndPosition, padding;
        int skippedBytes = 0;

        freeSize = readFreeSize();

        if((readerPosition +  DATA_LENGTH_SIZE_BYTES) > this.bufferSize) {
            skippedBytes = (int)(this.bufferSize - readerPosition);
            freeSize = incrementFreeSize(freeSize, skippedBytes);
            readerPosition = READER_START_POSITION;
            skippedBytes = 0;
        }
        else {
            padding = (8 - (readerPosition % 8)) % 8;
            freeSize = incrementFreeSize(freeSize, padding);
            readerPosition += padding;
        }

        dataLength = 0;
        while(dataLength == 0) {
            dataLength = JMVXRuntime.unsafe.getLongVolatile(null, bufferAddress + readerPosition);
            if(dataLength > len) {
                throw new Error("Insufficient buffer size. Cannot complete the read operation!");
            }
//	    if(dataLength-16 > len) {
//              throw new Error("Insufficient buffer size. Cannot complete the read operation!");
//          }
        }

//        if((dataLength < 0) || (dataLength > this.bufferCapacity)) {
//            throw new Error("Invalid read size. Cannot read " + dataLength + " bytes!");
//        }

        readStartPosition = readerPosition + DATA_LENGTH_SIZE_BYTES;
        readerEndPosition = readStartPosition + dataLength;

        if(readerEndPosition > this.bufferSize) {
            skippedBytes = (int)(this.bufferSize - readStartPosition);
            readStartPosition = READER_START_POSITION;
            readerEndPosition = readStartPosition + dataLength;
        }

        JMVXRuntime.unsafe.copyMemory(null, bufferAddress + readStartPosition, b, byteOffset + off, dataLength);
        JMVXRuntime.unsafe.putLongVolatile(null, bufferAddress + readerPosition, 0L);

        if(readerEndPosition == this.bufferSize) {
            readerEndPosition = READER_START_POSITION;
        }

        readerPosition = readerEndPosition;
        freeSize = incrementFreeSize(freeSize, dataLength + DATA_LENGTH_SIZE_BYTES  + skippedBytes);

        return (int)dataLength;
    }
}
