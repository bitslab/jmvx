package edu.uic.cs.jmvx.divergence;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.prefs.Preferences;

public class DivergenceTest {


    public static void main(String[] args) throws IOException {

        boolean isLeader = args[0].contains("Leader");
        InputStream inputStream = Preferences.class.getResourceAsStream("/index.html");

        if (isLeader) {
            inputStream.available();
            inputStream.available();
            inputStream.available();
        } else {
            inputStream.available();
            inputStream.available();
        }
        System.out.println("Ending divergence test");
        System.exit(1);
    }

    private static void read(InputStream inputStream) throws IOException {
        inputStream.read();
    }
}