Welcome to the new home of Concurrent-Locks!

*Additional Lock implementations for Java, extending the base functionality provided by the JDK.*

The following locks and utilities are provided.

**ReentrantReadWriteUpdateLock**
Similar to ReentrantReadWriteLock in the JDK, but in addition to providing a read lock and a write lock, it provides a third option: an update lock. Unlike the JDK, this supports read-before-write data access patterns efficiently.

**CompositeLock**
A lock spanning a group of backing locks.

**Lock utilities**
The Locks class provides utility methods which mimic the JDK `java.util.concurrent.locks.Lock` API, but instead apply those operations on groups of backing locks.

---
* You can find the latest source code for Concurrent-Locks here on GitHub.
* The current release of Concurrent-Locks is 1.0.0 (as at August 2015).
* Documentation is currently being transferred here to GitHub; and in the meantime you can find full documentation for Concurrent-Trees at its previous home on Google Code: http://code.google.com/p/concurrent-locks/
* The Concurrent-Trees discussion forum can be found at: http://groups.google.com/group/concurrent-locks-discuss
