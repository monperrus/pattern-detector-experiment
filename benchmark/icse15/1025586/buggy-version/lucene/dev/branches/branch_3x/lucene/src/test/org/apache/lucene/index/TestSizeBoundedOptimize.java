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

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.LuceneTestCase;

public class TestSizeBoundedOptimize extends LuceneTestCase {

  private void addDocs(IndexWriter writer, int numDocs) throws IOException {
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      writer.addDocument(doc);
    }
    writer.commit();
  }
  
  public void testByteSizeLimit() throws Exception {
    // tests that the max merge size constraint is applied during optimize.
    Directory dir = new RAMDirectory();

    // Prepare an index w/ several small segments and a large one.
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    // prevent any merges from happening.
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    final int numSegments = 15;
    for (int i = 0; i < numSegments; i++) {
      int numDocs = i == 7 ? 10 : 1;
      addDocs(writer, numDocs);
    }
    
    writer.close();

    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogByteSizeMergePolicy lmp = new LogByteSizeMergePolicy();
    lmp.setMaxMergeMB(200.0 / (1 << 20)); // ~100 bytes tops
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();

    // Should only be 3 segments in the index, because one of them exceeds the size limit
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(3, sis.size());
  }

  public void testNumDocsLimit() throws Exception {
    // tests that the max merge docs constraint is applied during optimize.
    Directory dir = new RAMDirectory();

    // Prepare an index w/ several small segments and a large one.
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    // prevent any merges from happening.
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);

    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 5);
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    
    writer.close();

    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(3);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();

    // Should only be 3 segments in the index, because one of them exceeds the size limit
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(3, sis.size());
  }

  public void testLastSegmentTooLarge() throws Exception {
    Directory dir = new RAMDirectory();

    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);

    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 5);
    
    writer.close();

    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(3);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();

    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(2, sis.size());
  }
  
  public void testFirstSegmentTooLarge() throws Exception {
    Directory dir = new RAMDirectory();
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    
    addDocs(writer, 5);
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    
    writer.close();
    
    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(3);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();
    
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(2, sis.size());
  }
  
  public void testAllSegmentsSmall() throws Exception {
    Directory dir = new RAMDirectory();
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    
    writer.close();
    
    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(3);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();
    
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(1, sis.size());
  }
  
  public void testAllSegmentsLarge() throws Exception {
    Directory dir = new RAMDirectory();
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    
    writer.close();
    
    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(2);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();
    
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(3, sis.size());
  }
  
  public void testOneLargeOneSmall() throws Exception {
    Directory dir = new RAMDirectory();
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    
    addDocs(writer, 3);
    addDocs(writer, 5);
    addDocs(writer, 3);
    addDocs(writer, 5);
    
    writer.close();
    
    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(3);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();
    
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(4, sis.size());
  }
  
  public void testMergeFactor() throws Exception {
    Directory dir = new RAMDirectory();
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 3);
    addDocs(writer, 5);
    addDocs(writer, 3);
    addDocs(writer, 3);
    
    writer.close();
    
    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(3);
    lmp.setMergeFactor(2);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();
    
    // Should only be 4 segments in the index, because of the merge factor and
    // max merge docs settings.
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(4, sis.size());
  }
  
  public void testSingleNonOptimizedSegment() throws Exception {
    Directory dir = new RAMDirectory();
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    
    addDocs(writer, 3);
    addDocs(writer, 5);
    addDocs(writer, 3);
    
    writer.close();
  
    // delete the last document, so that the last segment is optimized.
    IndexReader r = IndexReader.open(dir, false);
    r.deleteDocument(r.numDocs() - 1);
    r.close();
    
    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(3);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();
    
    // Verify that the last segment does not have deletions.
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(3, sis.size());
    assertFalse(sis.info(2).hasDeletions());
  }
  
  public void testSingleOptimizedSegment() throws Exception {
    Directory dir = new RAMDirectory();
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    
    addDocs(writer, 3);
    
    writer.close();
    
    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(3);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();
    
    // Verify that the last segment does not have deletions.
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(1, sis.size());
  }

  public void testSingleNonOptimizedTooLargeSegment() throws Exception {
    Directory dir = new RAMDirectory();
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(NoMergePolicy.COMPOUND_FILES);
    IndexWriter writer = new IndexWriter(dir, conf);
    
    addDocs(writer, 5);
    
    writer.close();
  
    // delete the last document
    IndexReader r = IndexReader.open(dir, false);
    r.deleteDocument(r.numDocs() - 1);
    r.close();
    
    conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMaxMergeDocs(2);
    conf.setMergePolicy(lmp);
    
    writer = new IndexWriter(dir, conf);
    writer.optimize();
    writer.close();
    
    // Verify that the last segment does not have deletions.
    SegmentInfos sis = new SegmentInfos();
    sis.read(dir);
    assertEquals(1, sis.size());
    assertTrue(sis.info(0).hasDeletions());
  }

}
