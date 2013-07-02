package com.googlecode.concurentlocks;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * @author Niall Gallagher
 */
public interface ReadWriteUpdateLock extends ReadWriteLock {

    /**
     * Returns a lock which allows reading and which may also be upgraded to a lock allowing writing.
     *
     * @return a lock which allows reading and which may also be upgraded to a lock allowing writing.
     */
    Lock updateLock();
}
