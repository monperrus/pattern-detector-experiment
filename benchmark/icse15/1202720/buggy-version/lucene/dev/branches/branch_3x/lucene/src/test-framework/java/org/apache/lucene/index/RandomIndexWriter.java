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
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter; // javadoc
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.Version;
import org.apache.lucene.util._TestUtil;

/** Silly class that randomizes the indexing experience.  EG
 *  it may swap in a different merge policy/scheduler; may
 *  commit periodically; may or may not forceMerge in the end,
 *  may flush by doc count instead of RAM, etc. 
 */

public class RandomIndexWriter implements Closeable {

  public IndexWriter w;
  private final Random r;
  int docCount;
  int flushAt;
  private double flushAtFactor = 1.0;
  private boolean getReaderCalled;

  // Randomly calls Thread.yield so we mixup thread scheduling
  private static final class MockIndexWriter extends IndexWriter {

    private final Random r;

    public MockIndexWriter(Random r,Directory dir, IndexWriterConfig conf) throws IOException {
      super(dir, conf);
      // must make a private random since our methods are
      // called from different threads; else test failures may
      // not be reproducible from the original seed
      this.r = new Random(r.nextInt());
    }

    @Override
    boolean testPoint(String name) {
      if (r.nextInt(4) == 2)
        Thread.yield();
      return true;
    }
  }

  /** create a RandomIndexWriter with a random config: Uses TEST_VERSION_CURRENT and Whitespace+LowercasingAnalyzer */
  public RandomIndexWriter(Random r, Directory dir) throws IOException {
    this(r, dir, LuceneTestCase.newIndexWriterConfig(r, LuceneTestCase.TEST_VERSION_CURRENT, new MockAnalyzer(r)));
  }
  
  /** create a RandomIndexWriter with a random config: Uses TEST_VERSION_CURRENT */
  public RandomIndexWriter(Random r, Directory dir, Analyzer a) throws IOException {
    this(r, dir, LuceneTestCase.newIndexWriterConfig(r, LuceneTestCase.TEST_VERSION_CURRENT, a));
  }
  
  /** create a RandomIndexWriter with a random config */
  public RandomIndexWriter(Random r, Directory dir, Version v, Analyzer a) throws IOException {
    this(r, dir, LuceneTestCase.newIndexWriterConfig(r, v, a));
  }
  
  /** create a RandomIndexWriter with the provided config */
  public RandomIndexWriter(Random r, Directory dir, IndexWriterConfig c) throws IOException {
    this.r = r;
    w = new MockIndexWriter(r, dir, c);
    flushAt = _TestUtil.nextInt(r, 10, 1000);
    if (LuceneTestCase.VERBOSE) {
      System.out.println("RIW config=" + w.getConfig());
    }
  } 

  /**
   * Adds a Document.
   * @see IndexWriter#addDocument(Document)
   */
  public void addDocument(final Document doc) throws IOException {
    if (r.nextInt(5) == 3) {
      // TODO: maybe, we should simply buffer up added docs
      // (but we need to clone them), and only when
      // getReader, commit, etc. are called, we do an
      // addDocuments?  Would be better testing.
      w.addDocuments(Collections.singletonList(doc));
    } else {
      w.addDocument(doc);
    }
    maybeCommit();
  }
  
  public void addDocuments(Collection<Document> docs) throws IOException {
    w.addDocuments(docs);
    maybeCommit();
  }

  public void updateDocuments(Term delTerm, Collection<Document> docs) throws IOException {
    w.updateDocuments(delTerm, docs);
    maybeCommit();
  }

  private void maybeCommit() throws IOException {
    if (docCount++ == flushAt) {
      if (LuceneTestCase.VERBOSE) {
        System.out.println("RIW.add/updateDocument: now doing a commit at docCount=" + docCount);
      }
      w.commit();
      flushAt += _TestUtil.nextInt(r, (int) (flushAtFactor * 10), (int) (flushAtFactor * 1000));
      if (flushAtFactor < 2e6) {
        // gradually but exponentially increase time b/w flushes
        flushAtFactor *= 1.05;
      }
    }
  }

  /**
   * Updates a document.
   * @see IndexWriter#updateDocument(Term, Document)
   */
  public void updateDocument(Term t, final Document doc) throws IOException {
    if (r.nextInt(5) == 3) {
      w.updateDocuments(t, Collections.singletonList(doc));
    } else {
      w.updateDocument(t, doc);
    }
    maybeCommit();
  }
  
  public void addIndexes(Directory... dirs) throws CorruptIndexException, IOException {
    w.addIndexes(dirs);
  }
  
  public void deleteDocuments(Term term) throws CorruptIndexException, IOException {
    w.deleteDocuments(term);
  }

  public void deleteDocuments(Query q) throws CorruptIndexException, IOException {
    w.deleteDocuments(q);
  }
  
  public void commit() throws CorruptIndexException, IOException {
    w.commit();
  }
  
  public int numDocs() throws IOException {
    return w.numDocs();
  }

  public int maxDoc() {
    return w.maxDoc();
  }

  public void deleteAll() throws IOException {
    w.deleteAll();
  }

  private boolean doRandomForceMerge = true;
  private boolean doRandomForceMergeAssert = true;

  public void expungeDeletes(boolean doWait) throws IOException {
    w.expungeDeletes(doWait);
  }

  public void expungeDeletes() throws IOException {
    w.expungeDeletes();
  }

  public void setDoRandomForceMerge(boolean v) {
    doRandomForceMerge = v;
  }

  public void setDoRandomForceMergeAssert(boolean v) {
    doRandomForceMergeAssert = v;
  }

  private void doRandomForceMerge() throws IOException {
    if (doRandomForceMerge) {
      final int segCount = w.getSegmentCount();
      if (r.nextBoolean() || segCount == 0) {
        // full forceMerge
        w.forceMerge(1);
      } else {
        // partial forceMerge
        final int limit = _TestUtil.nextInt(r, 1, segCount);
        w.forceMerge(limit);
        assert !doRandomForceMergeAssert || w.getSegmentCount() <= limit: "limit=" + limit + " actual=" + w.getSegmentCount();
      }
    }
  }

  public IndexReader getReader() throws IOException {
    return getReader(true);
  }

  public IndexReader getReader(boolean applyDeletions) throws IOException {
    getReaderCalled = true;
    if (r.nextInt(4) == 2) {
      doRandomForceMerge();
    }
    if (r.nextBoolean()) {
      if (LuceneTestCase.VERBOSE) {
        System.out.println("RIW.getReader: use NRT reader");
      }
      if (r.nextInt(5) == 1) {
        w.commit();
      }
      return w.getReader(applyDeletions);
    } else {
      if (LuceneTestCase.VERBOSE) {
        System.out.println("RIW.getReader: open new reader");
      }
      w.commit();
      if (r.nextBoolean()) {
        return IndexReader.open(w.getDirectory(), new KeepOnlyLastCommitDeletionPolicy(), r.nextBoolean(), _TestUtil.nextInt(r, 1, 10));
      } else {
        return w.getReader(applyDeletions);
      }
    }
  }

  /**
   * Close this writer.
   * @see IndexWriter#close()
   */
  public void close() throws IOException {
    // if someone isn't using getReader() API, we want to be sure to
    // forceMerge since presumably they might open a reader on the dir.
    if (getReaderCalled == false && r.nextInt(8) == 2) {
      doRandomForceMerge();
    }
    w.close();
  }

  /**
   * Forces a forceMerge.
   * <p>
   * NOTE: this should be avoided in tests unless absolutely necessary,
   * as it will result in less test coverage.
   * @see IndexWriter#forceMerge(int)
   */
  public void forceMerge(int maxSegmentCount) throws IOException {
    w.forceMerge(maxSegmentCount);
  }
}
