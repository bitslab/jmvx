package edu.uic.cs.jmvx.runtime;

import edu.uic.cs.jmvx.runtime.strategy.Follower;
import edu.uic.cs.jmvx.runtime.strategy.HandlerStatus;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface DivergenceHandler {

    static Logger log = Logger.getLogger(DivergenceHandler.class.getName());

    public default Object handleDivergence() {
        //TODO: remove stack trace logging after H2 (lib and client/server) works
        //synchronize so that thread's don't interleave a divergence report
        synchronized (DivergenceHandler.class) {
            Thread currentThread = Thread.currentThread();
            log.warn("external divergence handler called in thread " + currentThread.getName());
            for (StackTraceElement e : currentThread.getStackTrace()) {
                log.warn("\t " + e.toString());
            }
        }
        throw new DivergenceError();
    }

    public default HandlerStatus<Integer> handleDivergence(List<StackTraceElement> stack) {
        log.warn("external divergence handler called.");
        JMVXRuntime.enter();
        throw new DivergenceError();
    }

    public default HandlerStatus<Integer> handleReadDivergence(List<StackTraceElement> stack, Follower.Metadata metadata) {
        log.warn("external read divergence handler called.");
        JMVXRuntime.enter();
        throw new DivergenceError();
    }

    public default HandlerStatus<Integer> handleWriteDivergence(List<StackTraceElement> stack, Follower.Metadata metadata) {
        log.warn("external write divergence handler called.");
        JMVXRuntime.enter();
        throw new DivergenceError();
    }
}