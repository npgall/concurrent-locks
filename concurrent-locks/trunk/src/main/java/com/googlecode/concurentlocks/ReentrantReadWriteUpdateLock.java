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

    static abstract class HoldCountLock implements Lock {

        static class HoldCount { int value; }

        final ThreadLocal<HoldCount> threadHoldCount = new ThreadLocal<HoldCount>() {
            @Override
            protected HoldCount initialValue() {
                return new HoldCount();
            }
        };

        HoldCount holdCount() {
            return threadHoldCount.get();
        }

    }

    class ReadLock extends HoldCountLock {

        @Override
        public void lock() {
            ensureNotHoldingUpdateLock();
            readWriteLock.readLock().lock();
            holdCount().value++;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            ensureNotHoldingUpdateLock();
            readWriteLock.readLock().lockInterruptibly();
            holdCount().value++;
        }

        @Override
        public boolean tryLock() {
            ensureNotHoldingUpdateLock();
            if (readWriteLock.readLock().tryLock()) {
                holdCount().value++;
                return true;
            }
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            ensureNotHoldingUpdateLock();
            if (readWriteLock.readLock().tryLock(time, unit)) {
                holdCount().value++;
                return true;
            }
            return false;
        }

        @Override
        public void unlock() {
            readWriteLock.readLock().unlock();
            holdCount().value--;
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        void ensureNotHoldingUpdateLock() {
            if (updateLock.holdCount().value > 0) {
                throw new IllegalStateException("Cannot acquire read lock, as this thread previously acquired and must first release the update lock");
            }
        }
    }

    class UpdateLock extends HoldCountLock {

        @Override
        public void lock() {
            ensureNotHoldingReadLock();
            updateMutex.lock();
            holdCount().value++;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            ensureNotHoldingReadLock();
            updateMutex.lockInterruptibly();
            holdCount().value++;
        }

        @Override
        public boolean tryLock() {
            ensureNotHoldingReadLock();
            if (updateMutex.tryLock()) {
                holdCount().value++;
                return true;
            }
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            ensureNotHoldingReadLock();
            if (updateMutex.tryLock(time, unit)) {
                holdCount().value++;
                return true;
            }
            return false;
        }

        @Override
        public void unlock() {
            updateMutex.unlock();
            holdCount().value--;
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        void ensureNotHoldingReadLock() {
            if (readLock.holdCount().value > 0) {
                throw new IllegalStateException("Cannot acquire update lock, as this thread previously acquired and must first release the read lock");
            }
        }
    }

    class WriteLock implements Lock {

        @Override
        public void lock() {
            ensureNotHoldingReadLock();
            // Acquire UPDATE lock again, even if calling thread might already hold it.
            // This allow threads to go from both NONE -> WRITE and from UPDATE -> WRITE.
            // This also ensures that only the thread holding the single UPDATE lock,
            // can request the WRITE lock...
            Locks.lockAll(updateLock, readWriteLock.writeLock());
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            ensureNotHoldingReadLock();
            Locks.lockInterruptiblyAll(updateLock, readWriteLock.writeLock());
        }

        @Override
        public boolean tryLock() {
            ensureNotHoldingReadLock();
            return Locks.tryLockAll(updateLock, readWriteLock.writeLock());
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            ensureNotHoldingReadLock();
            return Locks.tryLockAll(time, unit, updateLock, readWriteLock.writeLock());
        }

        @Override
        public void unlock() {
            Locks.unlockAll(readWriteLock.writeLock(), updateLock);
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        void ensureNotHoldingReadLock() {
            if (readLock.holdCount().value > 0) {
                throw new IllegalStateException("Cannot acquire write lock, as this thread previously acquired and must first release the read lock");
            }
        }
    }
}
