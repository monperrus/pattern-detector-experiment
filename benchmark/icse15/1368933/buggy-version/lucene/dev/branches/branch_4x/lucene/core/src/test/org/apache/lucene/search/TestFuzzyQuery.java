  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1368927
package org.apache.lucene.search;

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

import java.util.List;
import java.util.Arrays;
import java.io.IOException;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Tests {@link FuzzyQuery}.
 *
 */
public class TestFuzzyQuery extends LuceneTestCase {

  public void testFuzziness() throws Exception {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory);
    addDoc("aaaaa", writer);
    addDoc("aaaab", writer);
    addDoc("aaabb", writer);
    addDoc("aabbb", writer);
    addDoc("abbbb", writer);
    addDoc("bbbbb", writer);
    addDoc("ddddd", writer);

    IndexReader reader = writer.getReader();
    IndexSearcher searcher = newSearcher(reader);
    writer.close();

    FuzzyQuery query = new FuzzyQuery(new Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 0);   
    ScoreDoc[] hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    
    // same with prefix
    query = new FuzzyQuery(new Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    query = new FuzzyQuery(new Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    query = new FuzzyQuery(new Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 3);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    query = new FuzzyQuery(new Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 4);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(2, hits.length);
    query = new FuzzyQuery(new Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 5);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    query = new FuzzyQuery(new Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 6);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    
    // test scoring
    query = new FuzzyQuery(new Term("field", "bbbbb"), FuzzyQuery.defaultMaxEdits, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals("3 documents should match", 3, hits.length);
    List<String> order = Arrays.asList("bbbbb","abbbb","aabbb");
    for (int i = 0; i < hits.length; i++) {
      final String term = searcher.doc(hits[i].doc).get("field");
      //System.out.println(hits[i].score);
      assertEquals(order.get(i), term);
    }

    // test pq size by supplying maxExpansions=2
    // This query would normally return 3 documents, because 3 terms match (see above):
    query = new FuzzyQuery(new Term("field", "bbbbb"), FuzzyQuery.defaultMaxEdits, 0, 2, false); 
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals("only 2 documents should match", 2, hits.length);
    order = Arrays.asList("bbbbb","abbbb");
    for (int i = 0; i < hits.length; i++) {
      final String term = searcher.doc(hits[i].doc).get("field");
      //System.out.println(hits[i].score);
      assertEquals(order.get(i), term);
    }

    // not similar enough:
    query = new FuzzyQuery(new Term("field", "xxxxx"), FuzzyQuery.defaultMaxEdits, 0);  	
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    query = new FuzzyQuery(new Term("field", "aaccc"), FuzzyQuery.defaultMaxEdits, 0);   // edit distance to "aaaaa" = 3
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);

    // query identical to a word in the index:
    query = new FuzzyQuery(new Term("field", "aaaaa"), FuzzyQuery.defaultMaxEdits, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("aaaaa"));
    // default allows for up to two edits:
    assertEquals(searcher.doc(hits[1].doc).get("field"), ("aaaab"));
    assertEquals(searcher.doc(hits[2].doc).get("field"), ("aaabb"));

    // query similar to a word in the index:
    query = new FuzzyQuery(new Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("aaaaa"));
    assertEquals(searcher.doc(hits[1].doc).get("field"), ("aaaab"));
    assertEquals(searcher.doc(hits[2].doc).get("field"), ("aaabb"));
    
    // now with prefix
    query = new FuzzyQuery(new Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("aaaaa"));
    assertEquals(searcher.doc(hits[1].doc).get("field"), ("aaaab"));
    assertEquals(searcher.doc(hits[2].doc).get("field"), ("aaabb"));
    query = new FuzzyQuery(new Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("aaaaa"));
    assertEquals(searcher.doc(hits[1].doc).get("field"), ("aaaab"));
    assertEquals(searcher.doc(hits[2].doc).get("field"), ("aaabb"));
    query = new FuzzyQuery(new Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 3);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("aaaaa"));
    assertEquals(searcher.doc(hits[1].doc).get("field"), ("aaaab"));
    assertEquals(searcher.doc(hits[2].doc).get("field"), ("aaabb"));
    query = new FuzzyQuery(new Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 4);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(2, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("aaaaa"));
    assertEquals(searcher.doc(hits[1].doc).get("field"), ("aaaab"));
    query = new FuzzyQuery(new Term("field", "aaaac"), FuzzyQuery.defaultMaxEdits, 5);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    

    query = new FuzzyQuery(new Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("ddddd"));
    
    // now with prefix
    query = new FuzzyQuery(new Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("ddddd"));
    query = new FuzzyQuery(new Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("ddddd"));
    query = new FuzzyQuery(new Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 3);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("ddddd"));
    query = new FuzzyQuery(new Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 4);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(searcher.doc(hits[0].doc).get("field"), ("ddddd"));
    query = new FuzzyQuery(new Term("field", "ddddX"), FuzzyQuery.defaultMaxEdits, 5);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    

    // different field = no match:
    query = new FuzzyQuery(new Term("anotherfield", "ddddX"), FuzzyQuery.defaultMaxEdits, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);

    reader.close();
    directory.close();
  }
  
  /** 
   * MultiTermQuery provides (via attribute) information about which values
   * must be competitive to enter the priority queue. 
   * 
   * FuzzyQuery optimizes itself around this information, if the attribute
   * is not implemented correctly, there will be problems!
   */
  public void testTieBreaker() throws Exception {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory);
    addDoc("a123456", writer);
    addDoc("c123456", writer);
    addDoc("d123456", writer);
    addDoc("e123456", writer);
    
    Directory directory2 = newDirectory();
    RandomIndexWriter writer2 = new RandomIndexWriter(random(), directory2);
    addDoc("a123456", writer2);
    addDoc("b123456", writer2);
    addDoc("b123456", writer2);
    addDoc("b123456", writer2);
    addDoc("c123456", writer2);
    addDoc("f123456", writer2);
    
    IndexReader ir1 = writer.getReader();
    IndexReader ir2 = writer2.getReader();
    
    MultiReader mr = new MultiReader(ir1, ir2);
    IndexSearcher searcher = newSearcher(mr);
    FuzzyQuery fq = new FuzzyQuery(new Term("field", "z123456"), 1, 0, 2, false);
    TopDocs docs = searcher.search(fq, 2);
    assertEquals(5, docs.totalHits); // 5 docs, from the a and b's
    mr.close();
    ir1.close();
    ir2.close();
    writer.close();
    writer2.close();
    directory.close();
    directory2.close(); 
  }
  
  /** Test the TopTermsBoostOnlyBooleanQueryRewrite rewrite method. */
  public void testBoostOnlyRewrite() throws Exception {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory);
    addDoc("Lucene", writer);
    addDoc("Lucene", writer);
    addDoc("Lucenne", writer);

    IndexReader reader = writer.getReader();
    IndexSearcher searcher = newSearcher(reader);
    writer.close();
    
    FuzzyQuery query = new FuzzyQuery(new Term("field", "lucene"));
    query.setRewriteMethod(new MultiTermQuery.TopTermsBoostOnlyBooleanQueryRewrite(50));
    ScoreDoc[] hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    // normally, 'Lucenne' would be the first result as IDF will skew the score.
    assertEquals("Lucene", reader.document(hits[0].doc).get("field"));
    assertEquals("Lucene", reader.document(hits[1].doc).get("field"));
    assertEquals("Lucenne", reader.document(hits[2].doc).get("field"));
    reader.close();
    directory.close();
  }
  
  public void testGiga() throws Exception {

    MockAnalyzer analyzer = new MockAnalyzer(random());
    Directory index = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), index);

    addDoc("Lucene in Action", w);
    addDoc("Lucene for Dummies", w);

    //addDoc("Giga", w);
    addDoc("Giga byte", w);

    addDoc("ManagingGigabytesManagingGigabyte", w);
    addDoc("ManagingGigabytesManagingGigabytes", w);

    addDoc("The Art of Computer Science", w);
    addDoc("J. K. Rowling", w);
    addDoc("JK Rowling", w);
    addDoc("Joanne K Roling", w);
    addDoc("Bruce Willis", w);
    addDoc("Willis bruce", w);
    addDoc("Brute willis", w);
    addDoc("B. willis", w);
    IndexReader r = w.getReader();
    w.close();

    Query q = new FuzzyQuery(new Term("field", "giga"), 0);

    // 3. search
    IndexSearcher searcher = newSearcher(r);
    ScoreDoc[] hits = searcher.search(q, 10).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals("Giga byte", searcher.doc(hits[0].doc).get("field"));
    r.close();
    index.close();
  }
  
  public void testDistanceAsEditsSearching() throws Exception {
    Directory index = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), index);
    addDoc("foobar", w);
    addDoc("test", w);
    addDoc("working", w);
    IndexReader reader = w.getReader();
    IndexSearcher searcher = newSearcher(reader);
    w.close();
    
    FuzzyQuery q = new FuzzyQuery(new Term("field", "fouba"), 2);
    ScoreDoc[] hits = searcher.search(q, 10).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals("foobar", searcher.doc(hits[0].doc).get("field"));
    
    q = new FuzzyQuery(new Term("field", "foubara"), 2);
    hits = searcher.search(q, 10).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals("foobar", searcher.doc(hits[0].doc).get("field"));
    
    try {
      q = new FuzzyQuery(new Term("field", "t"), 3);
      fail();
    } catch (IllegalArgumentException expected) {
      // expected
    }
      
    reader.close();
    index.close();
  }
  
  private void addDoc(String text, RandomIndexWriter writer) throws IOException {
    Document doc = new Document();
    doc.add(newTextField("field", text, Field.Store.YES));
    writer.addDocument(doc);
  }
}
