package edu.uic.cs.jmvx;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import edu.uic.cs.jmvx.runtime.JMVXInputStream;

public class Utils {
    // from: https://stackoverflow.com/questions/4485128/how-do-i-convert-long-to-byte-and-back-in-java
    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static byte[] getChecksum(byte[] b, CRC32 crc32) {
        crc32.reset();
        crc32.update(b);
        return longToBytes(crc32.getValue());
    }

    public static byte[] getChecksum(byte[] b, int off, int len, CRC32 crc32) {
        crc32.reset();
        crc32.update(b, off, len);
        return longToBytes(crc32.getValue());
    }

    public static boolean isInputStreamFromDisk(JMVXInputStream is) {
        if (is instanceof java.io.FileInputStream) {
            return true;
        }
        return false;
    }

}