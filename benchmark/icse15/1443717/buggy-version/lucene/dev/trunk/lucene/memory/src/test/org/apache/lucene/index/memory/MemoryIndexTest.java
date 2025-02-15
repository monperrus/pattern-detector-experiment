  Merged /lucene/dev/branches/lucene4547/lucene/join:r1407149-1443597
  Merged /lucene/dev/branches/lucene4547/lucene/tools:r1407149-1443597
  Merged /lucene/dev/branches/lucene4547/lucene/backwards:r1407149-1443597
  Merged /lucene/dev/branches/lucene4547/lucene/site:r1407149-1443597
  Merged /lucene/dev/branches/slowclosing/lucene/licenses:r1393532-1393785
  Merged /lucene/dev/branches/lucene3969/lucene/licenses:r1311219-1324948
  Merged /lucene/dev/branches/branch_3x/lucene/licenses:r1232954,1302749,1302808,1303007,1303023,1303269,1303733,1303854,1304295,1304360,1304660,1304904,1305074,1305142,1305681,1305693,1305719,1305741,1305816,1305837,1306929,1307050
  Merged /lucene/dev/branches/branch_4x/lucene/licenses:r1344391,1344929,1348012,1348274,1348293,1348919,1348951,1349048,1349340,1349446,1349991,1353701,1355203,1356608,1359358,1363876,1364063,1364069,1367391,1367489,1367833,1368975,1369226,1371960,1374622,1375497,1375558,1376547,1378442,1378591,1379175,1380802,1381204,1383216,1386921,1388425,1389811,1389929,1392460,1393832,1394309,1395515,1404227,1405891
  Merged /lucene/dev/branches/lucene_solr_4_0/lucene/licenses:r1388937,1389448,1390046,1394306
  Merged /lucene/dev/branches/cleanup2878/lucene/licenses:r1403701-1403781
  Merged /lucene/dev/branches/lucene2510/lucene/licenses:r1364862-1365496
  Merged /lucene/dev/branches/lucene3312/lucene/licenses:r1357905-1379945
  Merged /lucene/dev/branches/lucene4055/lucene/licenses:r1338960-1343359
  Merged /lucene/dev/branches/pforcodec_3892/lucene/licenses:r1352188-1375470
  Merged /lucene/dev/branches/ghost_of_4456/lucene/licenses:r1394211-1394305
  Merged /lucene/dev/branches/lucene4446/lucene/licenses:r1397400-1398082
  Merged /lucene/dev/branches/lucene4547/lucene/licenses:r1407149-1443597
  Merged /lucene/dev/branches/solr3733/lucene/licenses:r1388080-1388269
  Merged /lucene/dev/branches/lucene3846/lucene/licenses:r1397170-1403761
  Merged /lucene/dev/branches/lucene4199/lucene/licenses:r1358548-1359191
package org.apache.lucene.index.memory;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.codecs.lucene41.Lucene41PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.CompositeReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.DocValues.Source;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.ByteBlockPool.Allocator;
import org.apache.lucene.util.ByteBlockPool;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LineFileDocs;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.RecyclingByteBlockAllocator;
import org.apache.lucene.util._TestUtil;

/**
 * Verifies that Lucene MemoryIndex and RAMDirectory have the same behaviour,
 * returning the same results for queries on some randomish indexes.
 */
public class MemoryIndexTest extends BaseTokenStreamTestCase {
  private Set<String> queries = new HashSet<String>();
  
  public static final int ITERATIONS = 100 * RANDOM_MULTIPLIER;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    queries.addAll(readQueries("testqueries.txt"));
    queries.addAll(readQueries("testqueries2.txt"));
  }
  
  /**
   * read a set of queries from a resource file
   */
  private Set<String> readQueries(String resource) throws IOException {
    Set<String> queries = new HashSet<String>();
    InputStream stream = getClass().getResourceAsStream(resource);
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
    String line = null;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (line.length() > 0 && !line.startsWith("#") && !line.startsWith("//")) {
        queries.add(line);
      }
    }
    return queries;
  }
  
  
  /**
   * runs random tests, up to ITERATIONS times.
   */
  public void testRandomQueries() throws Exception {
    MemoryIndex index =  new MemoryIndex(random().nextBoolean(), random().nextInt(50) * 1024 * 1024);
    for (int i = 0; i < ITERATIONS; i++) {
      assertAgainstRAMDirectory(index);
    }
  }
  
  /**
   * Build a randomish document for both RAMDirectory and MemoryIndex,
   * and run all the queries against it.
   */
  public void assertAgainstRAMDirectory(MemoryIndex memory) throws Exception {
    memory.reset();
    StringBuilder fooField = new StringBuilder();
    StringBuilder termField = new StringBuilder();
 
    // add up to 250 terms to field "foo"
    final int numFooTerms = random().nextInt(250 * RANDOM_MULTIPLIER);
    for (int i = 0; i < numFooTerms; i++) {
      fooField.append(" ");
      fooField.append(randomTerm());
    }

    // add up to 250 terms to field "term"
    final int numTermTerms = random().nextInt(250 * RANDOM_MULTIPLIER);
    for (int i = 0; i < numTermTerms; i++) {
      termField.append(" ");
      termField.append(randomTerm());
    }
    
    Directory ramdir = new RAMDirectory();
    Analyzer analyzer = randomAnalyzer();
    IndexWriter writer = new IndexWriter(ramdir,
                                         new IndexWriterConfig(TEST_VERSION_CURRENT, analyzer).setCodec(_TestUtil.alwaysPostingsFormat(new Lucene41PostingsFormat())));
    Document doc = new Document();
    Field field1 = newTextField("foo", fooField.toString(), Field.Store.NO);
    Field field2 = newTextField("term", termField.toString(), Field.Store.NO);
    doc.add(field1);
    doc.add(field2);
    writer.addDocument(doc);
    writer.close();
    
    memory.addField("foo", fooField.toString(), analyzer);
    memory.addField("term", termField.toString(), analyzer);
    
    if (VERBOSE) {
      System.out.println("Random MemoryIndex:\n" + memory.toString());
      System.out.println("Same index as RAMDirectory: " +
        RamUsageEstimator.humanReadableUnits(RamUsageEstimator.sizeOf(ramdir)));
      System.out.println();
    } else {
      assertTrue(memory.getMemorySize() > 0L);
    }
    AtomicReader reader = (AtomicReader) memory.createSearcher().getIndexReader();
    DirectoryReader competitor = DirectoryReader.open(ramdir);
    duellReaders(competitor, reader);
    IOUtils.close(reader, competitor);
    assertAllQueries(memory, ramdir, analyzer);  
    ramdir.close();    
  }

  private void duellReaders(CompositeReader other, AtomicReader memIndexReader)
      throws IOException {
    AtomicReader competitor = new SlowCompositeReaderWrapper(other);
    Fields memFields = memIndexReader.fields();
    for (String field : competitor.fields()) {
      Terms memTerms = memFields.terms(field);
      Terms iwTerms = memIndexReader.terms(field);
      if (iwTerms == null) {
        assertNull(memTerms);
      } else {
        DocValues normValues = competitor.normValues(field);
        DocValues memNormValues = memIndexReader.normValues(field);
        if (normValues != null) {
          // mem idx always computes norms on the fly
          assertNotNull(memNormValues);
          assertEquals(normValues.getDirectSource().getInt(0), memNormValues.getDirectSource().getInt(0), 0.01);
        }
          
        assertNotNull(memTerms);
        assertEquals(iwTerms.getDocCount(), memTerms.getDocCount());
        assertEquals(iwTerms.getSumDocFreq(), memTerms.getSumDocFreq());
        assertEquals(iwTerms.getSumTotalTermFreq(), memTerms.getSumTotalTermFreq());
        TermsEnum iwTermsIter = iwTerms.iterator(null);
        TermsEnum memTermsIter = memTerms.iterator(null);
        if (iwTerms.hasPositions()) {
          final boolean offsets = iwTerms.hasOffsets() && memTerms.hasOffsets();
         
          while(iwTermsIter.next() != null) {
            assertNotNull(memTermsIter.next());
            assertEquals(iwTermsIter.term(), memTermsIter.term());
            DocsAndPositionsEnum iwDocsAndPos = iwTermsIter.docsAndPositions(null, null);
            DocsAndPositionsEnum memDocsAndPos = memTermsIter.docsAndPositions(null, null);
            while(iwDocsAndPos.nextDoc() != DocsAndPositionsEnum.NO_MORE_DOCS) {
              assertEquals(iwDocsAndPos.docID(), memDocsAndPos.nextDoc());
              assertEquals(iwDocsAndPos.freq(), memDocsAndPos.freq());
              for (int i = 0; i < iwDocsAndPos.freq(); i++) {
                assertEquals("term: " + iwTermsIter.term().utf8ToString(), iwDocsAndPos.nextPosition(), memDocsAndPos.nextPosition());
                if (offsets) {
                  assertEquals(iwDocsAndPos.startOffset(), memDocsAndPos.startOffset());
                  assertEquals(iwDocsAndPos.endOffset(), memDocsAndPos.endOffset());
                }
              }
              
            }
            
          }
        } else {
          while(iwTermsIter.next() != null) {
            assertEquals(iwTermsIter.term(), memTermsIter.term());
            DocsEnum iwDocsAndPos = iwTermsIter.docs(null, null);
            DocsEnum memDocsAndPos = memTermsIter.docs(null, null);
            while(iwDocsAndPos.nextDoc() != DocsAndPositionsEnum.NO_MORE_DOCS) {
              assertEquals(iwDocsAndPos.docID(), memDocsAndPos.nextDoc());
              assertEquals(iwDocsAndPos.freq(), memDocsAndPos.freq());
            }
          }
        }
      }
      
    }
  }
  
  /**
   * Run all queries against both the RAMDirectory and MemoryIndex, ensuring they are the same.
   */
  public void assertAllQueries(MemoryIndex memory, Directory ramdir, Analyzer analyzer) throws Exception {
    IndexReader reader = DirectoryReader.open(ramdir);
    IndexSearcher ram = new IndexSearcher(reader);
    IndexSearcher mem = memory.createSearcher();
    QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "foo", analyzer);
    for (String query : queries) {
      TopDocs ramDocs = ram.search(qp.parse(query), 1);
      TopDocs memDocs = mem.search(qp.parse(query), 1);
      assertEquals(query, ramDocs.totalHits, memDocs.totalHits);
    }
    reader.close();
  }
  
  /**
   * Return a random analyzer (Simple, Stop, Standard) to analyze the terms.
   */
  private Analyzer randomAnalyzer() {
    switch(random().nextInt(3)) {
      case 0: return new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
      case 1: return new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET, true);
      default: return new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    }
  }
  
  /**
   * Some terms to be indexed, in addition to random words. 
   * These terms are commonly used in the queries. 
   */
  private static final String[] TEST_TERMS = {"term", "Term", "tErm", "TERM",
      "telm", "stop", "drop", "roll", "phrase", "a", "c", "bar", "blar",
      "gack", "weltbank", "worlbank", "hello", "on", "the", "apache", "Apache",
      "copyright", "Copyright"};
  
  
  /**
   * half of the time, returns a random term from TEST_TERMS.
   * the other half of the time, returns a random unicode string.
   */
  private String randomTerm() {
    if (random().nextBoolean()) {
      // return a random TEST_TERM
      return TEST_TERMS[random().nextInt(TEST_TERMS.length)];
    } else {
      // return a random unicode term
      return _TestUtil.randomUnicodeString(random());
    }
  }
  
  public void testDocsEnumStart() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    MemoryIndex memory = new MemoryIndex(random().nextBoolean(),  random().nextInt(50) * 1024 * 1024);
    memory.addField("foo", "bar", analyzer);
    AtomicReader reader = (AtomicReader) memory.createSearcher().getIndexReader();
    DocsEnum disi = _TestUtil.docs(random(), reader, "foo", new BytesRef("bar"), null, null, DocsEnum.FLAG_NONE);
    int docid = disi.docID();
    assertTrue(docid == -1 || docid == DocIdSetIterator.NO_MORE_DOCS);
    assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
    
    // now reuse and check again
    TermsEnum te = reader.terms("foo").iterator(null);
    assertTrue(te.seekExact(new BytesRef("bar"), true));
    disi = te.docs(null, disi, DocsEnum.FLAG_NONE);
    docid = disi.docID();
    assertTrue(docid == -1 || docid == DocIdSetIterator.NO_MORE_DOCS);
    assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
    reader.close();
  }
  
  private Allocator randomByteBlockAllocator() {
    if (random().nextBoolean()) {
      return new RecyclingByteBlockAllocator();
    } else {
      return new ByteBlockPool.DirectAllocator();
    }
  }
  
  public void testDocsAndPositionsEnumStart() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    int numIters = atLeast(3);
    MemoryIndex memory = new MemoryIndex(true,  random().nextInt(50) * 1024 * 1024);
    for (int i = 0; i < numIters; i++) { // check reuse
      memory.addField("foo", "bar", analyzer);
      AtomicReader reader = (AtomicReader) memory.createSearcher().getIndexReader();
      assertEquals(1, reader.terms("foo").getSumTotalTermFreq());
      DocsAndPositionsEnum disi = reader.termPositionsEnum(new Term("foo", "bar"));
      int docid = disi.docID();
      assertTrue(docid == -1 || docid == DocIdSetIterator.NO_MORE_DOCS);
      assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
      assertEquals(0, disi.nextPosition());
      assertEquals(0, disi.startOffset());
      assertEquals(3, disi.endOffset());
      
      // now reuse and check again
      TermsEnum te = reader.terms("foo").iterator(null);
      assertTrue(te.seekExact(new BytesRef("bar"), true));
      disi = te.docsAndPositions(null, disi);
      docid = disi.docID();
      assertTrue(docid == -1 || docid == DocIdSetIterator.NO_MORE_DOCS);
      assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
      reader.close();
      memory.reset();
    }
  }

  // LUCENE-3831
  public void testNullPointerException() throws IOException {
    RegexpQuery regex = new RegexpQuery(new Term("field", "worl."));
    SpanQuery wrappedquery = new SpanMultiTermQueryWrapper<RegexpQuery>(regex);
        
    MemoryIndex mindex = new MemoryIndex(random().nextBoolean(),  random().nextInt(50) * 1024 * 1024);
    mindex.addField("field", new MockAnalyzer(random()).tokenStream("field", new StringReader("hello there")));

    // This throws an NPE
    assertEquals(0, mindex.search(wrappedquery), 0.00001f);
  }
    
  // LUCENE-3831
  public void testPassesIfWrapped() throws IOException {
    RegexpQuery regex = new RegexpQuery(new Term("field", "worl."));
    SpanQuery wrappedquery = new SpanOrQuery(new SpanMultiTermQueryWrapper<RegexpQuery>(regex));

    MemoryIndex mindex = new MemoryIndex(random().nextBoolean(),  random().nextInt(50) * 1024 * 1024);
    mindex.addField("field", new MockAnalyzer(random()).tokenStream("field", new StringReader("hello there")));

    // This passes though
    assertEquals(0, mindex.search(wrappedquery), 0.00001f);
  }
  
  public void testSameFieldAddedMultipleTimes() throws IOException {
    MemoryIndex mindex = new MemoryIndex(random().nextBoolean(),  random().nextInt(50) * 1024 * 1024);
    MockAnalyzer mockAnalyzer = new MockAnalyzer(random());
    mindex.addField("field", "the quick brown fox", mockAnalyzer);
    mindex.addField("field", "jumps over the", mockAnalyzer);
    AtomicReader reader = (AtomicReader) mindex.createSearcher().getIndexReader();
    assertEquals(7, reader.terms("field").getSumTotalTermFreq());
    PhraseQuery query = new PhraseQuery();
    query.add(new Term("field", "fox"));
    query.add(new Term("field", "jumps"));
    assertTrue(mindex.search(query) > 0.1);
    mindex.reset();
    mockAnalyzer.setPositionIncrementGap(1 + random().nextInt(10));
    mindex.addField("field", "the quick brown fox", mockAnalyzer);
    mindex.addField("field", "jumps over the", mockAnalyzer);
    assertEquals(0, mindex.search(query), 0.00001f);
    query.setSlop(10);
    assertTrue("posGap" + mockAnalyzer.getPositionIncrementGap("field") , mindex.search(query) > 0.0001);
  }
  
  public void testNonExistingsField() throws IOException {
    MemoryIndex mindex = new MemoryIndex(random().nextBoolean(),  random().nextInt(50) * 1024 * 1024);
    MockAnalyzer mockAnalyzer = new MockAnalyzer(random());
    mindex.addField("field", "the quick brown fox", mockAnalyzer);
    AtomicReader reader = (AtomicReader) mindex.createSearcher().getIndexReader();
    assertNull(reader.docValues("not-in-index"));
    assertNull(reader.normValues("not-in-index"));
    assertNull(reader.termDocsEnum(new Term("not-in-index", "foo")));
    assertNull(reader.termPositionsEnum(new Term("not-in-index", "foo")));
    assertNull(reader.terms("not-in-index"));
  }
  
  public void testDuellMemIndex() throws IOException {
    LineFileDocs lineFileDocs = new LineFileDocs(random());
    int numDocs = atLeast(10);
    MemoryIndex memory = new MemoryIndex(random().nextBoolean(),  random().nextInt(50) * 1024 * 1024);
    for (int i = 0; i < numDocs; i++) {
      Directory dir = newDirectory();
      MockAnalyzer mockAnalyzer = new MockAnalyzer(random());
      IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(random(), TEST_VERSION_CURRENT, mockAnalyzer));
      Document nextDoc = lineFileDocs.nextDoc();
      Document doc = new Document();
      for (Field field : nextDoc.getFields()) {
        if (field.fieldType().indexed()) {
          doc.add(field);
          if (random().nextInt(3) == 0) {
            doc.add(field);  // randomly add the same field twice
          }
        }
      }
      
      writer.addDocument(doc);
      writer.close();
      for (IndexableField field : doc.indexableFields()) {
          memory.addField(field.name(), ((Field)field).stringValue(), mockAnalyzer);  
      }
      DirectoryReader competitor = DirectoryReader.open(dir);
      AtomicReader memIndexReader= (AtomicReader) memory.createSearcher().getIndexReader();
      duellReaders(competitor, memIndexReader);
      IOUtils.close(competitor, memIndexReader);
      memory.reset();
      dir.close();
    }
    lineFileDocs.close();
  }
}
