package org.apache.lucene.search;

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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.CachingCollector;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.LuceneTestCase;

public class TestCachingCollector extends LuceneTestCase {

  private static final double ONE_BYTE = 1.0 / (1024 * 1024); // 1 byte out of MB
  
  private static class MockScorer extends Scorer {
    
    private MockScorer() {
      super((Weight) null);
    }
    
    @Override
    public float score() throws IOException { return 0; }

    @Override
    public int docID() { return 0; }

    @Override
    public int nextDoc() throws IOException { return 0; }

    @Override
    public int advance(int target) throws IOException { return 0; }
    
  }
  
  private static class NoOpCollector extends Collector {

    private final boolean acceptDocsOutOfOrder;
    
    public NoOpCollector(boolean acceptDocsOutOfOrder) {
      this.acceptDocsOutOfOrder = acceptDocsOutOfOrder;
    }
    
    @Override
    public void setScorer(Scorer scorer) throws IOException {}

    @Override
    public void collect(int doc) throws IOException {}

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {}

    @Override
    public boolean acceptsDocsOutOfOrder() {
      return acceptDocsOutOfOrder;
    }
    
  }

  public void testBasic() throws Exception {
    CachingCollector cc = new CachingCollector(new NoOpCollector(false), true, 1);
    cc.setScorer(new MockScorer());
    
    // collect 1000 docs
    for (int i = 0; i < 1000; i++) {
      cc.collect(i);
    }
    
    // now replay them
    cc.replay(new Collector() {
      int prevDocID = -1;
      
      @Override
      public void setScorer(Scorer scorer) throws IOException {}
      
      @Override
      public void setNextReader(IndexReader reader, int docBase) throws IOException {}
      
      @Override
      public void collect(int doc) throws IOException {
        assertEquals(prevDocID + 1, doc);
        prevDocID = doc;
      }
      
      @Override
      public boolean acceptsDocsOutOfOrder() {
        return false;
      }
    });
  }
  
  public void testIllegalStateOnReplay() throws Exception {
    CachingCollector cc = new CachingCollector(new NoOpCollector(false), true, 50 * ONE_BYTE);
    cc.setScorer(new MockScorer());
    
    // collect 130 docs, this should be enough for triggering cache abort.
    for (int i = 0; i < 130; i++) {
      cc.collect(i);
    }
    
    assertFalse("CachingCollector should not be cached due to low memory limit", cc.isCached());
    
    try {
      cc.replay(new NoOpCollector(false));
      fail("replay should fail if CachingCollector is not cached");
    } catch (IllegalStateException e) {
      // expected
    }
  }
  
  public void testIllegalCollectorOnReplay() throws Exception {
    // tests that the Collector passed to replay() has an out-of-order mode that
    // is valid with the Collector passed to the ctor
    
    // 'src' Collector does not support out-of-order
    CachingCollector cc = new CachingCollector(new NoOpCollector(false), true, 50 * ONE_BYTE);
    cc.setScorer(new MockScorer());
    for (int i = 0; i < 10; i++) cc.collect(i);
    cc.replay(new NoOpCollector(true)); // this call should not fail
    cc.replay(new NoOpCollector(false)); // this call should not fail

    // 'src' Collector supports out-of-order
    cc = new CachingCollector(new NoOpCollector(true), true, 50 * ONE_BYTE);
    cc.setScorer(new MockScorer());
    for (int i = 0; i < 10; i++) cc.collect(i);
    cc.replay(new NoOpCollector(true)); // this call should not fail
    try {
      cc.replay(new NoOpCollector(false)); // this call should fail
      fail("should have failed if an in-order Collector was given to replay(), " +
      		"while CachingCollector was initialized with out-of-order collection");
    } catch (IllegalArgumentException e) {
      // ok
    }
  }
  
  public void testCachedArraysAllocation() throws Exception {
    // tests the cached arrays allocation -- if the 'nextLength' was too high,
    // caching would terminate even if a smaller length would suffice.
    
    // set RAM limit enough for 150 docs + random(10000)
    int numDocs = random.nextInt(10000) + 150;
    CachingCollector cc = new CachingCollector(new NoOpCollector(false), true, 8 * ONE_BYTE * numDocs);
    cc.setScorer(new MockScorer());
    for (int i = 0; i < numDocs; i++) cc.collect(i);
    assertTrue(cc.isCached());
    
    // The 151's document should terminate caching
    cc.collect(numDocs);
    assertFalse(cc.isCached());
  }
  
}
