package edu.uic.cs.jmvx.runtime.strategy;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BigLockReentrantStrategy extends ReentrantStrategy {
    private final Lock lock = new ReentrantLock();

    public BigLockReentrantStrategy(JMVXStrategy strategy) {
        super(strategy);
    }

    @Override
    public void enter() {
        this.lock.lock();
        try {
            super.enter();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void exit() {
        this.lock.lock();
        try {
            super.exit();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    protected void firstEnter() {
        this.lock.lock(); // Lock the lock once again to hold it until the last exit
    }

    @Override
    protected void lastExit() {
        this.lock.unlock(); // Unlock the lock once again to balance firstEnter
    }
}
