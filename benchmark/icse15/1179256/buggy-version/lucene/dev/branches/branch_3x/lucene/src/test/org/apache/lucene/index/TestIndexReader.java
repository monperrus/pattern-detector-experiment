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


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.store.NoSuchDirectoryException;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.LockReleaseFailedException;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;

public class TestIndexReader extends LuceneTestCase {
    
    public void testCommitUserData() throws Exception {
      Directory d = newDirectory();

      Map<String,String> commitUserData = new HashMap<String,String>();
      commitUserData.put("foo", "fighters");
      
      // set up writer
      IndexWriter writer = new IndexWriter(d, newIndexWriterConfig(
          TEST_VERSION_CURRENT, new MockAnalyzer(random))
      .setMaxBufferedDocs(2));
      for(int i=0;i<27;i++)
        addDocumentWithFields(writer);
      writer.close();
      
      IndexReader r = IndexReader.open(d, false);
      r.deleteDocument(5);
      r.flush(commitUserData);
      IndexCommit c = r.getIndexCommit();
      r.close();
      
      SegmentInfos sis = new SegmentInfos();
      sis.read(d);
      IndexReader r2 = IndexReader.open(d, false);
      assertEquals(c.getUserData(), commitUserData);

      assertEquals(sis.getCurrentSegmentFileName(), c.getSegmentsFileName());

      // Change the index
      writer = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT,
          new MockAnalyzer(random)).setOpenMode(
              OpenMode.APPEND).setMaxBufferedDocs(2));
      for(int i=0;i<7;i++)
        addDocumentWithFields(writer);
      writer.close();

      IndexReader r3 = r2.reopen();
      assertFalse(c.equals(r3.getIndexCommit()));
      assertFalse(r2.getIndexCommit().isOptimized());
      r3.close();

      writer = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer(random))
        .setOpenMode(OpenMode.APPEND));
      writer.optimize();
      writer.close();

      r3 = r2.reopen();
      assertTrue(r3.getIndexCommit().isOptimized());
      r2.close();
      r3.close();
      d.close();
    }
    
    public void testIsCurrent() throws Exception {
      Directory d = newDirectory();
      IndexWriter writer = new IndexWriter(d, newIndexWriterConfig( 
        TEST_VERSION_CURRENT, new MockAnalyzer(random)));
      addDocumentWithFields(writer);
      writer.close();
      // set up reader:
      IndexReader reader = IndexReader.open(d, false);
      assertTrue(reader.isCurrent());
      // modify index by adding another document:
      writer = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT,
          new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
      addDocumentWithFields(writer);
      writer.close();
      assertFalse(reader.isCurrent());
      // re-create index:
      writer = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT,
          new MockAnalyzer(random)).setOpenMode(OpenMode.CREATE));
      addDocumentWithFields(writer);
      writer.close();
      assertFalse(reader.isCurrent());
      reader.close();
      d.close();
    }

    /**
     * Tests the IndexReader.getFieldNames implementation
     * @throws Exception on error
     */
    public void testGetFieldNames() throws Exception {
        Directory d = newDirectory();
        // set up writer
        IndexWriter writer = new IndexWriter(
            d,
            newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random))
        );

        Document doc = new Document();
        doc.add(new Field("keyword","test1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("text","test1", Field.Store.YES, Field.Index.ANALYZED));
        doc.add(new Field("unindexed","test1", Field.Store.YES, Field.Index.NO));
        doc.add(new Field("unstored","test1", Field.Store.NO, Field.Index.ANALYZED));
        writer.addDocument(doc);

        writer.close();
        // set up reader
        IndexReader reader = IndexReader.open(d, false);
        Collection<String> fieldNames = reader.getFieldNames(IndexReader.FieldOption.ALL);
        assertTrue(fieldNames.contains("keyword"));
        assertTrue(fieldNames.contains("text"));
        assertTrue(fieldNames.contains("unindexed"));
        assertTrue(fieldNames.contains("unstored"));
        reader.close();
        // add more documents
        writer = new IndexWriter(
            d,
            newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
                setOpenMode(OpenMode.APPEND).
                setMergePolicy(newLogMergePolicy())
        );
        // want to get some more segments here
        int mergeFactor = ((LogMergePolicy) writer.getConfig().getMergePolicy()).getMergeFactor();
        for (int i = 0; i < 5*mergeFactor; i++) {
          doc = new Document();
          doc.add(new Field("keyword","test1", Field.Store.YES, Field.Index.NOT_ANALYZED));
          doc.add(new Field("text","test1", Field.Store.YES, Field.Index.ANALYZED));
          doc.add(new Field("unindexed","test1", Field.Store.YES, Field.Index.NO));
          doc.add(new Field("unstored","test1", Field.Store.NO, Field.Index.ANALYZED));
          writer.addDocument(doc);
        }
        // new fields are in some different segments (we hope)
        for (int i = 0; i < 5*mergeFactor; i++) {
          doc = new Document();
          doc.add(new Field("keyword2","test1", Field.Store.YES, Field.Index.NOT_ANALYZED));
          doc.add(new Field("text2","test1", Field.Store.YES, Field.Index.ANALYZED));
          doc.add(new Field("unindexed2","test1", Field.Store.YES, Field.Index.NO));
          doc.add(new Field("unstored2","test1", Field.Store.NO, Field.Index.ANALYZED));
          writer.addDocument(doc);
        }
        // new termvector fields
        for (int i = 0; i < 5*mergeFactor; i++) {
          doc = new Document();
          doc.add(new Field("tvnot","tvnot", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.NO));
          doc.add(new Field("termvector","termvector", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
          doc.add(new Field("tvoffset","tvoffset", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_OFFSETS));
          doc.add(new Field("tvposition","tvposition", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS));
          doc.add(newField("tvpositionoffset","tvpositionoffset", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
          writer.addDocument(doc);
        }
        
        writer.close();
        // verify fields again
        reader = IndexReader.open(d, false);
        fieldNames = reader.getFieldNames(IndexReader.FieldOption.ALL);
        assertEquals(13, fieldNames.size());    // the following fields
        assertTrue(fieldNames.contains("keyword"));
        assertTrue(fieldNames.contains("text"));
        assertTrue(fieldNames.contains("unindexed"));
        assertTrue(fieldNames.contains("unstored"));
        assertTrue(fieldNames.contains("keyword2"));
        assertTrue(fieldNames.contains("text2"));
        assertTrue(fieldNames.contains("unindexed2"));
        assertTrue(fieldNames.contains("unstored2"));
        assertTrue(fieldNames.contains("tvnot"));
        assertTrue(fieldNames.contains("termvector"));
        assertTrue(fieldNames.contains("tvposition"));
        assertTrue(fieldNames.contains("tvoffset"));
        assertTrue(fieldNames.contains("tvpositionoffset"));
        
        // verify that only indexed fields were returned
        fieldNames = reader.getFieldNames(IndexReader.FieldOption.INDEXED);
        assertEquals(11, fieldNames.size());    // 6 original + the 5 termvector fields 
        assertTrue(fieldNames.contains("keyword"));
        assertTrue(fieldNames.contains("text"));
        assertTrue(fieldNames.contains("unstored"));
        assertTrue(fieldNames.contains("keyword2"));
        assertTrue(fieldNames.contains("text2"));
        assertTrue(fieldNames.contains("unstored2"));
        assertTrue(fieldNames.contains("tvnot"));
        assertTrue(fieldNames.contains("termvector"));
        assertTrue(fieldNames.contains("tvposition"));
        assertTrue(fieldNames.contains("tvoffset"));
        assertTrue(fieldNames.contains("tvpositionoffset"));
        
        // verify that only unindexed fields were returned
        fieldNames = reader.getFieldNames(IndexReader.FieldOption.UNINDEXED);
        assertEquals(2, fieldNames.size());    // the following fields
        assertTrue(fieldNames.contains("unindexed"));
        assertTrue(fieldNames.contains("unindexed2"));
                
        // verify index term vector fields  
        fieldNames = reader.getFieldNames(IndexReader.FieldOption.TERMVECTOR);
        assertEquals(1, fieldNames.size());    // 1 field has term vector only
        assertTrue(fieldNames.contains("termvector"));
        
        fieldNames = reader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION);
        assertEquals(1, fieldNames.size());    // 4 fields are indexed with term vectors
        assertTrue(fieldNames.contains("tvposition"));
        
        fieldNames = reader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_OFFSET);
        assertEquals(1, fieldNames.size());    // 4 fields are indexed with term vectors
        assertTrue(fieldNames.contains("tvoffset"));
                
        fieldNames = reader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION_OFFSET);
        assertEquals(1, fieldNames.size());    // 4 fields are indexed with term vectors
        assertTrue(fieldNames.contains("tvpositionoffset"));
        reader.close();
        d.close();
    }

  public void testTermVectors() throws Exception {
    Directory d = newDirectory();
    // set up writer
    IndexWriter writer = new IndexWriter(
        d,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setMergePolicy(newLogMergePolicy())
    );
    // want to get some more segments here
    // new termvector fields
    int mergeFactor = ((LogMergePolicy) writer.getConfig().getMergePolicy()).getMergeFactor();
    for (int i = 0; i < 5 * mergeFactor; i++) {
      Document doc = new Document();
        doc.add(new Field("tvnot","one two two three three three", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.NO));
        doc.add(new Field("termvector","one two two three three three", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        doc.add(new Field("tvoffset","one two two three three three", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_OFFSETS));
        doc.add(new Field("tvposition","one two two three three three", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS));
        doc.add(new Field("tvpositionoffset","one two two three three three", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));

        writer.addDocument(doc);
    }
    writer.close();
    IndexReader reader = IndexReader.open(d, false);
    FieldSortedTermVectorMapper mapper = new FieldSortedTermVectorMapper(new TermVectorEntryFreqSortedComparator());
    reader.getTermFreqVector(0, mapper);
    Map<String,SortedSet<TermVectorEntry>> map = mapper.getFieldToTerms();
    assertTrue("map is null and it shouldn't be", map != null);
    assertTrue("map Size: " + map.size() + " is not: " + 4, map.size() == 4);
    Set<TermVectorEntry> set = map.get("termvector");
    for (Iterator<TermVectorEntry> iterator = set.iterator(); iterator.hasNext();) {
      TermVectorEntry entry =  iterator.next();
      assertTrue("entry is null and it shouldn't be", entry != null);
      if (VERBOSE) System.out.println("Entry: " + entry);
    }
    reader.close();
    d.close();
  }

  static void assertTermDocsCount(String msg,
                                     IndexReader reader,
                                     Term term,
                                     int expected)
    throws IOException
    {
        TermDocs tdocs = null;

        try {
            tdocs = reader.termDocs(term);
            assertNotNull(msg + ", null TermDocs", tdocs);
            int count = 0;
            while(tdocs.next()) {
                count++;
            }
            assertEquals(msg + ", count mismatch", expected, count);

        } finally {
            if (tdocs != null)
                tdocs.close();
        }

    }

    
    public void testBinaryFields() throws IOException {
        Directory dir = newDirectory();
        byte[] bin = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setMergePolicy(newLogMergePolicy()));
        
        for (int i = 0; i < 10; i++) {
          addDoc(writer, "document number " + (i + 1));
          addDocumentWithFields(writer);
          addDocumentWithDifferentFields(writer);
          addDocumentWithTermVectorFields(writer);
        }
        writer.close();
        writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND).setMergePolicy(newLogMergePolicy()));
        Document doc = new Document();
        doc.add(new Field("bin1", bin));
        doc.add(new Field("junk", "junk text", Field.Store.NO, Field.Index.ANALYZED));
        writer.addDocument(doc);
        writer.close();
        IndexReader reader = IndexReader.open(dir, false);
        doc = reader.document(reader.maxDoc() - 1);
        Field[] fields = doc.getFields("bin1");
        assertNotNull(fields);
        assertEquals(1, fields.length);
        Field b1 = fields[0];
        assertTrue(b1.isBinary());
        byte[] data1 = b1.getBinaryValue();
        assertEquals(bin.length, b1.getBinaryLength());
        for (int i = 0; i < bin.length; i++) {
          assertEquals(bin[i], data1[i + b1.getBinaryOffset()]);
        }
        Set<String> lazyFields = new HashSet<String>();
        lazyFields.add("bin1");
        FieldSelector sel = new SetBasedFieldSelector(new HashSet<String>(), lazyFields);
        doc = reader.document(reader.maxDoc() - 1, sel);
        Fieldable[] fieldables = doc.getFieldables("bin1");
        assertNotNull(fieldables);
        assertEquals(1, fieldables.length);
        Fieldable fb1 = fieldables[0];
        assertTrue(fb1.isBinary());
        assertEquals(bin.length, fb1.getBinaryLength());
        data1 = fb1.getBinaryValue();
        assertEquals(bin.length, fb1.getBinaryLength());
        for (int i = 0; i < bin.length; i++) {
          assertEquals(bin[i], data1[i + fb1.getBinaryOffset()]);
        }
        reader.close();
        // force optimize


        writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND).setMergePolicy(newLogMergePolicy()));
        writer.optimize();
        writer.close();
        reader = IndexReader.open(dir, false);
        doc = reader.document(reader.maxDoc() - 1);
        fields = doc.getFields("bin1");
        assertNotNull(fields);
        assertEquals(1, fields.length);
        b1 = fields[0];
        assertTrue(b1.isBinary());
        data1 = b1.getBinaryValue();
        assertEquals(bin.length, b1.getBinaryLength());
        for (int i = 0; i < bin.length; i++) {
          assertEquals(bin[i], data1[i + b1.getBinaryOffset()]);
        }
        reader.close();
        dir.close();
    }

    // Make sure attempts to make changes after reader is
    // closed throws IOException:
    public void testChangesAfterClose() throws IOException {
        Directory dir = newDirectory();

        IndexWriter writer = null;
        IndexReader reader = null;
        Term searchTerm = new Term("content", "aaa");

        //  add 11 documents with term : aaa
        writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
        for (int i = 0; i < 11; i++) {
            addDoc(writer, searchTerm.text());
        }
        writer.close();

        reader = IndexReader.open(dir, false);

        // Close reader:
        reader.close();

        // Then, try to make changes:
        try {
          reader.deleteDocument(4);
          fail("deleteDocument after close failed to throw IOException");
        } catch (AlreadyClosedException e) {
          // expected
        }

        try {
          reader.setNorm(5, "aaa", 2.0f);
          fail("setNorm after close failed to throw IOException");
        } catch (AlreadyClosedException e) {
          // expected
        }

        try {
          reader.undeleteAll();
          fail("undeleteAll after close failed to throw IOException");
        } catch (AlreadyClosedException e) {
          // expected
        }
        dir.close();
    }

    // Make sure we get lock obtain failed exception with 2 writers:
    public void testLockObtainFailed() throws IOException {
        Directory dir = newDirectory();

        Term searchTerm = new Term("content", "aaa");

        //  add 11 documents with term : aaa
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
        writer.commit();
        for (int i = 0; i < 11; i++) {
            addDoc(writer, searchTerm.text());
        }

        // Create reader:
        IndexReader reader = IndexReader.open(dir, false);

        // Try to make changes
        try {
          reader.deleteDocument(4);
          fail("deleteDocument should have hit LockObtainFailedException");
        } catch (LockObtainFailedException e) {
          // expected
        }

        try {
          reader.setNorm(5, "aaa", 2.0f);
          fail("setNorm should have hit LockObtainFailedException");
        } catch (LockObtainFailedException e) {
          // expected
        }

        try {
          reader.undeleteAll();
          fail("undeleteAll should have hit LockObtainFailedException");
        } catch (LockObtainFailedException e) {
          // expected
        }
        writer.close();
        reader.close();
        dir.close();
    }

    // Make sure you can set norms & commit even if a reader
    // is open against the index:
    public void testWritingNorms() throws IOException {
        Directory dir = newDirectory();
        IndexWriter writer;
        IndexReader reader;
        Term searchTerm = new Term("content", "aaa");

        //  add 1 documents with term : aaa
        writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
        addDoc(writer, searchTerm.text());
        writer.close();

        //  now open reader & set norm for doc 0
        reader = IndexReader.open(dir, false);
        reader.setNorm(0, "content", (float) 2.0);

        // we should be holding the write lock now:
        assertTrue("locked", IndexWriter.isLocked(dir));

        reader.commit();

        // we should not be holding the write lock now:
        assertTrue("not locked", !IndexWriter.isLocked(dir));

        // open a 2nd reader:
        IndexReader reader2 = IndexReader.open(dir, false);

        // set norm again for doc 0
        reader.setNorm(0, "content", (float) 3.0);
        assertTrue("locked", IndexWriter.isLocked(dir));

        reader.close();

        // we should not be holding the write lock now:
        assertTrue("not locked", !IndexWriter.isLocked(dir));

        reader2.close();
        dir.close();
    }


    // Make sure you can set norms & commit, and there are
    // no extra norms files left:
    public void testWritingNormsNoReader() throws IOException {
        Directory dir = newDirectory();
        IndexWriter writer = null;
        IndexReader reader = null;
        Term searchTerm = new Term("content", "aaa");

        //  add 1 documents with term : aaa
        writer  = new IndexWriter(
            dir,
            newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
                setMergePolicy(newLogMergePolicy(false))
        );
        addDoc(writer, searchTerm.text());
        writer.close();

        //  now open reader & set norm for doc 0 (writes to
        //  _0_1.s0)
        reader = IndexReader.open(dir, false);
        reader.setNorm(0, "content", (float) 2.0);
        reader.close();
        
        //  now open reader again & set norm for doc 0 (writes to _0_2.s0)
        reader = IndexReader.open(dir, false);
        reader.setNorm(0, "content", (float) 2.0);
        reader.close();
        assertFalse("failed to remove first generation norms file on writing second generation",
                    dir.fileExists("_0_1.s0"));
        
        dir.close();
    }

    /* ??? public void testOpenEmptyDirectory() throws IOException{
      String dirName = "test.empty";
      File fileDirName = new File(dirName);
      if (!fileDirName.exists()) {
        fileDirName.mkdir();
      }
      try {
        IndexReader.open(fileDirName);
        fail("opening IndexReader on empty directory failed to produce FileNotFoundException");
      } catch (FileNotFoundException e) {
        // GOOD
      }
      rmDir(fileDirName);
    }*/
    
  public void testFilesOpenClose() throws IOException {
        // Create initial data set
        File dirFile = _TestUtil.getTempDir("TestIndexReader.testFilesOpenClose");
        Directory dir = newFSDirectory(dirFile);
        IndexWriter writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
        addDoc(writer, "test");
        writer.close();
        dir.close();

        // Try to erase the data - this ensures that the writer closed all files
        _TestUtil.rmDir(dirFile);
        dir = newFSDirectory(dirFile);

        // Now create the data set again, just as before
        writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.CREATE));
        addDoc(writer, "test");
        writer.close();
        dir.close();

        // Now open existing directory and test that reader closes all files
        dir = newFSDirectory(dirFile);
        IndexReader reader1 = IndexReader.open(dir, false);
        reader1.close();
        dir.close();

        // The following will fail if reader did not close
        // all files
        _TestUtil.rmDir(dirFile);
    }

    public void testLastModified() throws Exception {
      for(int i=0;i<2;i++) {
        final Directory dir = newDirectory();
        assertFalse(IndexReader.indexExists(dir));
        IndexWriter writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.CREATE));
        addDocumentWithFields(writer);
        assertTrue(IndexWriter.isLocked(dir));		// writer open, so dir is locked
        writer.close();
        assertTrue(IndexReader.indexExists(dir));
        IndexReader reader = IndexReader.open(dir, false);
        assertFalse(IndexWriter.isLocked(dir));		// reader only, no lock
        long version = IndexReader.lastModified(dir);
        if (i == 1) {
          long version2 = IndexReader.lastModified(dir);
          assertEquals(version, version2);
        }
        reader.close();
        // modify index and check version has been
        // incremented:
        Thread.sleep(1000);

        writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.CREATE));
        addDocumentWithFields(writer);
        writer.close();
        reader = IndexReader.open(dir, false);
        assertTrue("old lastModified is " + version + "; new lastModified is " + IndexReader.lastModified(dir), version <= IndexReader.lastModified(dir));
        reader.close();
        dir.close();
      }
    }

    public void testVersion() throws IOException {
      Directory dir = newDirectory();
      assertFalse(IndexReader.indexExists(dir));
      IndexWriter writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
      addDocumentWithFields(writer);
      assertTrue(IndexWriter.isLocked(dir));		// writer open, so dir is locked
      writer.close();
      assertTrue(IndexReader.indexExists(dir));
      IndexReader reader = IndexReader.open(dir, false);
      assertFalse(IndexWriter.isLocked(dir));		// reader only, no lock
      long version = IndexReader.getCurrentVersion(dir);
      reader.close();
      // modify index and check version has been
      // incremented:
      writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.CREATE));
      addDocumentWithFields(writer);
      writer.close();
      reader = IndexReader.open(dir, false);
      assertTrue("old version is " + version + "; new version is " + IndexReader.getCurrentVersion(dir), version < IndexReader.getCurrentVersion(dir));
      reader.close();
      dir.close();
    }

    public void testLock() throws IOException {
      Directory dir = newDirectory();
      IndexWriter writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
      addDocumentWithFields(writer);
      writer.close();
      writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
      IndexReader reader = IndexReader.open(dir, false);
      try {
        reader.deleteDocument(0);
        fail("expected lock");
      } catch(IOException e) {
        // expected exception
      }
      try {
        IndexWriter.unlock(dir);		// this should not be done in the real world! 
      } catch (LockReleaseFailedException lrfe) {
        writer.close();
      }
      reader.deleteDocument(0);
      reader.close();
      writer.close();
      dir.close();
    }

    public void testDocsOutOfOrderJIRA140() throws IOException {
      Directory dir = newDirectory();      
      IndexWriter writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
      for(int i=0;i<11;i++) {
        addDoc(writer, "aaa");
      }
      writer.close();
      IndexReader reader = IndexReader.open(dir, false);

      // Try to delete an invalid docId, yet, within range
      // of the final bits of the BitVector:

      boolean gotException = false;
      try {
        reader.deleteDocument(11);
      } catch (ArrayIndexOutOfBoundsException e) {
        gotException = true;
      }
      reader.close();

      writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));

      // We must add more docs to get a new segment written
      for(int i=0;i<11;i++) {
        addDoc(writer, "aaa");
      }

      // Without the fix for LUCENE-140 this call will
      // [incorrectly] hit a "docs out of order"
      // IllegalStateException because above out-of-bounds
      // deleteDocument corrupted the index:
      writer.optimize();
      writer.close();
      if (!gotException) {
        fail("delete of out-of-bounds doc number failed to hit exception");
      }
      dir.close();
    }

    public void testExceptionReleaseWriteLockJIRA768() throws IOException {

      Directory dir = newDirectory();      
      IndexWriter writer  = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
      addDoc(writer, "aaa");
      writer.close();

      IndexReader reader = IndexReader.open(dir, false);
      try {
        reader.deleteDocument(1);
        fail("did not hit exception when deleting an invalid doc number");
      } catch (ArrayIndexOutOfBoundsException e) {
        // expected
      }
      reader.close();
      if (IndexWriter.isLocked(dir)) {
        fail("write lock is still held after close");
      }

      reader = IndexReader.open(dir, false);
      try {
        reader.setNorm(1, "content", (float) 2.0);
        fail("did not hit exception when calling setNorm on an invalid doc number");
      } catch (ArrayIndexOutOfBoundsException e) {
        // expected
      }
      reader.close();
      if (IndexWriter.isLocked(dir)) {
        fail("write lock is still held after close");
      }
      dir.close();
    }

    private String arrayToString(String[] l) {
      String s = "";
      for(int i=0;i<l.length;i++) {
        if (i > 0) {
          s += "\n    ";
        }
        s += l[i];
      }
      return s;
    }

    public void testOpenReaderAfterDelete() throws IOException {
      File dirFile = _TestUtil.getTempDir("deletetest");
      Directory dir = newFSDirectory(dirFile);
      try {
        IndexReader.open(dir, false);
        fail("expected FileNotFoundException");
      } catch (FileNotFoundException e) {
        // expected
      }

      dirFile.delete();

      // Make sure we still get a CorruptIndexException (not NPE):
      try {
        IndexReader.open(dir, false);
        fail("expected FileNotFoundException");
      } catch (FileNotFoundException e) {
        // expected
      }
      
      dir.close();
    }

    static void addDocumentWithFields(IndexWriter writer) throws IOException
    {
        Document doc = new Document();
        doc.add(newField("keyword","test1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(newField("text","test1", Field.Store.YES, Field.Index.ANALYZED));
        doc.add(newField("unindexed","test1", Field.Store.YES, Field.Index.NO));
        doc.add(newField("unstored","test1", Field.Store.NO, Field.Index.ANALYZED));
        writer.addDocument(doc);
    }

    static void addDocumentWithDifferentFields(IndexWriter writer) throws IOException
    {
        Document doc = new Document();
        doc.add(newField("keyword2","test1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(newField("text2","test1", Field.Store.YES, Field.Index.ANALYZED));
        doc.add(newField("unindexed2","test1", Field.Store.YES, Field.Index.NO));
        doc.add(newField("unstored2","test1", Field.Store.NO, Field.Index.ANALYZED));
        writer.addDocument(doc);
    }

    static void addDocumentWithTermVectorFields(IndexWriter writer) throws IOException
    {
        Document doc = new Document();
        doc.add(newField("tvnot","tvnot", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.NO));
        doc.add(newField("termvector","termvector", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        doc.add(newField("tvoffset","tvoffset", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_OFFSETS));
        doc.add(newField("tvposition","tvposition", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS));
        doc.add(newField("tvpositionoffset","tvpositionoffset", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
        
        writer.addDocument(doc);
    }
    
    static void addDoc(IndexWriter writer, String value) throws IOException {
        Document doc = new Document();
        doc.add(newField("content", value, Field.Store.NO, Field.Index.ANALYZED));
        writer.addDocument(doc);
    }

    public static void assertIndexEquals(IndexReader index1, IndexReader index2) throws IOException {
      assertEquals("IndexReaders have different values for numDocs.", index1.numDocs(), index2.numDocs());
      assertEquals("IndexReaders have different values for maxDoc.", index1.maxDoc(), index2.maxDoc());
      assertEquals("Only one IndexReader has deletions.", index1.hasDeletions(), index2.hasDeletions());
      assertEquals("Only one index is optimized.", index1.isOptimized(), index2.isOptimized());
      
      // check field names
      Collection<String> fields1 = index1.getFieldNames(FieldOption.ALL);
      Collection<String> fields2 = index1.getFieldNames(FieldOption.ALL);
      assertEquals("IndexReaders have different numbers of fields.", fields1.size(), fields2.size());
      Iterator<String> it1 = fields1.iterator();
      Iterator<String> it2 = fields1.iterator();
      while (it1.hasNext()) {
        assertEquals("Different field names.", it1.next(), it2.next());
      }
      
      // check norms
      it1 = fields1.iterator();
      while (it1.hasNext()) {
        String curField = it1.next();
        byte[] norms1 = index1.norms(curField);
        byte[] norms2 = index2.norms(curField);
        if (norms1 != null && norms2 != null)
        {
          assertEquals(norms1.length, norms2.length);
	        for (int i = 0; i < norms1.length; i++) {
	          assertEquals("Norm different for doc " + i + " and field '" + curField + "'.", norms1[i], norms2[i]);
	        }
        }
        else
        {
          assertSame(norms1, norms2);
        }
      }
      
      // check deletions
      for (int i = 0; i < index1.maxDoc(); i++) {
        assertEquals("Doc " + i + " only deleted in one index.", index1.isDeleted(i), index2.isDeleted(i));
      }
      
      // check stored fields
      for (int i = 0; i < index1.maxDoc(); i++) {
        if (!index1.isDeleted(i)) {
          Document doc1 = index1.document(i);
          Document doc2 = index2.document(i);
          List<Fieldable> fieldable1 = doc1.getFields();
          List<Fieldable> fieldable2 = doc2.getFields();
          assertEquals("Different numbers of fields for doc " + i + ".", fieldable1.size(), fieldable2.size());
          Iterator<Fieldable> itField1 = fieldable1.iterator();
          Iterator<Fieldable> itField2 = fieldable2.iterator();
          while (itField1.hasNext()) {
            Field curField1 = (Field) itField1.next();
            Field curField2 = (Field) itField2.next();
            assertEquals("Different fields names for doc " + i + ".", curField1.name(), curField2.name());
            assertEquals("Different field values for doc " + i + ".", curField1.stringValue(), curField2.stringValue());
          }          
        }
      }
      
      // check dictionary and posting lists
      TermEnum enum1 = index1.terms();
      TermEnum enum2 = index2.terms();
      TermPositions tp1 = index1.termPositions();
      TermPositions tp2 = index2.termPositions();
      while(enum1.next()) {
        assertTrue(enum2.next());
        assertEquals("Different term in dictionary.", enum1.term(), enum2.term());
        tp1.seek(enum1.term());
        tp2.seek(enum1.term());
        while(tp1.next()) {
          assertTrue(tp2.next());
          assertEquals("Different doc id in postinglist of term " + enum1.term() + ".", tp1.doc(), tp2.doc());
          assertEquals("Different term frequence in postinglist of term " + enum1.term() + ".", tp1.freq(), tp2.freq());
          for (int i = 0; i < tp1.freq(); i++) {
            assertEquals("Different positions in postinglist of term " + enum1.term() + ".", tp1.nextPosition(), tp2.nextPosition());
          }
        }
      }
    }

    public void testGetIndexCommit() throws IOException {

      Directory d = newDirectory();

      // set up writer
      IndexWriter writer = new IndexWriter(
          d,
          newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
              setMaxBufferedDocs(2).
              setMergePolicy(newLogMergePolicy(10))
      );
      for(int i=0;i<27;i++)
        addDocumentWithFields(writer);
      writer.close();

      SegmentInfos sis = new SegmentInfos();
      sis.read(d);
      IndexReader r = IndexReader.open(d, false);
      IndexCommit c = r.getIndexCommit();

      assertEquals(sis.getCurrentSegmentFileName(), c.getSegmentsFileName());

      assertTrue(c.equals(r.getIndexCommit()));

      // Change the index
      writer = new IndexWriter(
          d,
          newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
              setOpenMode(OpenMode.APPEND).
              setMaxBufferedDocs(2).
              setMergePolicy(newLogMergePolicy(10))
      );
      for(int i=0;i<7;i++)
        addDocumentWithFields(writer);
      writer.close();

      IndexReader r2 = r.reopen();
      assertFalse(c.equals(r2.getIndexCommit()));
      assertFalse(r2.getIndexCommit().isOptimized());
      r2.close();

      writer = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer(random))
        .setOpenMode(OpenMode.APPEND));
      writer.optimize();
      writer.close();

      r2 = r.reopen();
      assertTrue(r2.getIndexCommit().isOptimized());

      r.close();
      r2.close();
      d.close();
    }      

    public void testReadOnly() throws Throwable {
      Directory d = newDirectory();
      IndexWriter writer = new IndexWriter(d, newIndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer(random)));
      addDocumentWithFields(writer);
      writer.commit();
      addDocumentWithFields(writer);
      writer.close();

      IndexReader r = IndexReader.open(d, true);
      try {
        r.deleteDocument(0);
        fail();
      } catch (UnsupportedOperationException uoe) {
        // expected
      }

      writer = new IndexWriter(
          d,
          newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
              setOpenMode(OpenMode.APPEND).
              setMergePolicy(newLogMergePolicy(10))
      );
      addDocumentWithFields(writer);
      writer.close();

      // Make sure reopen is still readonly:
      IndexReader r2 = r.reopen();
      r.close();

      assertFalse(r == r2);

      try {
        r2.deleteDocument(0);
        fail();
      } catch (UnsupportedOperationException uoe) {
        // expected
      }

      writer = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer(random))
        .setOpenMode(OpenMode.APPEND));
      writer.optimize();
      writer.close();

      // Make sure reopen to a single segment is still readonly:
      IndexReader r3 = r2.reopen();
      assertFalse(r3 == r2);
      r2.close();
      
      assertFalse(r == r2);

      try {
        r3.deleteDocument(0);
        fail();
      } catch (UnsupportedOperationException uoe) {
        // expected
      }

      // Make sure write lock isn't held
      writer = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT,
          new MockAnalyzer(random))
      .setOpenMode(OpenMode.APPEND));
      writer.close();

      r3.close();
      d.close();
    }


  // LUCENE-1474
  public void testIndexReader() throws Exception {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    writer.addDocument(createDocument("a"));
    writer.addDocument(createDocument("b"));
    writer.addDocument(createDocument("c"));
    writer.close();
    IndexReader reader = IndexReader.open(dir, false);
    reader.deleteDocuments(new Term("id", "a"));
    reader.flush();
    reader.deleteDocuments(new Term("id", "b"));
    reader.close();
    IndexReader.open(dir,true).close();
    dir.close();
  }

  static Document createDocument(String id) {
    Document doc = new Document();
    doc.add(newField("id", id, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
    return doc;
  }

  // LUCENE-1468 -- make sure on attempting to open an
  // IndexReader on a non-existent directory, you get a
  // good exception
  public void testNoDir() throws Throwable {
    Directory dir = newFSDirectory(_TestUtil.getTempDir("doesnotexist"));
    try {
      IndexReader.open(dir, true);
      fail("did not hit expected exception");
    } catch (NoSuchDirectoryException nsde) {
      // expected
    }
    dir.close();
  }

  // LUCENE-1509
  public void testNoDupCommitFileNames() throws Throwable {

    Directory dir = newDirectory();
    
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer(random))
        .setMaxBufferedDocs(2));
    writer.addDocument(createDocument("a"));
    writer.addDocument(createDocument("a"));
    writer.addDocument(createDocument("a"));
    writer.close();
    
    Collection<IndexCommit> commits = IndexReader.listCommits(dir);
    for (final IndexCommit commit : commits) {
      Collection<String> files = commit.getFileNames();
      HashSet<String> seen = new HashSet<String>();
      for (final String fileName : files) { 
        assertTrue("file " + fileName + " was duplicated", !seen.contains(fileName));
        seen.add(fileName);
      }
    }

    dir.close();
  }

  // LUCENE-1579: Ensure that on a cloned reader, segments
  // reuse the doc values arrays in FieldCache
  public void testFieldCacheReuseAfterClone() throws Exception {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    Document doc = new Document();
    doc.add(newField("number", "17", Field.Store.NO, Field.Index.NOT_ANALYZED));
    writer.addDocument(doc);
    writer.close();

    // Open reader
    IndexReader r = SegmentReader.getOnlySegmentReader(dir);
    final int[] ints = FieldCache.DEFAULT.getInts(r, "number");
    assertEquals(1, ints.length);
    assertEquals(17, ints[0]);

    // Clone reader
    IndexReader r2 = (IndexReader) r.clone();
    r.close();
    assertTrue(r2 != r);
    final int[] ints2 = FieldCache.DEFAULT.getInts(r2, "number");
    r2.close();

    assertEquals(1, ints2.length);
    assertEquals(17, ints2[0]);
    assertTrue(ints == ints2);

    dir.close();
  }

  // LUCENE-1579: Ensure that on a reopened reader, that any
  // shared segments reuse the doc values arrays in
  // FieldCache
  public void testFieldCacheReuseAfterReopen() throws Exception {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(
        dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setMergePolicy(newLogMergePolicy(10))
    );
    Document doc = new Document();
    doc.add(newField("number", "17", Field.Store.NO, Field.Index.NOT_ANALYZED));
    ((LogMergePolicy) writer.getMergePolicy()).setMergeFactor(10);
    writer.addDocument(doc);
    writer.commit();

    // Open reader1
    IndexReader r = IndexReader.open(dir, false);
    IndexReader r1 = SegmentReader.getOnlySegmentReader(r);
    final int[] ints = FieldCache.DEFAULT.getInts(r1, "number");
    assertEquals(1, ints.length);
    assertEquals(17, ints[0]);

    // Add new segment
    writer.addDocument(doc);
    writer.commit();

    // Reopen reader1 --> reader2
    IndexReader r2 = r.reopen();
    r.close();
    IndexReader sub0 = r2.getSequentialSubReaders()[0];
    final int[] ints2 = FieldCache.DEFAULT.getInts(sub0, "number");
    r2.close();
    assertTrue(ints == ints2);

    writer.close();
    dir.close();
  }

  // LUCENE-1586: getUniqueTermCount
  public void testUniqueTermCount() throws Exception {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    Document doc = new Document();
    doc.add(newField("field", "a b c d e f g h i j k l m n o p q r s t u v w x y z", Field.Store.NO, Field.Index.ANALYZED));
    doc.add(newField("number", "0 1 2 3 4 5 6 7 8 9", Field.Store.NO, Field.Index.ANALYZED));
    writer.addDocument(doc);
    writer.addDocument(doc);
    writer.commit();

    IndexReader r = IndexReader.open(dir, false);
    IndexReader r1 = SegmentReader.getOnlySegmentReader(r);
    assertEquals(36, r1.getUniqueTermCount());
    writer.addDocument(doc);
    writer.commit();
    IndexReader r2 = r.reopen();
    r.close();
    try {
      r2.getUniqueTermCount();
      fail("expected exception");
    } catch (UnsupportedOperationException uoe) {
      // expected
    }
    IndexReader[] subs = r2.getSequentialSubReaders();
    for(int i=0;i<subs.length;i++) {
      assertEquals(36, subs[i].getUniqueTermCount());
    }
    r2.close();
    writer.close();
    dir.close();
  }

  // LUCENE-1609: don't load terms index
  public void testNoTermsIndex() throws Throwable {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    Document doc = new Document();
    doc.add(newField("field", "a b c d e f g h i j k l m n o p q r s t u v w x y z", Field.Store.NO, Field.Index.ANALYZED));
    doc.add(newField("number", "0 1 2 3 4 5 6 7 8 9", Field.Store.NO, Field.Index.ANALYZED));
    writer.addDocument(doc);
    writer.addDocument(doc);
    writer.close();

    IndexReader r = IndexReader.open(dir, null, true, -1);
    try {
      r.docFreq(new Term("field", "f"));
      fail("did not hit expected exception");
    } catch (IllegalStateException ise) {
      // expected
    }
    assertFalse(((SegmentReader) r.getSequentialSubReaders()[0]).termsIndexLoaded());

    assertEquals(-1, ((SegmentReader) r.getSequentialSubReaders()[0]).getTermInfosIndexDivisor());
    writer = new IndexWriter(
        dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setMergePolicy(newLogMergePolicy(10))
    );
    writer.addDocument(doc);
    writer.close();

    // LUCENE-1718: ensure re-open carries over no terms index:
    IndexReader r2 = r.reopen();
    r.close();
    IndexReader[] subReaders = r2.getSequentialSubReaders();
    assertEquals(2, subReaders.length);
    for(int i=0;i<2;i++) {
      assertFalse(((SegmentReader) subReaders[i]).termsIndexLoaded());
    }
    r2.close();
    dir.close();
  }

  // LUCENE-2046
  public void testPrepareCommitIsCurrent() throws Throwable {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig( 
        TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    writer.commit();
    Document doc = new Document();
    writer.addDocument(doc);
    IndexReader r = IndexReader.open(dir, true);
    assertTrue(r.isCurrent());
    writer.addDocument(doc);
    writer.prepareCommit();
    assertTrue(r.isCurrent());
    IndexReader r2 = r.reopen();
    assertTrue(r == r2);
    writer.commit();
    assertFalse(r.isCurrent());
    writer.close();
    r.close();
    dir.close();
  }
  
  // LUCENE-2753
  public void testListCommits() throws Exception {
    Directory dir = newDirectory();
    SnapshotDeletionPolicy sdp = new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig( 
        TEST_VERSION_CURRENT, new WhitespaceAnalyzer(TEST_VERSION_CURRENT)).setIndexDeletionPolicy(sdp));
    writer.addDocument(new Document());
    writer.commit();
    sdp.snapshot("c1");
    writer.addDocument(new Document());
    writer.commit();
    sdp.snapshot("c2");
    writer.addDocument(new Document());
    writer.commit();
    sdp.snapshot("c3");
    writer.close();
    long currentGen = 0;
    for (IndexCommit ic : IndexReader.listCommits(dir)) {
      assertTrue("currentGen=" + currentGen + " commitGen=" + ic.getGeneration(), currentGen < ic.getGeneration());
      currentGen = ic.getGeneration();
    }
    dir.close();
  }

  // LUCENE-2812
  public void testIndexExists() throws Exception {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    writer.addDocument(new Document());
    writer.prepareCommit();
    assertFalse(IndexReader.indexExists(dir));
    writer.close();
    assertTrue(IndexReader.indexExists(dir));
    dir.close();
  }

  // LUCENE-2474
  public void testReaderFinishedListener() throws Exception {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setMergePolicy(newLogMergePolicy()));
    ((LogMergePolicy) writer.getConfig().getMergePolicy()).setMergeFactor(3);
    writer.setInfoStream(VERBOSE ? System.out : null);
    writer.addDocument(new Document());
    writer.commit();
    writer.addDocument(new Document());
    writer.commit();
    final IndexReader reader = writer.getReader();
    final int[] closeCount = new int[1];
    final IndexReader.ReaderFinishedListener listener = new IndexReader.ReaderFinishedListener() {
      public void finished(IndexReader reader) {
        closeCount[0]++;
      }
    };

    reader.addReaderFinishedListener(listener);

    reader.close();

    // Just the top reader
    assertEquals(1, closeCount[0]);
    writer.close();

    // Now also the subs
    assertEquals(3, closeCount[0]);

    IndexReader reader2 = IndexReader.open(dir);
    reader2.addReaderFinishedListener(listener);

    closeCount[0] = 0;
    reader2.close();
    assertEquals(3, closeCount[0]);
    dir.close();
  }

  public void testOOBDocID() throws Exception {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    writer.addDocument(new Document());
    IndexReader r = writer.getReader();
    writer.close();
    r.document(0);
    try {
      r.document(1);
      fail("did not hit exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
    r.close();
    dir.close();
  }
  
  public void testTryIncRef() throws CorruptIndexException, LockObtainFailedException, IOException {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    writer.addDocument(new Document());
    writer.commit();
    IndexReader r = IndexReader.open(dir);
    assertTrue(r.tryIncRef());
    r.decRef();
    r.close();
    assertFalse(r.tryIncRef());
    writer.close();
    dir.close();
  }
  
  public void testStressTryIncRef() throws CorruptIndexException, LockObtainFailedException, IOException, InterruptedException {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    writer.addDocument(new Document());
    writer.commit();
    IndexReader r = IndexReader.open(dir);
    int numThreads = atLeast(2);
    
    IncThread[] threads = new IncThread[numThreads];
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new IncThread(r, random);
      threads[i].start();
    }
    Thread.sleep(100);

    assertTrue(r.tryIncRef());
    r.decRef();
    r.close();

    for (int i = 0; i < threads.length; i++) {
      threads[i].join();
      assertNull(threads[i].failed);
    }
    assertFalse(r.tryIncRef());
    writer.close();
    dir.close();
  }
  
  static class IncThread extends Thread {
    final IndexReader toInc;
    final Random random;
    Throwable failed;
    
    IncThread(IndexReader toInc, Random random) {
      this.toInc = toInc;
      this.random = random;
    }
    
    @Override
    public void run() {
      try {
        while (toInc.tryIncRef()) {
          assertFalse(toInc.hasDeletions());
          toInc.decRef();
        }
        assertFalse(toInc.tryIncRef());
      } catch (Throwable e) {
        failed = e;
      }
    }
  }
}
