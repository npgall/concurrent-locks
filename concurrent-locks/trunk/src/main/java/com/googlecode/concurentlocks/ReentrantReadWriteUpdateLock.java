package com.googlecode.concurentlocks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * @author Niall Gallagher
 */
public class ReentrantReadWriteUpdateLock implements ReadWriteUpdateLock {

    final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
    final Lock updateMutex = new ReentrantLock();

    final ReadLock readLock = new ReadLock();
    final UpdateLock updateLock = new UpdateLock();
    final WriteLock writeLock = new WriteLock();

    static class LockState { int readHoldCount, updateHoldCount, writeHoldCount; }

    final ThreadLocal<LockState> threadLockState = new ThreadLocal<LockState>() {
        @Override
        protected LockState initialValue() {
            return new LockState();
        }
    };

    @Override
    public Lock updateLock() {
        return updateLock;
    }

    @Override
    public Lock readLock() {
        return readLock;
    }

    @Override
    public Lock writeLock() {
        return writeLock;
    }

    class ReadLock implements Lock {

        @Override
        public void lock() {
            LockState threadState = threadLockState.get();            
            readWriteLock.readLock().lock();
            threadState.readHoldCount++;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            LockState threadState = threadLockState.get();                        
            readWriteLock.readLock().lockInterruptibly();
            threadState.readHoldCount++;
        }

        @Override
        public boolean tryLock() {
            LockState threadState = threadLockState.get();                        
            if (readWriteLock.readLock().tryLock()) {
                threadState.readHoldCount++;
                return true;
            }
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            LockState threadState = threadLockState.get();                        
            if (readWriteLock.readLock().tryLock(time, unit)) {
                threadState.readHoldCount++;
                return true;
            }
            return false;
        }

        @Override
        public void unlock() {
            LockState threadState = threadLockState.get();
            readWriteLock.readLock().unlock();
            threadState.readHoldCount--;
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    class UpdateLock implements Lock {

        @Override
        public void lock() {
            LockState threadState = threadLockState.get();
            ensureNotHoldingReadLock(threadState);
            updateMutex.lock();
            threadState.updateHoldCount++;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            LockState threadState = threadLockState.get();
            ensureNotHoldingReadLock(threadState);
            updateMutex.lockInterruptibly();
            threadState.updateHoldCount++;
        }

        @Override
        public boolean tryLock() {
            LockState threadState = threadLockState.get();
            ensureNotHoldingReadLock(threadState);
            if (updateMutex.tryLock()) {
                threadState.updateHoldCount++;
                return true;
            }
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            LockState threadState = threadLockState.get();
            ensureNotHoldingReadLock(threadState);
            if (updateMutex.tryLock(time, unit)) {
                threadState.updateHoldCount++;
                return true;
            }
            return false;
        }

        @Override
        public void unlock() {
            LockState threadState = threadLockState.get();            
            updateMutex.unlock();
            threadState.updateHoldCount--;
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        void ensureNotHoldingReadLock(LockState threadState) {
            if (threadState.readHoldCount > 0) {
                throw new IllegalStateException("Cannot acquire update lock, as this thread previously acquired and must first release the read lock");
            }
        }
    }

    class WriteLock implements Lock {

        @Override
        public void lock() {
            LockState threadState = threadLockState.get();
            ensureNotHoldingReadLock(threadState);

            // Acquire UPDATE lock again, even if calling thread might already hold it,
            // to allow threads to go from both NONE -> WRITE, in addition to UPDATE -> WRITE...
            updateMutex.lock();
            threadState.updateHoldCount++;
            // At this point current thread is the only thread to hold the UPDATE lock.

            // Only this thread can request WRITE lock since it is the only one to hold the UPDATE lock,
            // but we might block here until other threads holding READ locks finish...
            readWriteLock.writeLock().lock();
            threadState.writeHoldCount++;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            LockState threadState = threadLockState.get();
            ensureNotHoldingReadLock(threadState);

            // Acquire UPDATE lock again, even if calling thread might already hold it,
            // to allow threads to go from both NONE -> WRITE, in addition to UPDATE -> WRITE...
            updateMutex.lockInterruptibly();
            threadState.updateHoldCount++;
            // At this point current thread is the only thread to hold the UPDATE lock.

            try {
                // Only this thread can request WRITE lock since it is the only one to hold the UPDATE lock,
                // but we might block here until other threads holding READ locks finish...
                readWriteLock.writeLock().lockInterruptibly();  // TODO validate interrupt handling
            }
            catch (InterruptedException interruptedException) {
                // Roll back: interrupted while waiting for the WRITE lock, so release the UPDATE lock...
                updateMutex.unlock();
                threadState.updateHoldCount--;
                throw interruptedException;
            }
            threadState.writeHoldCount++;
        }

        @Override
        public boolean tryLock() {
            LockState threadState = threadLockState.get();
            ensureNotHoldingReadLock(threadState);

            // Acquire UPDATE lock again, even if calling thread might already hold it,
            // to allow threads to go from both NONE -> WRITE, in addition to UPDATE -> WRITE...
            boolean updateAcquired = updateMutex.tryLock();
            if (!updateAcquired) {
                return false;
            }
            threadState.updateHoldCount++;
            // At this point current thread is the only thread to hold the UPDATE lock.

            // Only this thread can request WRITE lock since it is the only one to hold the UPDATE lock,
            // but we might fail here if other threads hold READ locks...
            boolean writeAcquired = readWriteLock.writeLock().tryLock();
            if (!writeAcquired) {
                // Roll back: failed to obtain WRITE lock due to other threads holding READ locks,
                // so release the UPDATE lock...
                updateMutex.unlock();
                threadState.updateHoldCount--;
                return false;
            }
            threadState.writeHoldCount++;
            return true;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            LockState threadState = threadLockState.get();
            ensureNotHoldingReadLock(threadState);

            long startTime = System.nanoTime();
            // Acquire UPDATE lock again, even if calling thread might already hold it,
            // to allow threads to go from both NONE -> WRITE, in addition to UPDATE -> WRITE...
            boolean updateAcquired = updateMutex.tryLock(time, unit);
            if (!updateAcquired) {
                return false;
            }
            threadState.updateHoldCount++;
            // At this point current thread is the only thread to hold the UPDATE lock.

            // Calculate time remaining within the timeout supplied...
            long elapsedTime = System.nanoTime() - startTime;
            long remainingTime = unit.toNanos(time) - elapsedTime;

            // Only this thread can request WRITE lock since it is the only one to hold the UPDATE lock,
            // but we might fail here if other threads hold READ locks or if interrupted while waiting...
            boolean writeAcquired;
            try {
                writeAcquired = readWriteLock.writeLock().tryLock(remainingTime, TimeUnit.NANOSECONDS); // TODO validate interrupt handling
            }
            catch (InterruptedException interruptedException) {
                // Roll back: interrupted while waiting for the WRITE lock, so release the UPDATE lock...
                updateMutex.unlock();
                threadState.updateHoldCount--;
                throw interruptedException;
            }
            if (!writeAcquired) {
                // Roll back: failed to obtain WRITE lock due to other threads holding READ locks,
                // so release the UPDATE lock...
                updateMutex.unlock();
                threadState.updateHoldCount--;
                return false;
            }
            threadState.writeHoldCount++;
            return true;
        }

        @Override
        public void unlock() {
            LockState threadState = threadLockState.get();
            // Release the WRITE lock...
            readWriteLock.writeLock().unlock();
            threadState.writeHoldCount--;
            // Release the UPDATE lock...
            updateMutex.unlock();
            threadState.updateHoldCount--;
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        void ensureNotHoldingReadLock(LockState threadState) {
            if (threadState.readHoldCount > 0) {
                throw new IllegalStateException("Cannot acquire write lock, as this thread previously acquired and must first release the read lock");
            }
        }
    }
}
