  + Date Author Id Revision HeadURL
  + native
package org.apache.lucene.index;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.ReaderUtil;

/** Base class for implementing {@link CompositeReader}s based on an array
 * of sub-readers. The implementing class has to add code for
 * correctly refcounting and closing the sub-readers.
 * 
 * <p>User code will most likely use {@link MultiReader} to build a
 * composite reader on a set of sub-readers (like several
 * {@link DirectoryReader}s).
 * 
 * <p> For efficiency, in this API documents are often referred to via
 * <i>document numbers</i>, non-negative integers which each name a unique
 * document in the index.  These document numbers are ephemeral -- they may change
 * as documents are added to and deleted from an index.  Clients should thus not
 * rely on a given document having the same number between sessions.
 * 
 * <p><a name="thread-safety"></a><p><b>NOTE</b>: {@link
 * IndexReader} instances are completely thread
 * safe, meaning multiple threads can call any of its methods,
 * concurrently.  If your application requires external
 * synchronization, you should <b>not</b> synchronize on the
 * <code>IndexReader</code> instance; use your own
 * (non-Lucene) objects instead.
 * @see MultiReader
 * @lucene.internal
 */
public abstract class BaseCompositeReader<R extends IndexReader> extends CompositeReader {
  protected final R[] subReaders;
  protected final int[] starts;       // 1st docno for each reader
  private final int maxDoc;
  private final int numDocs;
  private final boolean hasDeletions;
  
  /**
   * Constructs a {@code BaseCompositeReader} on the given subReaders.
   * @param subReaders the wrapped sub-readers. This array is returned by
   * {@link #getSequentialSubReaders} and used to resolve the correct
   * subreader for docID-based methods. <b>Please note:</b> This array is <b>not</b>
   * cloned and not protected for modification, the subclass is responsible 
   * to do this.
   */
  protected BaseCompositeReader(R[] subReaders) throws IOException {
    this.subReaders = subReaders;
    starts = new int[subReaders.length + 1];    // build starts array
    int maxDoc = 0, numDocs = 0;
    boolean hasDeletions = false;
    for (int i = 0; i < subReaders.length; i++) {
      starts[i] = maxDoc;
      final IndexReader r = subReaders[i];
      maxDoc += r.maxDoc();      // compute maxDocs
      numDocs += r.numDocs();    // compute numDocs
      if (r.hasDeletions()) {
        hasDeletions = true;
      }
      r.registerParentReader(this);
    }
    starts[subReaders.length] = maxDoc;
    this.maxDoc = maxDoc;
    this.numDocs = numDocs;
    this.hasDeletions = hasDeletions;
  }

  @Override
  public final Fields getTermVectors(int docID) throws IOException {
    ensureOpen();
    final int i = readerIndex(docID);        // find subreader num
    return subReaders[i].getTermVectors(docID - starts[i]); // dispatch to subreader
  }

  @Override
  public final int numDocs() {
    // Don't call ensureOpen() here (it could affect performance)
    return numDocs;
  }

  @Override
  public final int maxDoc() {
    // Don't call ensureOpen() here (it could affect performance)
    return maxDoc;
  }

  @Override
  public final void document(int docID, StoredFieldVisitor visitor) throws CorruptIndexException, IOException {
    ensureOpen();
    final int i = readerIndex(docID);                          // find subreader num
    subReaders[i].document(docID - starts[i], visitor);    // dispatch to subreader
  }

  @Override
  public final boolean hasDeletions() {
    // Don't call ensureOpen() here (it could affect performance)
    return hasDeletions;
  }

  @Override
  public final int docFreq(String field, BytesRef t) throws IOException {
    ensureOpen();
    int total = 0;          // sum freqs in subreaders
    for (int i = 0; i < subReaders.length; i++) {
      total += subReaders[i].docFreq(field, t);
    }
    return total;
  }

  /** Helper method for subclasses to get the corresponding reader for a doc ID */
  protected final int readerIndex(int docID) {
    if (docID < 0 || docID >= maxDoc) {
      throw new IllegalArgumentException("docID must be >= 0 and < maxDoc=" + maxDoc + " (got docID=" + docID + ")");
    }
    return ReaderUtil.subIndex(docID, this.starts);
  }
  
  @Override
  public final R[] getSequentialSubReaders() {
    return subReaders;
  }
}
