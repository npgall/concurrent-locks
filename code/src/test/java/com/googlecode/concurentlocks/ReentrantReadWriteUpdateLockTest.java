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

import org.junit.*;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.junit.Assert.*;

/**
 * @author Niall Gallagher
 */
public class ReentrantReadWriteUpdateLockTest {

    ExecutorService executor1, executor2;
    ReentrantReadWriteUpdateLock reentrantReadWriteUpdateLock;

    @Before
    public void setUp() throws Exception {
        executor1 = new ThreadPoolExecutor(0, 1, Integer.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        executor2 = new ThreadPoolExecutor(0, 1, Integer.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        reentrantReadWriteUpdateLock = new ReentrantReadWriteUpdateLock();
    }

    @After
    public void tearDown() throws Exception {
        executor1.shutdown();
        executor2.shutdown();
    }

    @Test
    public void testUpdateLockIsExclusive() throws Exception {
        // Acquire the update lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Try to acquire update lock in thread 2, should fail...
        assertFalse(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Release the update lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Try to acquire update lock in thread 2 again, should succeed...
        assertTrue(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Release the update lock in thread 2...
        assertTrue(executor2.submit(new UnlockTask(reentrantReadWriteUpdateLock.updateLock())).get());
    }

    @Test
    public void testUpdateLockAllowsOtherReaders() throws Exception {
        // Acquire the update lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Try to acquire read lock in thread 2, should succeed...
        assertTrue(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Release the update lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Release the read lock in thread 2...
        assertTrue(executor2.submit(new UnlockTask(reentrantReadWriteUpdateLock.readLock())).get());
    }

    @Test
    public void testUpdateLockBlocksOtherWriters() throws Exception {
        // Acquire the update lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Try to acquire write lock in thread 2, should fail...
        assertFalse(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Release the update lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Try to acquire write lock in thread 2 again, should succeed...
        assertTrue(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Release the write lock in thread 2...
        assertTrue(executor2.submit(new UnlockTask(reentrantReadWriteUpdateLock.writeLock())).get());
    }

    @Test
    public void testWriteLockBlocksOtherReaders() throws Exception {
        // Acquire the write lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Try to acquire read lock in thread 2, should fail...
        assertFalse(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Release the write lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Try to acquire read lock in thread 2 again, should succeed...
        assertTrue(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Release the read lock in thread 2...
        assertTrue(executor2.submit(new UnlockTask(reentrantReadWriteUpdateLock.readLock())).get());
    }

    @Test
    public void testUpdateLockUpgradeToWriteLock() throws Exception {
        // Acquire the update lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Try to acquire write lock in thread 1, should succeed...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Release the write lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Release the update lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.updateLock())).get());
    }

    @Test
    public void testPreventedFromAcquiringReadLockIfHoldingUpdateLock() throws Exception {
        // Acquire the update lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        Exception expected = null;
        try {
            // Try to acquire read lock in thread 1, should get exception...
            executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get();

        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalStateException) {
                expected = e;
            }
        }
        assertNotNull(expected);

        // Release the update lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Try to acquire read lock in thread 1 again, should succeed...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Release the read lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.readLock())).get());
    }

    @Test
    public void testPreventedFromAcquiringUpdateLockIfHoldingReadLock() throws Exception {
        // Acquire the read lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        Exception expected = null;
        try {
            // Try to acquire update lock in thread 1, should get exception...
            executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get();

        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalStateException) {
                expected = e;
            }
        }
        assertNotNull(expected);

        // Release the read lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Try to acquire update lock in thread 1 again, should succeed...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.updateLock())).get());

        // Release the update lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.updateLock())).get());
    }

    @Test
    public void testPreventedFromAcquiringWriteLockIfHoldingReadLock() throws Exception {
        // Acquire the read lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        Exception expected = null;
        try {
            // Try to acquire write lock in thread 1, should get exception...
            executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.writeLock())).get();

        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalStateException) {
                expected = e;
            }
        }
        assertNotNull(expected);

        // Release the read lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Try to acquire write lock in thread 1 again, should succeed...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Release the write lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.writeLock())).get());
    }

    @Test
    public void testReadLockHoldCount() throws Exception {
        reentrantReadWriteUpdateLock.readLock().lock();
        int hc1 = ((ReentrantReadWriteUpdateLock.ReadLock)reentrantReadWriteUpdateLock.readLock()).holdCount().value;
        reentrantReadWriteUpdateLock.readLock().unlock();
        int hc2 = ((ReentrantReadWriteUpdateLock.ReadLock)reentrantReadWriteUpdateLock.readLock()).holdCount().value;
        assertEquals(1, hc1);
        assertEquals(0, hc2);
    }

    @Test
    public void testReadLockHoldCount_Interruptibly() throws Exception {
        reentrantReadWriteUpdateLock.readLock().lockInterruptibly();
        int hc1 = ((ReentrantReadWriteUpdateLock.ReadLock)reentrantReadWriteUpdateLock.readLock()).holdCount().value;
        reentrantReadWriteUpdateLock.readLock().unlock();
        int hc2 = ((ReentrantReadWriteUpdateLock.ReadLock)reentrantReadWriteUpdateLock.readLock()).holdCount().value;
        assertEquals(1, hc1);
        assertEquals(0, hc2);
    }

    @Test
    public void testReadLockHoldCount_WithTimeout() throws Exception {
        // Acquire the write lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Try to acquire read lock in foreground thread, waiting for a short time, should fail...
        assertFalse(reentrantReadWriteUpdateLock.readLock().tryLock(1, TimeUnit.MILLISECONDS));
        int hc1 = ((ReentrantReadWriteUpdateLock.ReadLock)reentrantReadWriteUpdateLock.readLock()).holdCount().value;
        assertEquals(0, hc1);

        // Release the write lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Try to acquire read lock again in foreground thread, waiting for a short time, should succeed...
        assertTrue(reentrantReadWriteUpdateLock.readLock().tryLock(1, TimeUnit.MILLISECONDS));
        int hc2 = ((ReentrantReadWriteUpdateLock.ReadLock)reentrantReadWriteUpdateLock.readLock()).holdCount().value;
        assertEquals(1, hc2);

        // Release the read lock in foreground thread...
        reentrantReadWriteUpdateLock.readLock().unlock();
        int hc3 = ((ReentrantReadWriteUpdateLock.ReadLock)reentrantReadWriteUpdateLock.readLock()).holdCount().value;
        assertEquals(0, hc3);
    }

    @Test
    public void testUpdateLockHoldCount() throws Exception {
        reentrantReadWriteUpdateLock.updateLock().lock();
        int hc1 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        reentrantReadWriteUpdateLock.updateLock().unlock();
        int hc2 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(1, hc1);
        assertEquals(0, hc2);
    }

    @Test
    public void testUpdateLockHoldCount_Interruptibly() throws Exception {
        reentrantReadWriteUpdateLock.updateLock().lockInterruptibly();
        int hc1 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        reentrantReadWriteUpdateLock.updateLock().unlock();
        int hc2 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(1, hc1);
        assertEquals(0, hc2);
    }

    @Test
    public void testUpdateLockHoldCount_WithTimeout() throws Exception {
        // Acquire the write lock in thread 1...
        assertTrue(executor1.submit(new TryLockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Try to acquire update lock in foreground thread, waiting for a short time, should fail...
        assertFalse(reentrantReadWriteUpdateLock.updateLock().tryLock(1, TimeUnit.MILLISECONDS));
        int hc1 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(0, hc1);

        // Release the write lock in thread 1...
        assertTrue(executor1.submit(new UnlockTask(reentrantReadWriteUpdateLock.writeLock())).get());

        // Try to acquire update lock again in foreground thread, waiting for a short time, should succeed...
        assertTrue(reentrantReadWriteUpdateLock.updateLock().tryLock(1, TimeUnit.MILLISECONDS));
        int hc2 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(1, hc2);

        // Release the update lock in foreground thread...
        reentrantReadWriteUpdateLock.updateLock().unlock();
        int hc3 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(0, hc3);
    }

    @Test
    public void testWriteLockAcquiresUpdateLock() throws Exception {
        reentrantReadWriteUpdateLock.updateLock().lock();
        int hc1 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(1, hc1);

        reentrantReadWriteUpdateLock.writeLock().lock();
        int hc2 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(2, hc2);

        reentrantReadWriteUpdateLock.writeLock().unlock();
        int hc3 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(1, hc3);

        reentrantReadWriteUpdateLock.updateLock().unlock();
        int hc4 = ((ReentrantReadWriteUpdateLock.UpdateLock)reentrantReadWriteUpdateLock.updateLock()).holdCount().value;
        assertEquals(0, hc4);
    }

    @Test
    public void testWriteLock_Lock() throws Exception {
        // Acquire the write lock...
        reentrantReadWriteUpdateLock.writeLock().lock();

        // Try to acquire read lock in thread 2, should fail...
        assertFalse(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Release the write lock...
        reentrantReadWriteUpdateLock.writeLock().unlock();
    }

    @Test
    public void testWriteLock_LockInterruptibly() throws Exception {
        // Acquire the write lock...
        reentrantReadWriteUpdateLock.writeLock().lockInterruptibly();

        // Try to acquire read lock in thread 2, should fail...
        assertFalse(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Release the write lock...
        reentrantReadWriteUpdateLock.writeLock().unlock();
    }

    @Test
    public void testWriteLock_TryLockWithTimeout() throws Exception {
        // Acquire the write lock...
        assertTrue(reentrantReadWriteUpdateLock.writeLock().tryLock(1, TimeUnit.MILLISECONDS));

        // Try to acquire read lock in thread 2, should fail...
        assertFalse(executor2.submit(new TryLockTask(reentrantReadWriteUpdateLock.readLock())).get());

        // Release the write lock...
        reentrantReadWriteUpdateLock.writeLock().unlock();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReadLockNewCondition() throws Exception {
        reentrantReadWriteUpdateLock.readLock().newCondition();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateLockNewCondition() throws Exception {
        reentrantReadWriteUpdateLock.updateLock().newCondition();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testWriteLockNewCondition() throws Exception {
        reentrantReadWriteUpdateLock.writeLock().newCondition();
    }



    static class TryLockTask implements Callable<Boolean> {
        final Lock lock;
        public TryLockTask(Lock lock) {
            this.lock = lock;
        }
        @Override
        public Boolean call() throws Exception {
            return lock.tryLock();
        }
    }

    static class UnlockTask implements Callable<Boolean> {
        final Lock lock;
        public UnlockTask(Lock lock) {
            this.lock = lock;
        }
        @Override
        public Boolean call() throws Exception {
            lock.unlock();
            return true;
        }
    }
}
