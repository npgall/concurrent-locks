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
package com.googlecode.concurentlocks.examples;

import com.googlecode.concurentlocks.ReadWriteUpdateLock;
import com.googlecode.concurentlocks.ReentrantReadWriteUpdateLock;

/**
 * Demonstrates usage of {@link com.googlecode.concurentlocks.ReentrantReadWriteUpdateLock}.
 * <p/>
 * For simplicity leaves methods to actually read and write the document as an exercise to the reader.
 *
 * @author Niall Gallagher
 */
public abstract class ExampleUsage {

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

    public Document readDocument() {
        readWriteUpdateLock.readLock().lock(); // blocks others from acquiring write lock
        try {
            return readInDocument();
        }
        finally {
            readWriteUpdateLock.readLock().unlock();
        }
    }

    interface Document {}
    protected abstract Document readInDocument();
    protected abstract boolean shouldUpdate(Document document);
    protected abstract Document generateNewVersion(Document document);
    protected abstract void writeOutDocument(Document document);
}
