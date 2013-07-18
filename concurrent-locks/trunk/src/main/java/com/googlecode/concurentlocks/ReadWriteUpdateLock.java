package com.googlecode.concurentlocks;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * A <tt>ReadWriteUpdateLock</tt> extends the JDK {@link ReadWriteLock}, providing an update lock as a third type of
 * associated lock in addition to the lock for read-only operation and the lock for writing.
 * <p/>
 * The {@link #updateLock update lock} supports read-only operations and can coexist with multiple
 * {@link #readLock read lock}s held simultaneously by other reader threads. However it may also be upgraded from
 * its read-only status to a {@link #writeLock write lock}, and it may be downgraded again back to a read lock.
 * <p/>
 * See implementation {@link ReentrantReadWriteUpdateLock} for more details.
 *
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
