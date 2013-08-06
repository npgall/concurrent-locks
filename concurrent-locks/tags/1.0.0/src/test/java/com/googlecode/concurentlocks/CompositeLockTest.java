package com.googlecode.concurentlocks;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.*;

/**
 * @author Niall Gallagher
 */
public class CompositeLockTest {

    @Test
    public void testLock() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());
        CompositeLock compositeLock = new CompositeLock(lock1, lock2);
        compositeLock.lock();
        assertEquals(1, lock1.holdCount().value);
        assertEquals(1, lock2.holdCount().value);
    }

    @Test
    public void testLockInterruptibly() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());
        CompositeLock compositeLock = new CompositeLock(lock1, lock2);
        compositeLock.lockInterruptibly();
        assertEquals(1, lock1.holdCount().value);
        assertEquals(1, lock2.holdCount().value);
    }

    @Test
    public void testTryLock() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());
        CompositeLock compositeLock = new CompositeLock(lock1, lock2);
        boolean acquired = compositeLock.tryLock();
        assertTrue(acquired);
        assertEquals(1, lock1.holdCount().value);
        assertEquals(1, lock2.holdCount().value);
    }

    @Test
    public void testTryLockWithTimeout() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());
        CompositeLock compositeLock = new CompositeLock(lock1, lock2);
        boolean acquired = compositeLock.tryLock(1, TimeUnit.NANOSECONDS);
        assertTrue(acquired);
        assertEquals(1, lock1.holdCount().value);
        assertEquals(1, lock2.holdCount().value);
    }

    @Test
    public void testUnlock() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());
        CompositeLock compositeLock = new CompositeLock(lock1, lock2);
        compositeLock.lock();
        compositeLock.unlock();
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNewCondition() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());
        CompositeLock compositeLock = new CompositeLock(lock1, lock2);
        compositeLock.newCondition();
    }

    static class HoldCountLock extends ReentrantReadWriteUpdateLock.HoldCountLock {

        public HoldCountLock(Lock backingLock) {
            super(backingLock);
        }

        @Override
        void validatePreconditions() {
            // No op
        }
    }
}
