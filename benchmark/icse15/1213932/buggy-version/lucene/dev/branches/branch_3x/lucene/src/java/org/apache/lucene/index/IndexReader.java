package org.apache.lucene.index;

/**
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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.search.FieldCache; // javadocs
import org.apache.lucene.search.SearcherManager; // javadocs
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.*;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.CommandLineUtil;
import org.apache.lucene.util.VirtualMethod;

/** IndexReader is an abstract class, providing an interface for accessing an
 index.  Search of an index is done entirely through this abstract interface,
 so that any subclass which implements it is searchable.

 <p> Concrete subclasses of IndexReader are usually constructed with a call to
 one of the static <code>open()</code> methods, e.g. {@link
 #open(Directory, boolean)}.

 <p> For efficiency, in this API documents are often referred to via
 <i>document numbers</i>, non-negative integers which each name a unique
 document in the index.  These document numbers are ephemeral--they may change
 as documents are added to and deleted from an index.  Clients should thus not
 rely on a given document having the same number between sessions.

 <p> An IndexReader can be opened on a directory for which an IndexWriter is
 opened already, but it cannot be used to delete documents from the index then.

 <p>
 <b>NOTE</b>: for backwards API compatibility, several methods are not listed 
 as abstract, but have no useful implementations in this base class and 
 instead always throw UnsupportedOperationException.  Subclasses are 
 strongly encouraged to override these methods, but in many cases may not 
 need to.
 </p>

 <p>

 <b>NOTE</b>: as of 2.4, it's possible to open a read-only
 IndexReader using the static open methods that accept the 
 boolean readOnly parameter.  Such a reader has better 
 concurrency as it's not necessary to synchronize on the 
 isDeleted method.  You must specify false if you want to 
 make changes with the resulting IndexReader.
 </p>

 <a name="thread-safety"></a><p><b>NOTE</b>: {@link
 IndexReader} instances are completely thread
 safe, meaning multiple threads can call any of its methods,
 concurrently.  If your application requires external
 synchronization, you should <b>not</b> synchronize on the
 <code>IndexReader</code> instance; use your own
 (non-Lucene) objects instead.
*/
public abstract class IndexReader implements Cloneable,Closeable {

  /**
   * A custom listener that's invoked when the IndexReader
   * is finished.
   *
   * <p>For a SegmentReader, this listener is called only
   * once all SegmentReaders sharing the same core are
   * closed.  At this point it is safe for apps to evict
   * this reader from any caches keyed on {@link
   * #getCoreCacheKey}.  This is the same interface that
   * {@link FieldCache} uses, internally, to evict
   * entries.</p>
   *
   * <p>For other readers, this listener is called when they
   * are closed.</p>
   *
   * @lucene.experimental
   */
  public static interface ReaderFinishedListener {
    public void finished(IndexReader reader);
  }

  // Impls must set this if they may call add/removeReaderFinishedListener:
  protected volatile Collection<ReaderFinishedListener> readerFinishedListeners;

  /** Expert: adds a {@link ReaderFinishedListener}.  The
   * provided listener is also added to any sub-readers, if
   * this is a composite reader.  Also, any reader reopened
   * or cloned from this one will also copy the listeners at
   * the time of reopen.
   *
   * @lucene.experimental */
  public void addReaderFinishedListener(ReaderFinishedListener listener) {
    ensureOpen();
    readerFinishedListeners.add(listener);
  }

  /** Expert: remove a previously added {@link ReaderFinishedListener}.
   *
   * @lucene.experimental */
  public void removeReaderFinishedListener(ReaderFinishedListener listener) {
    ensureOpen();
    readerFinishedListeners.remove(listener);
  }

  protected void notifyReaderFinishedListeners() {
    // Defensive (should never be null -- all impls must set
    // this):
    if (readerFinishedListeners != null) {
      for(ReaderFinishedListener listener : readerFinishedListeners) {
        listener.finished(this);
      }
    }
  }

  protected void readerFinished() {
    notifyReaderFinishedListeners();
  }

  /**
   * Constants describing field properties, for example used for
   * {@link IndexReader#getFieldNames(FieldOption)}.
   */
  public static enum FieldOption {
    /** All fields */
    ALL,
    /** All indexed fields */
    INDEXED,
    /** All fields that store payloads */
    STORES_PAYLOADS,
    /** All fields that omit tf */
    OMIT_TERM_FREQ_AND_POSITIONS,
    /** All fields that omit positions */
    OMIT_POSITIONS,
    /** All fields which are not indexed */
    UNINDEXED,
    /** All fields which are indexed with termvectors enabled */
    INDEXED_WITH_TERMVECTOR,
    /** All fields which are indexed but don't have termvectors enabled */
    INDEXED_NO_TERMVECTOR,
    /** All fields with termvectors enabled. Please note that only standard termvector fields are returned */
    TERMVECTOR,
    /** All fields with termvectors with position values enabled */
    TERMVECTOR_WITH_POSITION,
    /** All fields with termvectors with offset values enabled */
    TERMVECTOR_WITH_OFFSET,
    /** All fields with termvectors with offset values and position values enabled */
    TERMVECTOR_WITH_POSITION_OFFSET,
  }

  private volatile boolean closed;
  protected boolean hasChanges;
  
  private final AtomicInteger refCount = new AtomicInteger();

  static int DEFAULT_TERMS_INDEX_DIVISOR = 1;

  /** Expert: returns the current refCount for this reader */
  public final int getRefCount() {
    return refCount.get();
  }
  
  /**
   * Expert: increments the refCount of this IndexReader
   * instance.  RefCounts are used to determine when a
   * reader can be closed safely, i.e. as soon as there are
   * no more references.  Be sure to always call a
   * corresponding {@link #decRef}, in a finally clause;
   * otherwise the reader may never be closed.  Note that
   * {@link #close} simply calls decRef(), which means that
   * the IndexReader will not really be closed until {@link
   * #decRef} has been called for all outstanding
   * references.
   *
   * @see #decRef
   * @see #tryIncRef
   */
  public final void incRef() {
    ensureOpen();
    refCount.incrementAndGet();
  }
  
  /**
   * Expert: increments the refCount of this IndexReader
   * instance only if the IndexReader has not been closed yet
   * and returns <code>true</code> iff the refCount was
   * successfully incremented, otherwise <code>false</code>.
   * If this method returns <code>false</code> the reader is either
   * already closed or is currently been closed. Either way this
   * reader instance shouldn't be used by an application unless
   * <code>true</code> is returned.
   * <p>
   * RefCounts are used to determine when a
   * reader can be closed safely, i.e. as soon as there are
   * no more references.  Be sure to always call a
   * corresponding {@link #decRef}, in a finally clause;
   * otherwise the reader may never be closed.  Note that
   * {@link #close} simply calls decRef(), which means that
   * the IndexReader will not really be closed until {@link
   * #decRef} has been called for all outstanding
   * references.
   *
   * @see #decRef
   * @see #incRef
   */
  public final boolean tryIncRef() {
    int count;
    while ((count = refCount.get()) > 0) {
      if (refCount.compareAndSet(count, count+1)) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder();
    if (hasChanges) {
      buffer.append('*');
    }
    buffer.append(getClass().getSimpleName());
    buffer.append('(');
    final IndexReader[] subReaders = getSequentialSubReaders();
    if ((subReaders != null) && (subReaders.length > 0)) {
      buffer.append(subReaders[0]);
      for (int i = 1; i < subReaders.length; ++i) {
        buffer.append(" ").append(subReaders[i]);
      }
    }
    buffer.append(')');
    return buffer.toString();
  }

  /**
   * Expert: decreases the refCount of this IndexReader
   * instance.  If the refCount drops to 0, then pending
   * changes (if any) are committed to the index and this
   * reader is closed.  If an exception is hit, the refCount
   * is unchanged.
   *
   * @throws IOException in case an IOException occurs in commit() or doClose()
   *
   * @see #incRef
   */
  public final void decRef() throws IOException {
    ensureOpen();
    final int rc = refCount.decrementAndGet();
    if (rc == 0) {
      boolean success = false;
      try {
        commit();
        doClose();
        success = true;
      } finally {
        if (!success) {
          // Put reference back on failure
          refCount.incrementAndGet();
        }
      }
      readerFinished();
    } else if (rc < 0) {
      throw new IllegalStateException("too many decRef calls: refCount is " + rc + " after decrement");
    }
  }
  
  protected IndexReader() { 
    refCount.set(1);
  }
  
  /**
   * @throws AlreadyClosedException if this IndexReader is closed
   */
  protected final void ensureOpen() throws AlreadyClosedException {
    if (refCount.get() <= 0) {
      throw new AlreadyClosedException("this IndexReader is closed");
    }
  }
  
  /** Returns a IndexReader reading the index in the given
   *  Directory, with readOnly=true.
   * @param directory the index directory
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public static IndexReader open(final Directory directory) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, null, null, true, DEFAULT_TERMS_INDEX_DIVISOR);
  }

  /** Returns an IndexReader reading the index in the given
   *  Directory.  You should pass readOnly=true, since it
   *  gives much better concurrent performance, unless you
   *  intend to do write operations (delete documents or
   *  change norms) with the reader.
   * @param directory the index directory
   * @param readOnly true if no changes (deletions, norms) will be made with this IndexReader
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #open(Directory)} instead
   */
  @Deprecated
  public static IndexReader open(final Directory directory, boolean readOnly) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, null, null, readOnly, DEFAULT_TERMS_INDEX_DIVISOR);
  }

  /**
   * Open a near real time IndexReader from the {@link org.apache.lucene.index.IndexWriter}.
   *
   * @param writer The IndexWriter to open from
   * @param applyAllDeletes If true, all buffered deletes will
   * be applied (made visible) in the returned reader.  If
   * false, the deletes are not applied but remain buffered
   * (in IndexWriter) so that they will be applied in the
   * future.  Applying deletes can be costly, so if your app
   * can tolerate deleted documents being returned you might
   * gain some performance by passing false.
   * @return The new IndexReader
   * @throws CorruptIndexException
   * @throws IOException if there is a low-level IO error
   *
   * @see #openIfChanged(IndexReader,IndexWriter,boolean)
   *
   * @lucene.experimental
   */
  public static IndexReader open(final IndexWriter writer, boolean applyAllDeletes) throws CorruptIndexException, IOException {
    return writer.getReader(applyAllDeletes);
  }

  /** Expert: returns an IndexReader reading the index in the given
   *  {@link IndexCommit}.
   * @param commit the commit point to open
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public static IndexReader open(final IndexCommit commit) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), null, commit, true, DEFAULT_TERMS_INDEX_DIVISOR);
  }

  /** Expert: returns an IndexReader reading the index in the given
   *  {@link IndexCommit}.  You should pass readOnly=true, since it
   *  gives much better concurrent performance, unless you
   *  intend to do write operations (delete documents or
   *  change norms) with the reader.
   * @param commit the commit point to open
   * @param readOnly true if no changes (deletions, norms) will be made with this IndexReader
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #open(IndexCommit)} instead
   */
  @Deprecated
  public static IndexReader open(final IndexCommit commit, boolean readOnly) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), null, commit, readOnly, DEFAULT_TERMS_INDEX_DIVISOR);
  }

  /** Expert: returns an IndexReader reading the index in
   *  the given Directory, with a custom {@link
   *  IndexDeletionPolicy}.  You should pass readOnly=true,
   *  since it gives much better concurrent performance,
   *  unless you intend to do write operations (delete
   *  documents or change norms) with the reader.
   * @param directory the index directory
   * @param deletionPolicy a custom deletion policy (only used
   *  if you use this reader to perform deletes or to set
   *  norms); see {@link IndexWriter} for details.
   * @param readOnly true if no changes (deletions, norms) will be made with this IndexReader
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #open(Directory)} instead
   */
  @Deprecated
  public static IndexReader open(final Directory directory, IndexDeletionPolicy deletionPolicy, boolean readOnly) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, deletionPolicy, null, readOnly, DEFAULT_TERMS_INDEX_DIVISOR);
  }

  /** Expert: returns an IndexReader reading the index in
   *  the given Directory, with a custom {@link
   *  IndexDeletionPolicy}.  You should pass readOnly=true,
   *  since it gives much better concurrent performance,
   *  unless you intend to do write operations (delete
   *  documents or change norms) with the reader.
   * @param directory the index directory
   * @param deletionPolicy a custom deletion policy (only used
   *  if you use this reader to perform deletes or to set
   *  norms); see {@link IndexWriter} for details.
   * @param readOnly true if no changes (deletions, norms) will be made with this IndexReader
   * @param termInfosIndexDivisor Subsamples which indexed
   *  terms are loaded into RAM. This has the same effect as {@link
   *  IndexWriter#setTermIndexInterval} except that setting
   *  must be done at indexing time while this setting can be
   *  set per reader.  When set to N, then one in every
   *  N*termIndexInterval terms in the index is loaded into
   *  memory.  By setting this to a value > 1 you can reduce
   *  memory usage, at the expense of higher latency when
   *  loading a TermInfo.  The default value is 1.  Set this
   *  to -1 to skip loading the terms index entirely.
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #open(Directory,int)} instead
   */
  @Deprecated
  public static IndexReader open(final Directory directory, IndexDeletionPolicy deletionPolicy, boolean readOnly, int termInfosIndexDivisor) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, deletionPolicy, null, readOnly, termInfosIndexDivisor);
  }

  /** Expert: returns an IndexReader reading the index in
   *  the given Directory, using a specific commit and with
   *  a custom {@link IndexDeletionPolicy}.  You should pass
   *  readOnly=true, since it gives much better concurrent
   *  performance, unless you intend to do write operations
   *  (delete documents or change norms) with the reader.
   * @param commit the specific {@link IndexCommit} to open;
   * see {@link IndexReader#listCommits} to list all commits
   * in a directory
   * @param deletionPolicy a custom deletion policy (only used
   *  if you use this reader to perform deletes or to set
   *  norms); see {@link IndexWriter} for details.
   * @param readOnly true if no changes (deletions, norms) will be made with this IndexReader
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #open(IndexCommit)} instead
   */
  @Deprecated
  public static IndexReader open(final IndexCommit commit, IndexDeletionPolicy deletionPolicy, boolean readOnly) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), deletionPolicy, commit, readOnly, DEFAULT_TERMS_INDEX_DIVISOR);
  }

  /** Expert: returns an IndexReader reading the index in
   *  the given Directory, using a specific commit and with
   *  a custom {@link IndexDeletionPolicy}.  You should pass
   *  readOnly=true, since it gives much better concurrent
   *  performance, unless you intend to do write operations
   *  (delete documents or change norms) with the reader.
   * @param commit the specific {@link IndexCommit} to open;
   * see {@link IndexReader#listCommits} to list all commits
   * in a directory
   * @param deletionPolicy a custom deletion policy (only used
   *  if you use this reader to perform deletes or to set
   *  norms); see {@link IndexWriter} for details.
   * @param readOnly true if no changes (deletions, norms) will be made with this IndexReader
   * @param termInfosIndexDivisor Subsamples which indexed
   *  terms are loaded into RAM. This has the same effect as {@link
   *  IndexWriter#setTermIndexInterval} except that setting
   *  must be done at indexing time while this setting can be
   *  set per reader.  When set to N, then one in every
   *  N*termIndexInterval terms in the index is loaded into
   *  memory.  By setting this to a value > 1 you can reduce
   *  memory usage, at the expense of higher latency when
   *  loading a TermInfo.  The default value is 1.  Set this
   *  to -1 to skip loading the terms index entirely. This is only useful in 
   *  advanced situations when you will only .next() through all terms; 
   *  attempts to seek will hit an exception.
   *  
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #open(IndexCommit,int)} instead
   */
  @Deprecated
  public static IndexReader open(final IndexCommit commit, IndexDeletionPolicy deletionPolicy, boolean readOnly, int termInfosIndexDivisor) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), deletionPolicy, commit, readOnly, termInfosIndexDivisor);
  }

  /** Expert: Returns a IndexReader reading the index in the given
   *  Director and given termInfosIndexDivisor
   * @param directory the index directory
   * @param termInfosIndexDivisor Subsamples which indexed
   *  terms are loaded into RAM. This has the same effect as {@link
   *  IndexWriterConfig#setTermIndexInterval} except that setting
   *  must be done at indexing time while this setting can be
   *  set per reader.  When set to N, then one in every
   *  N*termIndexInterval terms in the index is loaded into
   *  memory.  By setting this to a value > 1 you can reduce
   *  memory usage, at the expense of higher latency when
   *  loading a TermInfo.  The default value is 1.  Set this
   *  to -1 to skip loading the terms index entirely.
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public static IndexReader open(final Directory directory, int termInfosIndexDivisor) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, null, null, true, termInfosIndexDivisor);
  }

  /** Expert: returns an IndexReader reading the index in the given
   *  {@link IndexCommit} and termInfosIndexDivisor.
   * @param commit the commit point to open
   * @param termInfosIndexDivisor Subsamples which indexed
   *  terms are loaded into RAM. This has the same effect as {@link
   *  IndexWriterConfig#setTermIndexInterval} except that setting
   *  must be done at indexing time while this setting can be
   *  set per reader.  When set to N, then one in every
   *  N*termIndexInterval terms in the index is loaded into
   *  memory.  By setting this to a value > 1 you can reduce
   *  memory usage, at the expense of higher latency when
   *  loading a TermInfo.  The default value is 1.  Set this
   *  to -1 to skip loading the terms index entirely.
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public static IndexReader open(final IndexCommit commit, int termInfosIndexDivisor) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), null, commit, true, termInfosIndexDivisor);
  }

  /**
   * If the index has changed since the provided reader was
   * opened, open and return a new reader; else, return
   * null.  The new reader, if not null, will be the same
   * type of reader as the previous one, ie an NRT reader
   * will open a new NRT reader, a MultiReader will open a
   * new MultiReader,  etc.
   *
   * <p>This method is typically far less costly than opening a
   * fully new <code>IndexReader</code> as it shares
   * resources (for example sub-readers) with the provided
   * <code>IndexReader</code>, when possible.
   *
   * <p>The provided reader is not closed (you are responsible
   * for doing so); if a new reader is returned you also
   * must eventually close it.  Be sure to never close a
   * reader while other threads are still using it; see
   * {@link SearcherManager} to simplify managing this.
   *
   * <p>If a new reader is returned, it's safe to make changes
   * (deletions, norms) with it.  All shared mutable state
   * with the old reader uses "copy on write" semantics to
   * ensure the changes are not seen by other readers.
   *
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @return null if there are no changes; else, a new
   * IndexReader instance which you must eventually close
   */  
  public static IndexReader openIfChanged(IndexReader oldReader) throws IOException {
    if (oldReader.hasNewReopenAPI1) {
      final IndexReader newReader = oldReader.doOpenIfChanged();
      assert newReader != oldReader;
      return newReader;
    } else {
      final IndexReader newReader = oldReader.reopen();
      if (newReader == oldReader) {
        return null;
      } else {
        return newReader;
      }
    }
  }

  /**
   * If the index has changed since the provided reader was
   * opened, open and return a new reader, with the
   * specified <code>readOnly</code>; else, return
   * null.
   *
   * @see #openIfChanged(IndexReader)
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #openIfChanged(IndexReader)} instead
   */
  @Deprecated
  public static IndexReader openIfChanged(IndexReader oldReader, boolean readOnly) throws IOException {
    if (oldReader.hasNewReopenAPI2) {
      final IndexReader newReader = oldReader.doOpenIfChanged(readOnly);
      assert newReader != oldReader;
      return newReader;
    } else {
      final IndexReader newReader = oldReader.reopen(readOnly);
      if (newReader == oldReader) {
        return null;
      } else {
        return newReader;
      }
    }
  }

  /**
   * If the IndexCommit differs from what the
   * provided reader is searching, or the provided reader is
   * not already read-only, open and return a new
   * <code>readOnly=true</code> reader; else, return null.
   *
   * @see #openIfChanged(IndexReader)
   */
  // TODO: should you be able to specify readOnly?
  public static IndexReader openIfChanged(IndexReader oldReader, IndexCommit commit) throws IOException {
    if (oldReader.hasNewReopenAPI3) {
      final IndexReader newReader = oldReader.doOpenIfChanged(commit);
      assert newReader != oldReader;
      return newReader;
    } else {
      final IndexReader newReader = oldReader.reopen(commit);
      if (newReader == oldReader) {
        return null;
      } else {
        return newReader;
      }
    }
  }

  /**
   * Expert: If there changes (committed or not) in the
   * {@link IndexWriter} versus what the provided reader is
   * searching, then open and return a new read-only
   * IndexReader searching both committed and uncommitted
   * changes from the writer; else, return null (though, the
   * current implementation never returns null).
   *
   * <p>This provides "near real-time" searching, in that
   * changes made during an {@link IndexWriter} session can be
   * quickly made available for searching without closing
   * the writer nor calling {@link IndexWriter#commit}.
   *
   * <p>It's <i>near</i> real-time because there is no hard
   * guarantee on how quickly you can get a new reader after
   * making changes with IndexWriter.  You'll have to
   * experiment in your situation to determine if it's
   * fast enough.  As this is a new and experimental
   * feature, please report back on your findings so we can
   * learn, improve and iterate.</p>
   *
   * <p>The very first time this method is called, this
   * writer instance will make every effort to pool the
   * readers that it opens for doing merges, applying
   * deletes, etc.  This means additional resources (RAM,
   * file descriptors, CPU time) will be consumed.</p>
   *
   * <p>For lower latency on reopening a reader, you should
   * call {@link IndexWriterConfig#setMergedSegmentWarmer} to
   * pre-warm a newly merged segment before it's committed
   * to the index.  This is important for minimizing
   * index-to-search delay after a large merge.  </p>
   *
   * <p>If an addIndexes* call is running in another thread,
   * then this reader will only search those segments from
   * the foreign index that have been successfully copied
   * over, so far.</p>
   *
   * <p><b>NOTE</b>: Once the writer is closed, any
   * outstanding readers may continue to be used.  However,
   * if you attempt to reopen any of those readers, you'll
   * hit an {@link AlreadyClosedException}.</p>
   *
   * @return IndexReader that covers entire index plus all
   * changes made so far by this IndexWriter instance, or
   * null if there are no new changes
   *
   * @param writer The IndexWriter to open from
   *
   * @param applyAllDeletes If true, all buffered deletes will
   * be applied (made visible) in the returned reader.  If
   * false, the deletes are not applied but remain buffered
   * (in IndexWriter) so that they will be applied in the
   * future.  Applying deletes can be costly, so if your app
   * can tolerate deleted documents being returned you might
   * gain some performance by passing false.
   *
   * @throws IOException
   *
   * @lucene.experimental
   */
  public static IndexReader openIfChanged(IndexReader oldReader, IndexWriter writer, boolean applyAllDeletes) throws IOException {
    if (oldReader.hasNewReopenAPI4) {
      final IndexReader newReader = oldReader.doOpenIfChanged(writer, applyAllDeletes);
      assert newReader != oldReader;
      return newReader;
    } else {
      final IndexReader newReader = oldReader.reopen(writer, applyAllDeletes);
      if (newReader == oldReader) {
        return null;
      } else {
        return newReader;
      }
    }
  }

  /**
   * Refreshes an IndexReader if the index has changed since this instance 
   * was (re)opened. 
   * <p>
   * Opening an IndexReader is an expensive operation. This method can be used
   * to refresh an existing IndexReader to reduce these costs. This method 
   * tries to only load segments that have changed or were created after the 
   * IndexReader was (re)opened.
   * <p>
   * If the index has not changed since this instance was (re)opened, then this
   * call is a NOOP and returns this instance. Otherwise, a new instance is 
   * returned. The old instance is <b>not</b> closed and remains usable.<br>
   * <p>   
   * If the reader is reopened, even though they share
   * resources internally, it's safe to make changes
   * (deletions, norms) with the new reader.  All shared
   * mutable state obeys "copy on write" semantics to ensure
   * the changes are not seen by other readers.
   * <p>
   * You can determine whether a reader was actually reopened by comparing the
   * old instance with the instance returned by this method: 
   * <pre>
   * IndexReader reader = ... 
   * ...
   * IndexReader newReader = r.reopen();
   * if (newReader != reader) {
   * ...     // reader was reopened
   *   reader.close(); 
   * }
   * reader = newReader;
   * ...
   * </pre>
   *
   * Be sure to synchronize that code so that other threads,
   * if present, can never use reader after it has been
   * closed and before it's switched to newReader.
   *
   * <p><b>NOTE</b>: If this reader is a near real-time
   * reader (obtained from {@link IndexWriter#getReader()},
   * reopen() will simply call writer.getReader() again for
   * you, though this may change in the future.
   * 
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Use {@link #openIfChanged(IndexReader)} instead
   */
  @Deprecated
  public IndexReader reopen() throws CorruptIndexException, IOException {
    final IndexReader newReader = IndexReader.openIfChanged(this);
    if (newReader == null) {
      return this;
    } else {
      return newReader;
    }
  }

  /** Just like {@link #reopen()}, except you can change the
   *  readOnly of the original reader.  If the index is
   *  unchanged but readOnly is different then a new reader
   *  will be returned.
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #openIfChanged(IndexReader)} instead
   */
  @Deprecated
  public IndexReader reopen(boolean openReadOnly) throws CorruptIndexException, IOException {
    final IndexReader newReader = IndexReader.openIfChanged(this, openReadOnly);
    if (newReader == null) {
      return this;
    } else {
      return newReader;
    }
  }

  /** Expert: reopen this reader on a specific commit point.
   *  This always returns a readOnly reader.  If the
   *  specified commit point matches what this reader is
   *  already on, and this reader is already readOnly, then
   *  this same instance is returned; if it is not already
   *  readOnly, a readOnly clone is returned.
   * @deprecated Use {@link #openIfChanged(IndexReader,IndexCommit)} instead
   */
  @Deprecated
  public IndexReader reopen(IndexCommit commit) throws CorruptIndexException, IOException {
    final IndexReader newReader = IndexReader.openIfChanged(this, commit);
    if (newReader == null) {
      return this;
    } else {
      return newReader;
    }
  }

  /**
   * Expert: returns a readonly reader, covering all
   * committed as well as un-committed changes to the index.
   * This provides "near real-time" searching, in that
   * changes made during an IndexWriter session can be
   * quickly made available for searching without closing
   * the writer nor calling {@link #commit}.
   *
   * <p>Note that this is functionally equivalent to calling
   * {#flush} (an internal IndexWriter operation) and then using {@link IndexReader#open} to
   * open a new reader.  But the turnaround time of this
   * method should be faster since it avoids the potentially
   * costly {@link #commit}.</p>
   *
   * <p>You must close the {@link IndexReader} returned by
   * this method once you are done using it.</p>
   *
   * <p>It's <i>near</i> real-time because there is no hard
   * guarantee on how quickly you can get a new reader after
   * making changes with IndexWriter.  You'll have to
   * experiment in your situation to determine if it's
   * fast enough.  As this is a new and experimental
   * feature, please report back on your findings so we can
   * learn, improve and iterate.</p>
   *
   * <p>The resulting reader supports {@link
   * IndexReader#reopen}, but that call will simply forward
   * back to this method (though this may change in the
   * future).</p>
   *
   * <p>The very first time this method is called, this
   * writer instance will make every effort to pool the
   * readers that it opens for doing merges, applying
   * deletes, etc.  This means additional resources (RAM,
   * file descriptors, CPU time) will be consumed.</p>
   *
   * <p>For lower latency on reopening a reader, you should
   * call {@link IndexWriterConfig#setMergedSegmentWarmer} to
   * pre-warm a newly merged segment before it's committed
   * to the index.  This is important for minimizing
   * index-to-search delay after a large merge.  </p>
   *
   * <p>If an addIndexes* call is running in another thread,
   * then this reader will only search those segments from
   * the foreign index that have been successfully copied
   * over, so far</p>.
   *
   * <p><b>NOTE</b>: Once the writer is closed, any
   * outstanding readers may continue to be used.  However,
   * if you attempt to reopen any of those readers, you'll
   * hit an {@link AlreadyClosedException}.</p>
   *
   * @return IndexReader that covers entire index plus all
   * changes made so far by this IndexWriter instance
   *
   * @param writer The IndexWriter to open from
   * @param applyAllDeletes If true, all buffered deletes will
   * be applied (made visible) in the returned reader.  If
   * false, the deletes are not applied but remain buffered
   * (in IndexWriter) so that they will be applied in the
   * future.  Applying deletes can be costly, so if your app
   * can tolerate deleted documents being returned you might
   * gain some performance by passing false.
   *
   * @throws IOException
   *
   * @lucene.experimental
   * @deprecated Use {@link #openIfChanged(IndexReader,IndexWriter,boolean)} instead
   */
  @Deprecated
  public IndexReader reopen(IndexWriter writer, boolean applyAllDeletes) throws CorruptIndexException, IOException {
    final IndexReader newReader = IndexReader.openIfChanged(this, writer, applyAllDeletes);
    if (newReader == null) {
      return this;
    } else {
      return newReader;
    }
  }

  /**
   * If the index has changed since it was opened, open and return a new reader;
   * else, return {@code null}.
   * 
   * @see #openIfChanged(IndexReader)
   */
  protected IndexReader doOpenIfChanged() throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not support reopen().");
  }
  
  /**
   * If the index has changed since it was opened, open and return a new reader;
   * else, return {@code null}.
   * 
   * @see #openIfChanged(IndexReader, boolean)
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #doOpenIfChanged()} instead
   */
  @Deprecated
  protected IndexReader doOpenIfChanged(boolean openReadOnly) throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not support reopen().");
  }

  /**
   * If the index has changed since it was opened, open and return a new reader;
   * else, return {@code null}.
   * 
   * @see #openIfChanged(IndexReader, IndexCommit)
   */
  protected IndexReader doOpenIfChanged(final IndexCommit commit) throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not support reopen(IndexCommit).");
  }

  /**
   * If the index has changed since it was opened, open and return a new reader;
   * else, return {@code null}.
   * 
   * @see #openIfChanged(IndexReader, IndexWriter, boolean)
   */
  protected IndexReader doOpenIfChanged(IndexWriter writer, boolean applyAllDeletes) throws CorruptIndexException, IOException {
    return writer.getReader(applyAllDeletes);
  }

  /**
   * Efficiently clones the IndexReader (sharing most
   * internal state).
   * <p>
   * On cloning a reader with pending changes (deletions,
   * norms), the original reader transfers its write lock to
   * the cloned reader.  This means only the cloned reader
   * may make further changes to the index, and commit the
   * changes to the index on close, but the old reader still
   * reflects all changes made up until it was cloned.
   * <p>
   * Like {@link #openIfChanged(IndexReader)}, it's safe to make changes to
   * either the original or the cloned reader: all shared
   * mutable state obeys "copy on write" semantics to ensure
   * the changes are not seen by other readers.
   * <p>
   */
  @Override
  public synchronized Object clone() {
    throw new UnsupportedOperationException("This reader does not implement clone()");
  }
  
  /**
   * Clones the IndexReader and optionally changes readOnly.  A readOnly 
   * reader cannot open a writeable reader.  
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link #clone()} instead.
   */
  @Deprecated
  public synchronized IndexReader clone(boolean openReadOnly) throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not implement clone()");
  }

  /** 
   * Returns the directory associated with this index.  The Default 
   * implementation returns the directory specified by subclasses when 
   * delegating to the IndexReader(Directory) constructor, or throws an 
   * UnsupportedOperationException if one was not specified.
   * @throws UnsupportedOperationException if no directory
   */
  public Directory directory() {
    ensureOpen();
    throw new UnsupportedOperationException("This reader does not support this method.");  
  }

  /**
   * Returns the time the index in the named directory was last modified. 
   * Do not use this to check whether the reader is still up-to-date, use
   * {@link #isCurrent()} instead. 
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public static long lastModified(final Directory directory2) throws CorruptIndexException, IOException {
    return ((Long) new SegmentInfos.FindSegmentsFile(directory2) {
        @Override
        public Object doBody(String segmentFileName) throws IOException {
          return Long.valueOf(directory2.fileModified(segmentFileName));
        }
      }.run()).longValue();
  }

  /**
   * Reads version number from segments files. The version number is
   * initialized with a timestamp and then increased by one for each change of
   * the index.
   * 
   * @param directory where the index resides.
   * @return version number.
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public static long getCurrentVersion(Directory directory) throws CorruptIndexException, IOException {
    return SegmentInfos.readCurrentVersion(directory);
  }

  /**
   * Reads commitUserData, previously passed to {@link
   * IndexWriter#commit(Map)}, from current index
   * segments file.  This will return null if {@link
   * IndexWriter#commit(Map)} has never been called for
   * this index.
   * 
   * @param directory where the index resides.
   * @return commit userData.
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   *
   * @see #getCommitUserData()
   */
  public static Map<String,String> getCommitUserData(Directory directory) throws CorruptIndexException, IOException {
    return SegmentInfos.readCurrentUserData(directory);
  }

  /**
   * Version number when this IndexReader was opened. Not
   * implemented in the IndexReader base class.
   *
   * <p>If this reader is based on a Directory (ie, was
   * created by calling {@link #open}, or {@link #openIfChanged} on
   * a reader based on a Directory), then this method
   * returns the version recorded in the commit that the
   * reader opened.  This version is advanced every time
   * {@link IndexWriter#commit} is called.</p>
   *
   * <p>If instead this reader is a near real-time reader
   * (ie, obtained by a call to {@link
   * IndexWriter#getReader}, or by calling {@link #openIfChanged}
   * on a near real-time reader), then this method returns
   * the version of the last commit done by the writer.
   * Note that even as further changes are made with the
   * writer, the version will not changed until a commit is
   * completed.  Thus, you should not rely on this method to
   * determine when a near real-time reader should be
   * opened.  Use {@link #isCurrent} instead.</p>
   *
   * @throws UnsupportedOperationException unless overridden in subclass
   */
  public long getVersion() {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }

  /**
   * Retrieve the String userData optionally passed to
   * IndexWriter#commit.  This will return null if {@link
   * IndexWriter#commit(Map)} has never been called for
   * this index.
   *
   * @see #getCommitUserData(Directory)
   */
  public Map<String,String> getCommitUserData() {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }


  /**
   * Check whether any new changes have occurred to the
   * index since this reader was opened.
   *
   * <p>If this reader is based on a Directory (ie, was
   * created by calling {@link #open}, or {@link #openIfChanged} on
   * a reader based on a Directory), then this method checks
   * if any further commits (see {@link IndexWriter#commit}
   * have occurred in that directory).</p>
   *
   * <p>If instead this reader is a near real-time reader
   * (ie, obtained by a call to {@link
   * IndexWriter#getReader}, or by calling {@link #openIfChanged}
   * on a near real-time reader), then this method checks if
   * either a new commmit has occurred, or any new
   * uncommitted changes have taken place via the writer.
   * Note that even if the writer has only performed
   * merging, this method will still return false.</p>
   *
   * <p>In any event, if this returns false, you should call
   * {@link #openIfChanged} to get a new reader that sees the
   * changes.</p>
   *
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException           if there is a low-level IO error
   * @throws UnsupportedOperationException unless overridden in subclass
   */
  public boolean isCurrent() throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }

  /** @deprecated Check segment count using {@link
   *  #getSequentialSubReaders} instead. */
  @Deprecated
  public boolean isOptimized() {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }
  
  /**
   * Return an array of term frequency vectors for the specified document.
   * The array contains a vector for each vectorized field in the document.
   * Each vector contains terms and frequencies for all terms in a given vectorized field.
   * If no such fields existed, the method returns null. The term vectors that are
   * returned may either be of type {@link TermFreqVector}
   * or of type {@link TermPositionVector} if
   * positions or offsets have been stored.
   * 
   * @param docNumber document for which term frequency vectors are returned
   * @return array of term frequency vectors. May be null if no term vectors have been
   *  stored for the specified document.
   * @throws IOException if index cannot be accessed
   * @see org.apache.lucene.document.Field.TermVector
   */
  abstract public TermFreqVector[] getTermFreqVectors(int docNumber)
          throws IOException;


  /**
   * Return a term frequency vector for the specified document and field. The
   * returned vector contains terms and frequencies for the terms in
   * the specified field of this document, if the field had the storeTermVector
   * flag set. If termvectors had been stored with positions or offsets, a 
   * {@link TermPositionVector} is returned.
   * 
   * @param docNumber document for which the term frequency vector is returned
   * @param field field for which the term frequency vector is returned.
   * @return term frequency vector May be null if field does not exist in the specified
   * document or term vector was not stored.
   * @throws IOException if index cannot be accessed
   * @see org.apache.lucene.document.Field.TermVector
   */
  abstract public TermFreqVector getTermFreqVector(int docNumber, String field)
          throws IOException;

  /**
   * Load the Term Vector into a user-defined data structure instead of relying on the parallel arrays of
   * the {@link TermFreqVector}.
   * @param docNumber The number of the document to load the vector for
   * @param field The name of the field to load
   * @param mapper The {@link TermVectorMapper} to process the vector.  Must not be null
   * @throws IOException if term vectors cannot be accessed or if they do not exist on the field and doc. specified.
   * 
   */
  abstract public void getTermFreqVector(int docNumber, String field, TermVectorMapper mapper) throws IOException;

  /**
   * Map all the term vectors for all fields in a Document
   * @param docNumber The number of the document to load the vector for
   * @param mapper The {@link TermVectorMapper} to process the vector.  Must not be null
   * @throws IOException if term vectors cannot be accessed or if they do not exist on the field and doc. specified.
   */
  abstract public void getTermFreqVector(int docNumber, TermVectorMapper mapper) throws IOException;

  /**
   * Returns <code>true</code> if an index exists at the specified directory.
   * @param  directory the directory to check for an index
   * @return <code>true</code> if an index exists; <code>false</code> otherwise
   * @throws IOException if there is a problem with accessing the index
   */
  public static boolean indexExists(Directory directory) throws IOException {
    try {
      new SegmentInfos().read(directory);
      return true;
    } catch (IOException ioe) {
      return false;
    }
  }

  /** Returns the number of documents in this index. */
  public abstract int numDocs();

  /** Returns one greater than the largest possible document number.
   * This may be used to, e.g., determine how big to allocate an array which
   * will have an element for every document number in an index.
   */
  public abstract int maxDoc();

  /** Returns the number of deleted documents. */
  public final int numDeletedDocs() {
    return maxDoc() - numDocs();
  }

  /**
   * Returns the stored fields of the <code>n</code><sup>th</sup>
   * <code>Document</code> in this index.
   * <p>
   * <b>NOTE:</b> for performance reasons, this method does not check if the
   * requested document is deleted, and therefore asking for a deleted document
   * may yield unspecified results. Usually this is not required, however you
   * can call {@link #isDeleted(int)} with the requested document ID to verify
   * the document is not deleted.
   * 
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public final Document document(int n) throws CorruptIndexException, IOException {
    ensureOpen();
    if (n < 0 || n >= maxDoc()) {
      throw new IllegalArgumentException("docID must be >= 0 and < maxDoc=" + maxDoc() + " (got docID=" + n + ")");
    }
    return document(n, null);
  }

  /**
   * Get the {@link org.apache.lucene.document.Document} at the <code>n</code>
   * <sup>th</sup> position. The {@link FieldSelector} may be used to determine
   * what {@link org.apache.lucene.document.Field}s to load and how they should
   * be loaded. <b>NOTE:</b> If this Reader (more specifically, the underlying
   * <code>FieldsReader</code>) is closed before the lazy
   * {@link org.apache.lucene.document.Field} is loaded an exception may be
   * thrown. If you want the value of a lazy
   * {@link org.apache.lucene.document.Field} to be available after closing you
   * must explicitly load it or fetch the Document again with a new loader.
   * <p>
   * <b>NOTE:</b> for performance reasons, this method does not check if the
   * requested document is deleted, and therefore asking for a deleted document
   * may yield unspecified results. Usually this is not required, however you
   * can call {@link #isDeleted(int)} with the requested document ID to verify
   * the document is not deleted.
   * 
   * @param n Get the document at the <code>n</code><sup>th</sup> position
   * @param fieldSelector The {@link FieldSelector} to use to determine what
   *        Fields should be loaded on the Document. May be null, in which case
   *        all Fields will be loaded.
   * @return The stored fields of the
   *         {@link org.apache.lucene.document.Document} at the nth position
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @see org.apache.lucene.document.Fieldable
   * @see org.apache.lucene.document.FieldSelector
   * @see org.apache.lucene.document.SetBasedFieldSelector
   * @see org.apache.lucene.document.LoadFirstFieldSelector
   */
  // TODO (1.5): When we convert to JDK 1.5 make this Set<String>
  public abstract Document document(int n, FieldSelector fieldSelector) throws CorruptIndexException, IOException;
  
  /** Returns true if document <i>n</i> has been deleted */
  public abstract boolean isDeleted(int n);

  /** Returns true if any documents have been deleted */
  public abstract boolean hasDeletions();

  /** Returns true if there are norms stored for this field. */
  public boolean hasNorms(String field) throws IOException {
    // backward compatible implementation.
    // SegmentReader has an efficient implementation.
    ensureOpen();
    return norms(field) != null;
  }

  /** Returns the byte-encoded normalization factor for the named field of
   *  every document.  This is used by the search code to score documents.
   *  Returns null if norms were not indexed for this field.
   *
   * @see org.apache.lucene.document.Field#setBoost(float)
   */
  public abstract byte[] norms(String field) throws IOException;

  /** Reads the byte-encoded normalization factor for the named field of every
   *  document.  This is used by the search code to score documents.
   *
   * @see org.apache.lucene.document.Field#setBoost(float)
   */
  public abstract void norms(String field, byte[] bytes, int offset)
    throws IOException;

  /** Expert: Resets the normalization factor for the named field of the named
   * document.  The norm represents the product of the field's {@link
   * org.apache.lucene.document.Fieldable#setBoost(float) boost} and its {@link Similarity#lengthNorm(String,
   * int) length normalization}.  Thus, to preserve the length normalization
   * values when resetting this, one should base the new value upon the old.
   *
   * <b>NOTE:</b> If this field does not index norms, then
   * this method throws {@link IllegalStateException}.
   *
   * @see #norms(String)
   * @see Similarity#decodeNormValue(byte)
   * @throws StaleReaderException if the index has changed
   *  since this reader was opened
   * @throws CorruptIndexException if the index is corrupt
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws IOException if there is a low-level IO error
   * @throws IllegalStateException if the field does not index norms
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  public final synchronized  void setNorm(int doc, String field, byte value)
          throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doSetNorm(doc, field, value);
  }

  /** Implements setNorm in subclass.
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  protected abstract void doSetNorm(int doc, String field, byte value)
          throws CorruptIndexException, IOException;

  /** Expert: Resets the normalization factor for the named field of the named
   * document.
   *
   * @see #norms(String)
   * @see Similarity#decodeNormValue(byte)
   * 
   * @throws StaleReaderException if the index has changed
   *  since this reader was opened
   * @throws CorruptIndexException if the index is corrupt
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  public final void setNorm(int doc, String field, float value)
          throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    setNorm(doc, field, Similarity.getDefault().encodeNormValue(value));
  }

  /** Returns an enumeration of all the terms in the index. The
   * enumeration is ordered by Term.compareTo(). Each term is greater
   * than all that precede it in the enumeration. Note that after
   * calling terms(), {@link TermEnum#next()} must be called
   * on the resulting enumeration before calling other methods such as
   * {@link TermEnum#term()}.
   * @throws IOException if there is a low-level IO error
   */
  public abstract TermEnum terms() throws IOException;

  /** Returns an enumeration of all terms starting at a given term. If
   * the given term does not exist, the enumeration is positioned at the
   * first term greater than the supplied term. The enumeration is
   * ordered by Term.compareTo(). Each term is greater than all that
   * precede it in the enumeration.
   * @throws IOException if there is a low-level IO error
   */
  public abstract TermEnum terms(Term t) throws IOException;

  /** Returns the number of documents containing the term <code>t</code>.
   * @throws IOException if there is a low-level IO error
   */
  public abstract int docFreq(Term t) throws IOException;

  /** Returns an enumeration of all the documents which contain
   * <code>term</code>. For each document, the document number, the frequency of
   * the term in that document is also provided, for use in
   * search scoring.  If term is null, then all non-deleted
   * docs are returned with freq=1.
   * Thus, this method implements the mapping:
   * <p><ul>
   * Term &nbsp;&nbsp; =&gt; &nbsp;&nbsp; &lt;docNum, freq&gt;<sup>*</sup>
   * </ul>
   * <p>The enumeration is ordered by document number.  Each document number
   * is greater than all that precede it in the enumeration.
   * @throws IOException if there is a low-level IO error
   */
  public TermDocs termDocs(Term term) throws IOException {
    ensureOpen();
    TermDocs termDocs = termDocs();
    termDocs.seek(term);
    return termDocs;
  }

  /** Returns an unpositioned {@link TermDocs} enumerator.
   * <p>
   * Note: the TermDocs returned is unpositioned. Before using it, ensure
   * that you first position it with {@link TermDocs#seek(Term)} or 
   * {@link TermDocs#seek(TermEnum)}.
   * 
   * @throws IOException if there is a low-level IO error
   */
  public abstract TermDocs termDocs() throws IOException;

  /** Returns an enumeration of all the documents which contain
   * <code>term</code>.  For each document, in addition to the document number
   * and frequency of the term in that document, a list of all of the ordinal
   * positions of the term in the document is available.  Thus, this method
   * implements the mapping:
   *
   * <p><ul>
   * Term &nbsp;&nbsp; =&gt; &nbsp;&nbsp; &lt;docNum, freq,
   * &lt;pos<sub>1</sub>, pos<sub>2</sub>, ...
   * pos<sub>freq-1</sub>&gt;
   * &gt;<sup>*</sup>
   * </ul>
   * <p> This positional information facilitates phrase and proximity searching.
   * <p>The enumeration is ordered by document number.  Each document number is
   * greater than all that precede it in the enumeration.
   * @throws IOException if there is a low-level IO error
   */
  public final TermPositions termPositions(Term term) throws IOException {
    ensureOpen();
    TermPositions termPositions = termPositions();
    termPositions.seek(term);
    return termPositions;
  }

  /** Returns an unpositioned {@link TermPositions} enumerator.
   * @throws IOException if there is a low-level IO error
   */
  public abstract TermPositions termPositions() throws IOException;



  /** Deletes the document numbered <code>docNum</code>.  Once a document is
   * deleted it will not appear in TermDocs or TermPostitions enumerations.
   * Attempts to read its field with the {@link #document}
   * method will result in an error.  The presence of this document may still be
   * reflected in the {@link #docFreq} statistic, though
   * this will be corrected eventually as the index is further modified.
   *
   * @throws StaleReaderException if the index has changed
   * since this reader was opened
   * @throws CorruptIndexException if the index is corrupt
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link IndexWriter#deleteDocuments(Term)} instead
   */
  @Deprecated
  public final synchronized void deleteDocument(int docNum) throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doDelete(docNum);
  }


  /** Implements deletion of the document numbered <code>docNum</code>.
   * Applications should call {@link #deleteDocument(int)} or {@link #deleteDocuments(Term)}.
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link IndexWriter#deleteDocuments(Term)} instead
   */
  @Deprecated
  protected abstract void doDelete(int docNum) throws CorruptIndexException, IOException;


  /** Deletes all documents that have a given <code>term</code> indexed.
   * This is useful if one uses a document field to hold a unique ID string for
   * the document.  Then to delete such a document, one merely constructs a
   * term with the appropriate field and the unique ID string as its text and
   * passes it to this method.
   * See {@link #deleteDocument(int)} for information about when this deletion will 
   * become effective.
   *
   * @return the number of documents deleted
   * @throws StaleReaderException if the index has changed
   *  since this reader was opened
   * @throws CorruptIndexException if the index is corrupt
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@link IndexWriter#deleteDocuments(Term)} instead
   */
  @Deprecated
  public final int deleteDocuments(Term term) throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    TermDocs docs = termDocs(term);
    if (docs == null) return 0;
    int n = 0;
    try {
      while (docs.next()) {
        deleteDocument(docs.doc());
        n++;
      }
    } finally {
      docs.close();
    }
    return n;
  }

  /** Undeletes all documents currently marked as deleted in
   * this index.
   *
   * <p>NOTE: this method can only recover documents marked
   * for deletion but not yet removed from the index; when
   * and how Lucene removes deleted documents is an
   * implementation detail, subject to change from release
   * to release.  However, you can use {@link
   * #numDeletedDocs} on the current IndexReader instance to
   * see how many documents will be un-deleted.
   *
   * @throws StaleReaderException if the index has changed
   *  since this reader was opened
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  public final synchronized void undeleteAll() throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doUndeleteAll();
  }

  /** Implements actual undeleteAll() in subclass.
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  protected abstract void doUndeleteAll() throws CorruptIndexException, IOException;

  /** Does nothing by default. Subclasses that require a write lock for
   *  index modifications must implement this method.
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  protected synchronized void acquireWriteLock() throws IOException {
    /* NOOP */
  }
  
  /**
   * 
   * @throws IOException
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  public final synchronized void flush() throws IOException {
    ensureOpen();
    commit();
  }

  /**
   * @param commitUserData Opaque Map (String -> String)
   *  that's recorded into the segments file in the index,
   *  and retrievable by {@link
   *  IndexReader#getCommitUserData}.
   * @throws IOException
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  public final synchronized void flush(Map<String, String> commitUserData) throws IOException {
    ensureOpen();
    commit(commitUserData);
  }
  
  /**
   * Commit changes resulting from delete, undeleteAll, or
   * setNorm operations
   *
   * If an exception is hit, then either no changes or all
   * changes will have been committed to the index
   * (transactional semantics).
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  protected final synchronized void commit() throws IOException {
    commit(null);
  }
  
  /**
   * Commit changes resulting from delete, undeleteAll, or
   * setNorm operations
   *
   * If an exception is hit, then either no changes or all
   * changes will have been committed to the index
   * (transactional semantics).
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  public final synchronized void commit(Map<String, String> commitUserData) throws IOException {
    // Don't call ensureOpen since we commit() on close
    doCommit(commitUserData);
    hasChanges = false;
  }

  /** Implements commit.
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  protected abstract void doCommit(Map<String, String> commitUserData) throws IOException;

  /**
   * Closes files associated with this index.
   * Also saves any new deletions to disk.
   * No other methods should be called after this has been called.
   * @throws IOException if there is a low-level IO error
   */
  public final synchronized void close() throws IOException {
    if (!closed) {
      decRef();
      closed = true;
    }
  }
  
  /** Implements close. */
  protected abstract void doClose() throws IOException;


  /**
   * Get a list of unique field names that exist in this index and have the specified
   * field option information.
   * @param fldOption specifies which field option should be available for the returned fields
   * @return Collection of Strings indicating the names of the fields.
   * @see IndexReader.FieldOption
   */
  public abstract Collection<String> getFieldNames(FieldOption fldOption);

  /**
   * Expert: return the IndexCommit that this reader has
   * opened.  This method is only implemented by those
   * readers that correspond to a Directory with its own
   * segments_N file.
   *
   * @lucene.experimental
   */
  public IndexCommit getIndexCommit() throws IOException {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }
  
  /**
   * Prints the filename and size of each file within a given compound file.
   * Add the -extract flag to extract files to the current working directory.
   * In order to make the extracted version of the index work, you have to copy
   * the segments file from the compound index into the directory where the extracted files are stored.
   * @param args Usage: org.apache.lucene.index.IndexReader [-extract] &lt;cfsfile&gt;
   */
  public static void main(String [] args) {
    String filename = null;
    boolean extract = false;
    String dirImpl = null;

    int j = 0;
    while(j < args.length) {
      String arg = args[j];
      if ("-extract".equals(arg)) {
        extract = true;
      } else if ("-dir-impl".equals(arg)) {
        if (j == args.length - 1) {
          System.out.println("ERROR: missing value for -dir-impl option");
          System.exit(1);
        }
        j++;
        dirImpl = args[j];
      } else if (filename == null) {
        filename = arg;
      }
      j++;
    }

    if (filename == null) {
      System.out.println("Usage: org.apache.lucene.index.IndexReader [-extract] [-dir-impl X] <cfsfile>");
      return;
    }

    Directory dir = null;
    CompoundFileReader cfr = null;

    try {
      File file = new File(filename);
      String dirname = file.getAbsoluteFile().getParent();
      filename = file.getName();
      if (dirImpl == null) {
        dir = FSDirectory.open(new File(dirname));
      } else {
        dir = CommandLineUtil.newFSDirectory(dirImpl, new File(dirname));
      }
      
      cfr = new CompoundFileReader(dir, filename);

      String [] files = cfr.listAll();
      ArrayUtil.mergeSort(files);   // sort the array of filename so that the output is more readable

      for (int i = 0; i < files.length; ++i) {
        long len = cfr.fileLength(files[i]);

        if (extract) {
          System.out.println("extract " + files[i] + " with " + len + " bytes to local directory...");
          IndexInput ii = cfr.openInput(files[i]);

          FileOutputStream f = new FileOutputStream(files[i]);

          // read and write with a small buffer, which is more effective than reading byte by byte
          byte[] buffer = new byte[1024];
          int chunk = buffer.length;
          while(len > 0) {
            final int bufLen = (int) Math.min(chunk, len);
            ii.readBytes(buffer, 0, bufLen);
            f.write(buffer, 0, bufLen);
            len -= bufLen;
          }

          f.close();
          ii.close();
        }
        else
          System.out.println(files[i] + ": " + len + " bytes");
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
    finally {
      try {
        if (dir != null)
          dir.close();
        if (cfr != null)
          cfr.close();
      }
      catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }

  /** Returns all commit points that exist in the Directory.
   *  Normally, because the default is {@link
   *  KeepOnlyLastCommitDeletionPolicy}, there would be only
   *  one commit point.  But if you're using a custom {@link
   *  IndexDeletionPolicy} then there could be many commits.
   *  Once you have a given commit, you can open a reader on
   *  it by calling {@link IndexReader#open(IndexCommit,boolean)}
   *  There must be at least one commit in
   *  the Directory, else this method throws {@link
   *  IndexNotFoundException}.  Note that if a commit is in
   *  progress while this method is running, that commit
   *  may or may not be returned.
   *  
   *  @return a sorted list of {@link IndexCommit}s, from oldest 
   *  to latest. */
  public static Collection<IndexCommit> listCommits(Directory dir) throws IOException {
    return DirectoryReader.listCommits(dir);
  }

  /** Expert: returns the sequential sub readers that this
   *  reader is logically composed of.  For example,
   *  IndexSearcher uses this API to drive searching by one
   *  sub reader at a time.  If this reader is not composed
   *  of sequential child readers, it should return null.
   *  If this method returns an empty array, that means this
   *  reader is a null reader (for example a MultiReader
   *  that has no sub readers).
   *  <p>
   *  NOTE: You should not try using sub-readers returned by
   *  this method to make any changes (setNorm, deleteDocument,
   *  etc.). While this might succeed for one composite reader
   *  (like MultiReader), it will most likely lead to index
   *  corruption for other readers (like DirectoryReader obtained
   *  through {@link #open}. Use the parent reader directly. */
  public IndexReader[] getSequentialSubReaders() {
    ensureOpen();
    return null;
  }

  /** Expert */
  public Object getCoreCacheKey() {
    // Don't can ensureOpen since FC calls this (to evict)
    // on close
    return this;
  }

  /** Expert.  Warning: this returns null if the reader has
   *  no deletions */
  public Object getDeletesCacheKey() {
    return this;
  }

  /** Returns the number of unique terms (across all fields)
   *  in this reader.
   *
   *  This method returns long, even though internally
   *  Lucene cannot handle more than 2^31 unique terms, for
   *  a possible future when this limitation is removed.
   *
   *  @throws UnsupportedOperationException if this count
   *  cannot be easily determined (eg Multi*Readers).
   *  Instead, you should call {@link
   *  #getSequentialSubReaders} and ask each sub reader for
   *  its unique term count. */
  public long getUniqueTermCount() throws IOException {
    throw new UnsupportedOperationException("this reader does not implement getUniqueTermCount()");
  }

  // Back compat for reopen()
  @Deprecated
  private static final VirtualMethod<IndexReader> reopenMethod1 =
    new VirtualMethod<IndexReader>(IndexReader.class, "reopen");
  @Deprecated
  private static final VirtualMethod<IndexReader> doOpenIfChangedMethod1 =
    new VirtualMethod<IndexReader>(IndexReader.class, "doOpenIfChanged");
  @Deprecated
  private final boolean hasNewReopenAPI1 =
    VirtualMethod.compareImplementationDistance(getClass(),
        doOpenIfChangedMethod1, reopenMethod1) >= 0; // its ok for both to be overridden

  // Back compat for reopen(boolean openReadOnly)
  @Deprecated
  private static final VirtualMethod<IndexReader> reopenMethod2 =
    new VirtualMethod<IndexReader>(IndexReader.class, "reopen", boolean.class);
  @Deprecated
  private static final VirtualMethod<IndexReader> doOpenIfChangedMethod2 =
    new VirtualMethod<IndexReader>(IndexReader.class, "doOpenIfChanged", boolean.class);
  @Deprecated
  private final boolean hasNewReopenAPI2 =
    VirtualMethod.compareImplementationDistance(getClass(),
        doOpenIfChangedMethod2, reopenMethod2) >= 0; // its ok for both to be overridden

  // Back compat for reopen(IndexCommit commit)
  @Deprecated
  private static final VirtualMethod<IndexReader> reopenMethod3 =
    new VirtualMethod<IndexReader>(IndexReader.class, "reopen", IndexCommit.class);
  @Deprecated
  private static final VirtualMethod<IndexReader> doOpenIfChangedMethod3 =
    new VirtualMethod<IndexReader>(IndexReader.class, "doOpenIfChanged", IndexCommit.class);
  @Deprecated
  private final boolean hasNewReopenAPI3 =
    VirtualMethod.compareImplementationDistance(getClass(),
        doOpenIfChangedMethod3, reopenMethod3) >= 0; // its ok for both to be overridden

  // Back compat for reopen(IndexWriter writer, boolean applyDeletes)
  @Deprecated
  private static final VirtualMethod<IndexReader> reopenMethod4 =
    new VirtualMethod<IndexReader>(IndexReader.class, "reopen", IndexWriter.class, boolean.class);
  @Deprecated
  private static final VirtualMethod<IndexReader> doOpenIfChangedMethod4 =
    new VirtualMethod<IndexReader>(IndexReader.class, "doOpenIfChanged", IndexWriter.class, boolean.class);
  @Deprecated
  private final boolean hasNewReopenAPI4 =
    VirtualMethod.compareImplementationDistance(getClass(),
        doOpenIfChangedMethod4, reopenMethod4) >= 0; // its ok for both to be overridden

  /** For IndexReader implementations that use
   *  TermInfosReader to read terms, this returns the
   *  current indexDivisor as specified when the reader was
   *  opened.
   */
  public int getTermInfosIndexDivisor() {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }
}
