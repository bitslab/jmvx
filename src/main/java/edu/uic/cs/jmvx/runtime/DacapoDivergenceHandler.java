package edu.uic.cs.jmvx.runtime;

import edu.uic.cs.jmvx.runtime.strategy.Follower;
import edu.uic.cs.jmvx.runtime.strategy.HandlerStatus;
import org.apache.log4j.Logger;

import java.io.InputStream;
import java.util.List;

public class DacapoDivergenceHandler implements DivergenceHandler {

    private static Logger log = Logger.getLogger(DivergenceHandler.class.getName());
    private int readCount = 0;

    public HandlerStatus<Integer> handleReadDivergence(List<StackTraceElement> stack, Follower.Metadata metadata) {
        log.warn("external read divergence handler called.");
        if (readCount == 0) {
            try {
                // on the first read, call the actual read() method using the inputstream that was passed in
                int r = JMVXRuntime.read((InputStream) metadata.is, metadata.buffer, 0, 10);
                readCount++;
                return new HandlerStatus<>(DivergenceStatus.STATUS.OK, new Integer(r));
            } catch (Exception e) {
                System.out.println(e);
                throw new RuntimeException(e);
            }
        } else {
            // on the second read, don't do anything, just log a message
            log.debug("Divergence tolerated!!!");
        }
        return new HandlerStatus<>(DivergenceStatus.STATUS.OK, new Integer(0));
    }

    public HandlerStatus<Integer> handleWriteDivergence(List<StackTraceElement> stack, Follower.Metadata metadata) {
        log.warn("external write divergence handler called.");

        if (stack.size() < 10) return DivergenceHandler.super.handleWriteDivergence(stack, metadata);

        StackTraceElement stackFrame = stack.get(17);

        // When there is a write divergence at the end of batik, append an extra string to the follower's
        // version of the message to show that this method ran successfully
        // This has to be done at the byte level
        if (stackFrame.getClassName() == "org.dacapo.harness.Callback"  && stackFrame.getMethodName() == "complete") {
            String extra = "and divergence handled ";
            byte[] bytes = extra.getBytes();
            for (int i = 0; i < bytes.length; i++) {
                metadata.buffer[i + metadata.length] = bytes[i];
            }
            metadata.length += bytes.length;

            return new HandlerStatus<>(DivergenceStatus.STATUS.OK, new Integer(0)); //CoreDumpCallback.memDump
        }else if(stackFrame.getClassName().equals("CoreDumpCallback") && stackFrame.getMethodName().equals("memDump")){
	    //for MX heap info
            return new HandlerStatus<>(DivergenceStatus.STATUS.OK, 0);
	}

        return DivergenceHandler.super.handleWriteDivergence(stack, metadata);
    }
}
