package edu.uic.cs.jmvx.runtime;

import sun.misc.Signal;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class JMVXNativeDetector {

    private static AtomicBoolean inited = new AtomicBoolean(false);
    private static AtomicBoolean raised = new AtomicBoolean(false);
    private static Lock l = new ReentrantLock();
    public static Map<StackTraceElement, List<Trace>> found;
    private static StackTraceElement[] last;
    private static File outFile;
    private static ObjectOutputStream output;
    private static int count = 0;
    private static final int logAfter = 10_000;

    static {
    }

    public static void registerHandler() {
        if (inited.getAndSet(true))
            return;

        JMVXRuntime.enter();
	try{
	    outFile = new File("natives.log");
	    output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
	}catch(IOException e){
	    throw new Error("Failed to setup logging");
	}finally{
            JMVXRuntime.exit();
	}

        found = new ConcurrentHashMap<>();
        Signal.handle(new Signal("USR2"), signal -> {
            raised.set(true);
        });

	Signal.handle(new Signal("INT"), signal -> {
	    logStacks();
	});
    }

    public static void beforeNativeCall() {
        if (inited.get()) {
            l.lock();
            last = new Exception().getStackTrace();
            raised.set(false);
        }
    }
    public static void afterNativeCall() {
        if (inited.get()) {
            if (raised.get()) {
                try {
                    Arrays.stream(last).skip(1).limit(1).forEach(JMVXNativeDetector::saveIfUnique);
                    raised.set(false);
		    //dump traces we've gotten so far
		    /*if(count > logAfter){ //log stacks can only be called once now
	                logStacks();
		    }*/
                } catch (Throwable t) {
                    // Ignore
                    t = t;
                }
            }
            l.unlock();
        }
    }

    private static void saveIfUnique(StackTraceElement key){
        List<Trace> stacks = found.get(key);
        Trace lastTrace = new Trace(last);
        if(stacks != null &&
           stacks.stream().noneMatch((t) -> lastTrace.hash == t.hash)){
                stacks.add(lastTrace);
                count++;
        }else{
                List<Trace> l = new LinkedList<>();
                l.add(lastTrace);
                found.put(key, l);
                count++;
        }
    }

    public static void logStacks(){
        if(output == null)
            return;
        JMVXRuntime.enter();
	try{
	    output.writeObject(found);
	    output.flush();
	    output.reset();
            output.close();
	    
	    found.clear();
	    count = 0;
	}catch(IOException e){
	     e = e;
	}finally{
             JMVXRuntime.exit();
        }
    }

    public static class Trace implements Serializable {
        public StackTraceElement[] stack;
        public final int hash;

        Trace(StackTraceElement[] s){
            stack = s;
            hash = Arrays.hashCode(stack);
        }
    }
}
