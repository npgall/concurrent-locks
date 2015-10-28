/**
 * Copyright 2013 Niall Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.concurentlocks;

import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.*;

/**
 * @author Niall Gallagher
 */
public class LocksTest {

    @Test
    public void testLockAll() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());

        Locks.lockAll(lock1, lock2);
        assertEquals(1, lock1.holdCount().value);
        assertEquals(1, lock2.holdCount().value);
    }

    @Test
    public void testLockAll_Rollback() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()) {
            @Override
            public void lock() {
                throw new RuntimeException();
            }
        };
        Exception expected = null;
        try {
            Locks.lockAll(lock1, lock2);
        }
        catch (RuntimeException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test
    public void testLockInterruptiblyAll() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());

        Locks.lockInterruptiblyAll(lock1, lock2);
        assertEquals(1, lock1.holdCount().value);
        assertEquals(1, lock2.holdCount().value);
    }

    @Test
    public void testLockInterruptiblyAll_RollbackOnRuntimeException() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()) {
            @Override
            public void lockInterruptibly() throws InterruptedException {
                throw new RuntimeException();
            }
        };
        Exception expected = null;
        try {
            Locks.lockInterruptiblyAll(lock1, lock2);
        }
        catch (RuntimeException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test
    public void testLockInterruptiblyAll_RollbackOnInterruptedException() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()) {
            @Override
            public void lockInterruptibly() throws InterruptedException {
                throw new InterruptedException();
            }
        };
        Exception expected = null;
        try {
            Locks.lockInterruptiblyAll(lock1, lock2);
        }
        catch (InterruptedException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test
    public void testTryLockAll() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());

        boolean acquired = Locks.tryLockAll(lock1, lock2);
        assertTrue(acquired);
        assertEquals(1, lock1.holdCount().value);
        assertEquals(1, lock2.holdCount().value);
    }

    @Test
    public void testTryLockAll_RollbackOnFailure() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()) {
            @Override
            public boolean tryLock() {
                return false;
            }
        };

        boolean acquired = Locks.tryLockAll(lock1, lock2);
        assertFalse(acquired);
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test
    public void testTryLockAll_RollbackOnException() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()) {
            @Override
            public boolean tryLock() {
                throw new RuntimeException();
            }
        };

        Exception expected = null;
        try {
            Locks.tryLockAll(lock1, lock2);
        }
        catch (RuntimeException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test
    public void testTryLockAllWithTimeout() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock());

        boolean acquired = Locks.tryLockAll(1L, TimeUnit.MILLISECONDS, lock1, lock2);
        assertTrue(acquired);
        assertEquals(1, lock1.holdCount().value);
        assertEquals(1, lock2.holdCount().value);
    }

    @Test
    public void testTryLockAllWithTimeout_RollbackOnFailure() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()) {
            @Override
            public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                return false;
            }
        };

        boolean acquired = Locks.tryLockAll(1L, TimeUnit.MILLISECONDS, lock1, lock2);
        assertFalse(acquired);
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test
    public void testTryLockAllWithTimeout_RollbackOnRuntimeException() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()) {
            @Override
            public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                throw new RuntimeException();
            }
        };

        Exception expected = null;
        try {
            Locks.tryLockAll(1L, TimeUnit.MILLISECONDS, lock1, lock2);
        }
        catch (RuntimeException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test
    public void testTryLockAllWithTimeout_RollbackOnInterruptedException() throws Exception {
        HoldCountLock lock1 = new HoldCountLock(new ReentrantLock());
        HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()) {
            @Override
            public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                throw new InterruptedException();
            }
        };

        Exception expected = null;
        try {
            Locks.tryLockAll(1L, TimeUnit.MILLISECONDS, lock1, lock2);
        }
        catch (InterruptedException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertEquals(0, lock1.holdCount().value);
        assertEquals(0, lock2.holdCount().value);
    }

    @Test
    public void testTryLockAllWithTimeout_TimeoutSpansAllAcquisitions() throws Exception {
        // Acquire a lock in a background thread so that it is never available to foreground thread...
        final ReentrantLock unavailableLock = new ReentrantLock();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        assertTrue(executor.submit(new ReentrantReadWriteUpdateLockTest.TryLockTask(unavailableLock)).get());
        try {
            // Set up lock1.
            // Should acquire lock1, even though in doing so we use more time than timeout allows...
            final AtomicBoolean lock1WasInitiallyAcquired = new AtomicBoolean();
            final AtomicLong timeoutSuppliedToLock1 = new AtomicLong(Long.MIN_VALUE);
            HoldCountLock lock1 = new HoldCountLock(new ReentrantLock()){
                @Override
                public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                    timeoutSuppliedToLock1.set(unit.toNanos(time)); // A positive timeout should be supplied to this lock
                    boolean acquired = super.tryLock(time, unit);
                    lock1WasInitiallyAcquired.set(acquired);
                    Thread.sleep(1000); // Sleep for longer than overall timeout
                    return acquired;
                }
            };

            // Set up lock2.
            // Should acquire lock2, because even though timeout expired it's available without blocking...
            final AtomicBoolean lock2WasInitiallyAcquired = new AtomicBoolean();
            final AtomicLong timeoutSuppliedToLock2 = new AtomicLong(Long.MAX_VALUE);
            HoldCountLock lock2 = new HoldCountLock(new ReentrantLock()){
                @Override
                public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                    timeoutSuppliedToLock2.set(unit.toNanos(time)); // A negative timeout should be supplied to this lock
                    boolean acquired = super.tryLock(time, unit); // Should succeed as no need to wait
                    lock2WasInitiallyAcquired.set(acquired);
                    return acquired;
                }
            };

            // Set up lock3.
            // Should fail to acquire lock3, because it's unavailable and it can't block as timeout expired...
            final AtomicBoolean lock3WasInitiallyAcquired = new AtomicBoolean();
            final AtomicLong timeoutSuppliedToLock3 = new AtomicLong(Long.MAX_VALUE);
            HoldCountLock lock3 = new HoldCountLock(unavailableLock) {
                @Override
                public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                    timeoutSuppliedToLock3.set(unit.toNanos(time)); // A negative timeout should be supplied to this lock
                    boolean acquired = super.tryLock(time, unit); // Should fail as can't wait due to timeout <= 0
                    lock3WasInitiallyAcquired.set(acquired);
                    return acquired;
                }
            };

            // Now actually try to acquire the locks...
            boolean acquired = Locks.tryLockAll(1L, TimeUnit.NANOSECONDS, lock1, lock2, lock3);
            // Should fail...
            assertFalse(acquired);

            // Lock 1 should have been supplied a positive timeout and it should have been acquired...
            assertTrue(timeoutSuppliedToLock1.get() > 0);
            assertTrue(lock1WasInitiallyAcquired.get());

            // Lock 2 should have been supplied a negative timeout as lock1 used all of the available time,
            // but acquisition should have succeeded anyway because it was uncontended and available without blocking...
            assertTrue(timeoutSuppliedToLock2.get() < 0);
            assertTrue(lock2WasInitiallyAcquired.get());

            // Lock 3 should have been supplied a negative timeout as lock1 used all of the available time,
            // and acquisition should have failed because it was unavailable and could not block due to timeout expired...
            assertTrue(timeoutSuppliedToLock3.get() < 0);
            assertFalse(lock3WasInitiallyAcquired.get());

            // All locks acquired should have been rolled back due to failure to acquire lock3...
            assertEquals(0, lock1.holdCount().value);
            assertEquals(0, lock2.holdCount().value);
            assertEquals(0, lock3.holdCount().value);
        }
        finally {
            executor.shutdown();
        }
    }

    @Test
    public void testConstructor() {
        assertNotNull(new Locks());
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
