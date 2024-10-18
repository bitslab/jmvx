package edu.uic.cs.jmvx.divergence;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.prefs.Preferences;

public class DivergenceTest2 {


    public static void main(String[] args) throws IOException {

        boolean isLeader = args[0].contains("Leader");
        boolean isFollower = !isLeader;
        InputStream is = Preferences.class.getResource("/index.html").openStream();

        byte[] bytes = new byte[10];
        Arrays.fill(bytes, (byte)0);

        if (isLeader) {
            // the leader reads 10 bytes
            is.read(bytes, 0, 10);
        } else {
            // the follower reads 5 + 5 bytes
            is.read(bytes, 0, 5);
            is.read(bytes, 5, 5);
        }
        System.out.println(new String(bytes));
        System.exit(1);
    }

    private static void read(InputStream inputStream) throws IOException {
        inputStream.read();
    }
}