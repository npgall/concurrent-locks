package com.googlecode.concurentlocks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * @author Niall Gallagher
 */
public class ReentrantReadWriteUpdateLock implements ReadWriteUpdateLock {

    final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
    final Lock mutex = new ReentrantLock();

    final ReadLock readLock = new ReadLock();
    final UpdateLock updateLock = new UpdateLock();
    final WriteLock writeLock = new WriteLock();

    static class LockState { int readLocksHeld, updateLocksHeld, writeLocksHeld; }

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
            threadState.readLocksHeld++;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            LockState threadState = threadLockState.get();                        
            readWriteLock.readLock().lockInterruptibly();
            threadState.readLocksHeld++;
        }

        @Override
        public boolean tryLock() {
            LockState threadState = threadLockState.get();                        
            boolean acquired = readWriteLock.readLock().tryLock();
            if (acquired) {
                threadState.readLocksHeld++;
            }
            return acquired;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            LockState threadState = threadLockState.get();                        
            boolean acquired = readWriteLock.readLock().tryLock(time, unit);
            if (acquired) {
                threadState.readLocksHeld++;
            }
            return acquired;
        }

        @Override
        public void unlock() {
            LockState threadState = threadLockState.get();
            readWriteLock.readLock().unlock();
            threadState.readLocksHeld--;
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
            sequentialLock(mutex, readWriteLock.readLock());
            threadState.updateLocksHeld++;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            LockState threadState = threadLockState.get();            
            sequentialLockInterruptibly(mutex, readWriteLock.readLock());
            threadState.updateLocksHeld++;
        }

        @Override
        public boolean tryLock() {
            LockState threadState = threadLockState.get();            
            boolean acquired = sequentialTryLock(mutex, readWriteLock.readLock());
            if (acquired) {
                threadState.updateLocksHeld++;
            }
            return acquired;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            LockState threadState = threadLockState.get();            
            boolean acquired =  sequentialTryLock(mutex, readWriteLock.readLock(), time, unit);
            if (acquired) {
                threadState.updateLocksHeld++;
            }
            return acquired;
        }

        @Override
        public void unlock() {
            LockState threadState = threadLockState.get();            
            sequentialUnlock(readWriteLock.readLock(), mutex);
            threadState.updateLocksHeld--;
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    class WriteLock implements Lock {

        @Override
        public void lock() {
            LockState threadState = threadLockState.get();
            if (threadState.updateLocksHeld <= 0) {
                throw new IllegalStateException("Cannot upgrade to write lock, this thread does not hold and must first acquire an update lock");
            }
            // At this point current thread holds the only update lock.

            // Release read lock; update lock still prevents other threads from getting here to steal the write lock...
            readWriteLock.readLock().unlock();
            // Only this thread can request write lock so no other thread could have stolen it,
            // but it might block until other readers finish...
            readWriteLock.writeLock().lock();
            threadState.writeLocksHeld++;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            LockState threadState = threadLockState.get();
            if (threadState.updateLocksHeld <= 0) {
                throw new IllegalStateException("Cannot upgrade to write lock, this thread does not hold and must first acquire an update lock");
            }
            // At this point current thread holds the only update lock.

            // Release read lock; update lock still prevents other threads from getting here to steal the write lock...
            readWriteLock.readLock().unlock();
            try {
                // Only this thread can request write lock so no other thread could have stolen it,
                // but it might block until other readers finish...
                readWriteLock.writeLock().lockInterruptibly();
            }
            catch (InterruptedException interruptedException) {
                // Interrupted before write lock could be obtained.
                // Roll back: re-acquire the read lock, which is guaranteed to succeed immediately
                // because no other threads hold the write lock...
                readWriteLock.readLock().lock();
                throw interruptedException;
            }
            threadState.writeLocksHeld++;
        }

        @Override
        public boolean tryLock() {
            LockState threadState = threadLockState.get();
            if (threadState.updateLocksHeld <= 0) {
                throw new IllegalStateException("Cannot upgrade to write lock, this thread does not hold and must first acquire an update lock");
            }
            // At this point current thread holds the only update lock.

            // Release read lock; update lock still prevents other threads from getting here to steal the write lock...
            readWriteLock.readLock().unlock();
            // Only this thread can request write lock so no other thread could have stolen it,
            // but other readers may prevent its immediate acquisition...
            boolean acquired = readWriteLock.writeLock().tryLock();
            if (!acquired) {
                // Other readers must have prevented the immediate acquisition of write lock.
                // Roll back: re-acquire the read lock, which is guaranteed to succeed immediately
                // because no other threads hold the write lock...
                readWriteLock.readLock().lock();
            }
            else {
                threadState.writeLocksHeld++;
            }
            return acquired;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            LockState threadState = threadLockState.get();
            if (threadState.updateLocksHeld <= 0) {
                throw new IllegalStateException("Cannot upgrade to write lock, this thread does not hold and must first acquire an update lock");
            }
            // At this point current thread holds the only update lock.

            // Release read lock; update lock still prevents other threads from getting here to steal the write lock...
            readWriteLock.readLock().unlock();
            boolean acquired;
            try {
                // Only this thread can request write lock so no other thread could have stolen it,
                // but other readers may prevent its immediate acquisition...
                acquired = readWriteLock.writeLock().tryLock(time, unit);
            }
            catch (InterruptedException interruptedException) {
                // Interrupted before timeout occurred.
                // Roll back: re-acquire the read lock, which is guaranteed to succeed immediately
                // because no other threads hold the write lock...
                readWriteLock.readLock().lock();
                throw interruptedException;
            }
            if (!acquired) {
                // Timeout occurred. Other readers must have prevented the acquisition of write lock within timeout.
                // Roll back: re-acquire the read lock, which is guaranteed to succeed immediately
                // because no other threads hold the write lock...
                readWriteLock.readLock().lock();
            }
            else {
                threadState.writeLocksHeld++;
            }
            return acquired;
        }

        @Override
        public void unlock() {
            LockState threadState = threadLockState.get();
            if (threadState.writeLocksHeld <= 0) {
                throw new IllegalStateException("Cannot downgrade to update lock, this thread does not hold a write lock");
            }
            // At this point current thread holds the only write lock and the only update lock.

            // Release the write lock...
            readWriteLock.writeLock().unlock();  // this is the only place where write lock is released, and at this point current thread holds it and the single update lock
            // Re-acquire the read lock to revert to a standard update lock again,
            // which is guaranteed to succeed immediately because no other threads hold the write lock...
            readWriteLock.readLock().lock();
            threadState.writeLocksHeld--;
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }


    // ******************************
    // ***** Utility methods... *****
    // ******************************

    static void sequentialLock(Lock lock1, Lock lock2) {
        lock1.lock();
        lock2.lock();
    }

    static void sequentialLockInterruptibly(Lock lock1, Lock lock2) throws InterruptedException {
        lock1.lockInterruptibly();
        try {
            lock2.lockInterruptibly();  // TODO validate interrupt handling
        }
        catch (InterruptedException interruptedException) {
            lock1.unlock();
            throw interruptedException;
        }
    }

    static boolean sequentialTryLock(Lock lock1, Lock lock2) {
        boolean lock1Acquired = lock1.tryLock();
        if (!lock1Acquired) {
            return false;
        }
        boolean lock2Acquired = lock2.tryLock();
        if (!lock2Acquired) {
            lock1.unlock();
            return false;
        }
        return true;
    }

    static boolean sequentialTryLock(Lock lock1, Lock lock2, long time, TimeUnit unit) throws InterruptedException {
        long startTime = System.nanoTime();
        boolean lock1Acquired = lock1.tryLock(time, unit);
        if (!lock1Acquired) {
            return false;
        }
        long elapsedTime = System.nanoTime() - startTime;
        long remainingTime = unit.toNanos(time) - elapsedTime;
        boolean lock2Acquired;
        try {
            lock2Acquired = lock2.tryLock(remainingTime, TimeUnit.NANOSECONDS); // TODO validate interrupt handling
        }
        catch (InterruptedException interruptedException) {
            lock1.unlock();
            throw interruptedException;
        }
        if (!lock2Acquired) {
            lock1.unlock();
            return false;
        }
        return true;
    }

    static void sequentialUnlock(Lock lock1, Lock lock2) {
        lock1.unlock();
        lock2.unlock();
    }
}
