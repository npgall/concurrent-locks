# Concurrent-Locks #

This project provides additional Lock implementations for Java.

The following locks and utilities are provided.

# ReentrantReadWriteUpdateLock #
The [ReentrantReadWriteUpdateLock](http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/concurrent-locks/master/documentation/javadoc/apidocs/com/googlecode/concurentlocks/ReentrantReadWriteUpdateLock.html) provided in this project, is like the [ReentrantReadWriteLock](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/locks/ReentrantReadWriteLock.html) counterpart in the JDK, but in addition to providing a _read_ lock and a _write_ lock, it provides a third option: an _update_ lock.

Unlike the JDK, this efficiently supports **_read-before-write_ data access patterns**.

## Background - JDK ReentrantReadWriteLock ##
The `ReentrantReadWriteLock` in the JDK classifies threads as needing to _read-without-write_, _write-without-read_, or _write-before-read_. Threads can obtain the read lock, or the write lock, and if they have the write lock, they can downgrade it to a read lock. But the key limitation is that it does not support _read-before-write_: threads which hold a read lock cannot upgrade it to a write lock. Thus the JDK `ReentrantReadWriteLock` does not support _read-before-write_ access patterns efficiently.

Read-before-write is common. Imagine a document (or a resource) where most requests are to read data, but also occasionally the document might need to be updated, which involves reading it in, performing some analysis and alterations to it, then writing out a new version. An efficient lock implementation would minimize the amount of time for which reading threads are blocked, while the document is being updated.

The only way to provide safe access to the document while it is being updated like this using the `ReentrantReadWriteLock` of the JDK, is for a thread which might update the document (a "writing thread") to do so in three steps:
  1. Acquire the write lock, and only then read the document
  1. Hold the write lock while analyzing and generating a new version of the document
  1. Hold the write lock while writing out the new version

If the writing thread did not acquire any lock before it got to step 3, and it tried to acquire the write lock only at step 3, then a race condition is possible with other threads doing the same thing, because at step 3 there would be no guarantee that the version each thread had read at step 1 had not already been overwritten with another version by another thread. Performing a write in this case, would be susceptible to the lost update problem, where one thread overwrites changes made by another thread.

If the writing thread acquired a read lock at step 1, it would be guaranteed that the version of the document it read had not been modified by other threads by the time it got to step 3, but at step 3 it would be unable to acquire the write lock because the JDK `ReentrantReadWriteLock` does not allow the read lock to be upgraded to a write lock.

So the only solution with the the JDK `ReentrantReadWriteLock`, is for the writing thread to hold the write lock all for three steps of the process, which prevents concurrent read access by other threads for the entire duration, which is needless for steps 1 & 2.

The following table shows the situations in which a conventional Read-Write lock can needlessly block reading threads in applications with _read-before-write_ access patterns. The extended periods for which reads are blocked, of course will be felt most severely in applications in which reading and writing are long-running operations, in applications where document access has relatively high latency (e.g. across a network), or in applications with high levels of concurrency.

**Read-before-write access pattern with conventional Read-Write lock**

| **Step** | **Concurrent reads allowed?** |
|:---------|:------------------------------|
|1. Acquire write lock and read data|<font color='red'>NO</font>    |
|2. Holding write lock, perform computations on the data|<font color='red'>NO</font>    |
|3. If new data needs to be written, holding write lock, write new data|<font color='red'>NO</font>    |
|4. Release write lock|<font color='green'>YES</font> |

## ReentrantReadWriteUpdateLock Overview ##
The `ReentrantReadWriteUpdateLock` in this project provides a third type of lock, an _update lock_. An update lock is an intermediate type of lock between a read lock and a write lock. Like the write lock, only one thread can acquire an update lock at a time. But like a read lock, it allows read access to the thread which holds it, and concurrently to other threads which hold regular read locks.

The key feature is that the update lock can be upgraded from its read-only status, to a write lock. Thus it supports _read-before-write_ access patterns efficiently. Also the write lock can be downgraded again to an update lock, supporting _write-before-read_ access patterns efficiently.

The following table shows the situations in which the Read-Write-Update lock provided in this project can increase concurrency in applications with _read-before-write_ access patterns. It should also be noted that if the writing thread determines that the document does not need to be updated after all, then it does not upgrade to a write lock and so concurrent reads will not be blocked at all.

**Read-before-write access pattern with Read-Write-Update lock**

| **Step** | **Concurrent reads allowed?** |
|:---------|:------------------------------|
|1. Acquire update lock and read data|<font color='green'>YES</font> |
|2. Holding update lock, perform computations on the data|<font color='green'>YES</font> |
|3. If new data needs to be written, upgrade to write lock, write new data|<font color='red'>NO</font>    |
|4. Release write lock, optionally release update lock|<font color='green'>YES</font> |

**Example Usage - Writing Threads**
```
final ReadWriteUpdateLock readWriteUpdateLock = new ReentrantReadWriteUpdateLock();

public void updateDocumentIfNecessary() {
    readWriteUpdateLock.updateLock().lock(); // allows other readers, blocks others from acquiring update or write locks
    try {
        // STEP 1: Read in the document...
        Document currentDocument = readInDocument();
        // Decide if document actually needs to be updated...
        if (shouldUpdate(currentDocument)) {
            // STEP 2: Generate a new version of the document...
            Document newVersion = generateNewVersion(currentDocument);
            // STEP 3: Write out new version...
            readWriteUpdateLock.writeLock().lock(); // upgrade to the write lock, at this point blocks other readers
            try {
                writeOutDocument(newVersion);
            }
            finally {
                readWriteUpdateLock.writeLock().unlock(); // downgrade back to update lock
            }
        }
    }
    finally {
        readWriteUpdateLock.updateLock().unlock(); // release update lock
    }
}
```

**Example Usage - Reading Threads**
```
public Document readDocument() {
    readWriteUpdateLock.readLock().lock(); // blocks others from acquiring write lock
    try {
        return readInDocument();
    }
    finally {
        readWriteUpdateLock.readLock().unlock();
    }
}
```

**Supported Lock Acquisition Paths**

|<sub> **Lock Type** </sub>|<sub> **Associated Permissions** </sub>|<sub> **Permitted acquisitions** </sub>|<sub> **Permited downgrades** </sub>|<sub> **Prohibited**</sub>|
|:--------------|:---------------------------|:---------------------------|:-------------------------|:----------------------------|
|<sub>Read           </sub>|<sub>• Read (shared)               </sub>|<sub>• None → Read<br>• Read → Read (reentrant)</sub>|<sub>• Read → None               </sub>|<sub>• Read → Update<br>• Read → Write</sub>|
|<sub>Update         </sub>|<sub>• Read (shared)               </sub>|<sub>• None → Update<br>• Update → Update (reentrant)<br>• Write → Update (reentrant)</sub>|<sub>• Update → None             </sub>|<sub>• Update → Read                </sub>|
|<sub>Write          </sub>|<sub>• Read (exclusive)<br>• Write (exclusive)</sub>|<sub>• None → Write<br>• Update → Write<br>• Write → Write (reentrant)</sub>|<sub>• Write → Update<br>• Write → None</sub>|<sub>• Write → Read                 </sub>|

<h1>CompositeLock</h1>
A lock spanning a group of backing locks. When locked <a href='http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/concurrent-locks/master/documentation/javadoc/apidocs/com/googlecode/concurentlocks/CompositeLock.html'>CompositeLock</a> locks all backing locks. When unlocked, it unlocks all backing locks.<br>
<br>
Ensures that either all backing locks are acquired, or no backing locks are acquired, by applying roll back logic such that failure to acquire any one lock, causes all locks already acquired to be unlocked.<br>
<br>
Lock acquisition methods which take timeouts, are implemented such that the timeout is applied across the acquisition of all locks.<br>
<br>
Locks are unlocked in the reverse of the order in which the were acquired.<br>
<br>
<h1>Utilities</h1>
The <a href='http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/concurrent-locks/master/documentation/javadoc/apidocs/com/googlecode/concurentlocks/Locks.html'>Locks</a> class provides utility methods which mimic the JDK <code>java.util.concurrent.locks.Lock</code> API, but instead apply those operations on groups of backing locks.<br>
<br>
Provides methods:<br>
<ul><li><code>lockAll(Iterable&lt;L&gt; locks)</code>
</li><li><code>lockInterruptiblyAll(Iterable&lt;L&gt; locks)</code>
</li><li><code>tryLockAll(Iterable&lt;L&gt; locks)</code>
</li><li><code>tryLockAll(long time, TimeUnit unit, Iterable&lt;L&gt; locks)</code>
</li><li><code>unlockAll(Iterable&lt;L&gt; locks)</code></li></ul>

<h1>Usage in Maven and Non-Maven Projects</h1>

Concurrent-Locks is in Maven Central, and can be added to a Maven project as follows:
```
<dependency>
    <groupId>com.googlecode.concurrent-locks</groupId>
    <artifactId>concurrent-locks</artifactId>
    <version>1.0.0</version>
</dependency>
```

For non-Maven projects, the library can be downloaded directly from Maven Central [here](http://search.maven.org/remotecontent?filepath=com/googlecode/concurrent-locks/concurrent-locks/).

<h1>Project Status</h1>

  * Development of the library is complete, and all code has 100% test coverage
  * The completed library has been deployed to Maven central as version 1.0.0
  * There are no known bugs
  * For a technical discussion of locks in this project, see the thread on the JDK <a href='http://cs.oswego.edu/pipermail/concurrency-interest/2013-July/thread.html#11621'>concurrency-interest mailing list</a>
  * API JavaDocs are available <a href='http://htmlpreview.github.io/?http://raw.githubusercontent.com/npgall/concurrent-locks/master/documentation/javadoc/apidocs/index.html'>here</a>

Report any bugs/feature requests in the [Issues](http://github.com/npgall/concurrent-locks/issues) tab.<br>
For support please use the <a href='http://groups.google.com/forum/?fromgroups#!forum/concurrent-locks-discuss'>Discussion Group</a>, not direct email to the developers.