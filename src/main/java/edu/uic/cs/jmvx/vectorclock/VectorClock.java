package edu.uic.cs.jmvx.vectorclock;

import com.sun.org.apache.xpath.internal.operations.Bool;

import java.util.*;
import java.util.stream.LongStream;
import java.util.Arrays;
import java.util.Comparator;

public class VectorClock {

    private Map<Long, Integer> tidToIndex = new HashMap<>();
    protected long[] clock = {0L};//new long[1]; //clinit slot is index 0
    private volatile long inClinit = -1L; //which thread is redirected to clinit slot

    protected int getIdX(){
        long tid = Thread.currentThread().getId();
        if(inClinit == tid){
            return 0;
        }
        synchronized (this) {
            return tidToIndex.get(tid);
        }
    }

    public void setInClinit(boolean val){
        long tid = Thread.currentThread().getId();
        if(val){
            inClinit = tid;
        }else{
            if(inClinit == tid)
                inClinit = -1L; //no thread in clinit
            else
                throw new Error("SHOULDN'T HAPPEN");
            //else someone else stole the value
        }
    }

    public synchronized int size() {
        return clock.length;
    }

    public synchronized int registerNewThread(int idx) {
        long tid = Thread.currentThread().getId();

        //int idx = tidToIndex.size();

        tidToIndex.put(tid, idx);

        //if we get an index that is larger than tidToIndex.size()+1,
        //we will need to add arbitrarily many longs
        int min_length = idx + 1; //minumum length of the clock to manage threads 0 to idx
        if(min_length > this.clock.length) {
            long[] toConcat = new long[min_length - this.clock.length];
            Arrays.fill(toConcat, 0);
            clock = LongStream.concat(Arrays.stream(clock), LongStream.of(toConcat)).toArray();
        }
        return idx;
    }

    public synchronized long[] increment(long[] result) {
        long tid = Thread.currentThread().getId();
        int idx = getIdX(); //tidToIndex.get(tid);

        long val = clock[idx] + 1;
        clock[idx] = val;

        if (result != null) {
            if (result.length < clock.length)
                result = new long[clock.length];

            System.arraycopy(clock, 0, result, 0, clock.length);
        }

        return result;
    }

    public boolean sync(long[] clock) {
        return sync(clock, false);
    }

    public boolean sync(long[] clock, boolean keepTrying) {
        long tid = Thread.currentThread().getId();

        int idx = getIdX(); //sync method

        //TODO register the clock we are sync'ing to so we can implement a function to see which thread should go next

        /*synchronized (this) {
            idx = tidToIndex.get(tid);
        }*/

        boolean yield = false;

        tryagain: while (true) {
            if(yield){
                Thread.yield();
                yield = false;
            }
            synchronized (this) {
                if (clock.length > this.clock.length) {
                    if (keepTrying) {
                        yield = true;
                        continue tryagain;
                    }else
                        return false;
                }

                for (int i = 0; i < clock.length; i++) {
                    if (idx == i) {
                        if (this.clock[i] + 1 != clock[i]) {
                            if (keepTrying) {
                                yield = true;
                                continue tryagain;
                            }else
                                return false;
                        }
                    } else if (this.clock[i] != clock[i]) {
                        if (keepTrying) {
                            yield = true;
                            continue tryagain;
                        }
                        else
                            return false;
                    }
                }
                return true;
            }
        }
    }


    public long[] diffClock(long[] clock){
	    long[] res = new long[this.clock.length];
	    for(int i = 0; i < this.clock.length; i++){
		    res[i] = clock[i] - this.clock[i];
	    }
	    return res;
    }
}
