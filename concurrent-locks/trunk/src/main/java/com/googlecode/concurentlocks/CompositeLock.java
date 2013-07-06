package com.googlecode.concurentlocks;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A lock spanning a group of backing locks. When this composite lock is locked, it locks all backing locks, and when
 * unlocked, it unlocks all backing locks. Employs roll back logic to ensure that either all locks are acquired
 * or no locks are acquired. Locks are unlocked in the reverse of the order in which the were acquired.
 * <p/>
 * This class delegates most of its implementation to the {@link Locks} utility class.
 *
 * @author Niall Gallagher
 */
public class CompositeLock implements Lock {

    final Deque<Lock> locks;

    public CompositeLock(Lock... locks) {
        this(new LinkedList<Lock>(Arrays.asList(locks)));
    }

    public CompositeLock(Deque<Lock> locks) {
        this.locks = locks;
    }

    @Override
    public void lock() {
        Locks.lockAll(locks);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        Locks.lockInterruptiblyAll(locks);
    }

    @Override
    public boolean tryLock() {
        return Locks.tryLockAll(locks);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return Locks.tryLockAll(time, unit, locks);
    }

    @Override
    public void unlock() {
        // Unlock in reverse order...
        Locks.unlockAll(new Iterable<Lock>() {
            @Override
            public Iterator<Lock> iterator() {
                return locks.descendingIterator();
            }
        });
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
