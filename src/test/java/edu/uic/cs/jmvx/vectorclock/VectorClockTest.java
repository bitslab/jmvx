package edu.uic.cs.jmvx.vectorclock;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VectorClockTest {
    private static final String WORKLOAD_STRING = "Hello world!";
    private static final int N = 100;

    @Test
    public void firstLeadersThenFollowers() {

        AtomicBoolean sync = new AtomicBoolean(false);
        VectorClock leaders_clock = new VectorClock();
        VectorClock followers_clock = new VectorClock();

        long[][] clocks = new long[N * WORKLOAD_STRING.length()][];

        char[] expected = new char[N * WORKLOAD_STRING.length()];
        char[] actual   = new char[N * WORKLOAD_STRING.length()];

        AtomicInteger last = new AtomicInteger(0);

        Runnable[] leaders   = new Runnable[N];
        Runnable[] followers = new Runnable[N];
        Thread[]   threads   = new Thread[N];

        // Initialize clocks
        {
            for (int i = 0; i < clocks.length; i++)
                clocks[i] = new long[N];
        }

        // Build N leaders
        for (int i = 0 ; i < N ; i++) {
            int I = i; //avoiding a compile time error
            leaders[i] = () -> {
                // Register this thread
                leaders_clock.registerNewThread(I);

                // Wait for all other leaders to be ready
                waitToBeReady(sync);

                // Do the workload
                for (char c : "Hello world".toCharArray()) {
                    // Race for the lock
                    synchronized (sync) {
                        // Got the lock, increment the vector clock to say that
                        leaders_clock.increment(clocks[last.get()]);
                        // Make the change
                        expected[last.getAndIncrement()] = c;
                    }
                }
            };

            // Register this leader has a thread
            threads[i] = new Thread(leaders[i]);
        }

        runThreadsToCompletion(sync, threads);

        last.set(0);


        // Build N followers
        for (int i = 0 ; i < N ; i++) {
            int I = i; //avoiding a compile time error
            followers[i] = () -> {
                // Register this thread
                followers_clock.registerNewThread(I);

                // Wait for all other followers to be ready
                waitToBeReady(sync);

                // Run the workload
                for (char c : "Hello world".toCharArray()) {
                    // Wait for our turn
                    while (!followers_clock.sync(clocks[last.get()]));

                    synchronized (sync) {
                        // Increment the vector clock AFTER getting the lock
                        // Doing this may release the next thread and must be done AFTER getting the lock
                        // Otherwise another thread may get the lock before we do
                        followers_clock.increment(null);
                        // Perform the change
                        actual[last.getAndIncrement()] = c;
                    }
                }
            };

            // Register this follower as a thread
            threads[i] = new Thread(followers[i]);
        }

        runThreadsToCompletion(sync, threads);

        String expString = new String(expected);
        String actString = new String(actual);

        Assert.assertEquals(expString, actString);
    }

    private static void waitToBeReady(AtomicBoolean sync) {
        synchronized (sync) {
            while (!sync.get()) {
                try {
                    sync.wait();
                } catch (InterruptedException e) { }
            }
        }
    }

    private static void runThreadsToCompletion(AtomicBoolean sync, Thread[] threads) {
        for (int i = 0 ; i < N ; i++) {
            threads[i].start();
        }

        synchronized (sync) {
            sync.set(true);
            sync.notifyAll();
        }

        for (int i = 0 ; i < N ; i++) {
            while (threads[i].isAlive()) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) { }
            }
        }

    }
}
