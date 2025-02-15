  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1297621-1298076
  Merged /lucene/dev/trunk/solr:r1297621-1298076
package org.apache.lucene.search.suggest;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;

import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.spell.TermFreqIterator;
import org.apache.lucene.util.BytesRefIterator;
import org.apache.lucene.util.PriorityQueue;

/**
 * Simple Lookup interface for {@link CharSequence} suggestions.
 * @lucene.experimental
 */
public abstract class Lookup {
  /**
   * Result of a lookup.
   */
  public static final class LookupResult implements Comparable<LookupResult> {
    public final CharSequence key;
    public final long value;
    
    public LookupResult(CharSequence key, long value) {
      this.key = key;
      this.value = value;
    }
    
    @Override
    public String toString() {
      return key + "/" + value;
    }

    /** Compare alphabetically. */
    public int compareTo(LookupResult o) {
      return CHARSEQUENCE_COMPARATOR.compare(key, o.key);
    }
  }
  
  public static final Comparator<CharSequence> CHARSEQUENCE_COMPARATOR = new CharSequenceComparator();
  
  private static class CharSequenceComparator implements Comparator<CharSequence> {

    @Override
    public int compare(CharSequence o1, CharSequence o2) {
      final int l1 = o1.length();
      final int l2 = o2.length();
      
      final int aStop = Math.min(l1, l2);
      for (int i = 0; i < aStop; i++) {
        int diff = o1.charAt(i) - o2.charAt(i);
        if (diff != 0) {
          return diff;
        }
      }
      // One is a prefix of the other, or, they are equal:
      return l1 - l2;
    }
    
  }
  
  public static final class LookupPriorityQueue extends PriorityQueue<LookupResult> {
    
    public LookupPriorityQueue(int size) {
      super(size);
    }

    @Override
    protected boolean lessThan(LookupResult a, LookupResult b) {
      return a.value < b.value;
    }
    
    public LookupResult[] getResults() {
      int size = size();
      LookupResult[] res = new LookupResult[size];
      for (int i = size - 1; i >= 0; i--) {
        res[i] = pop();
      }
      return res;
    }
  }
  
  /** Build lookup from a dictionary. Some implementations may require sorted
   * or unsorted keys from the dictionary's iterator - use
   * {@link SortedTermFreqIteratorWrapper} or
   * {@link UnsortedTermFreqIteratorWrapper} in such case.
   */
  public void build(Dictionary dict) throws IOException {
    BytesRefIterator it = dict.getWordsIterator();
    TermFreqIterator tfit;
    if (it instanceof TermFreqIterator) {
      tfit = (TermFreqIterator)it;
    } else {
      tfit = new TermFreqIterator.TermFreqIteratorWrapper(it);
    }
    build(tfit);
  }
  
  /**
   * Builds up a new internal {@link Lookup} representation based on the given {@link TermFreqIterator}.
   * The implementation might re-sort the data internally.
   */
  public abstract void build(TermFreqIterator tfit) throws IOException;
  
  /**
   * Look up a key and return possible completion for this key.
   * @param key lookup key. Depending on the implementation this may be
   * a prefix, misspelling, or even infix.
   * @param onlyMorePopular return only more popular results
   * @param num maximum number of results to return
   * @return a list of possible completions, with their relative weight (e.g. popularity)
   */
  public abstract List<LookupResult> lookup(CharSequence key, boolean onlyMorePopular, int num);

  
  /**
   * Persist the constructed lookup data to a directory. Optional operation.
   * @param output {@link OutputStream} to write the data to.
   * @return true if successful, false if unsuccessful or not supported.
   * @throws IOException when fatal IO error occurs.
   */
  public abstract boolean store(OutputStream output) throws IOException;

  /**
   * Discard current lookup data and load it from a previously saved copy.
   * Optional operation.
   * @param input the {@link InputStream} to load the lookup data.
   * @return true if completed successfully, false if unsuccessful or not supported.
   * @throws IOException when fatal IO error occurs.
   */
  public abstract boolean load(InputStream input) throws IOException;
  
  /**
   * Persist the constructed lookup data to a directory. Optional operation.
   * @param storeDir directory where data can be stored.
   * @return true if successful, false if unsuccessful or not supported.
   * @throws IOException when fatal IO error occurs.
   */
  public abstract boolean store(File storeDir) throws IOException;

  /**
   * Discard current lookup data and load it from a previously saved copy.
   * Optional operation.
   * @param storeDir directory where lookup data was stored.
   * @return true if completed successfully, false if unsuccessful or not supported.
   * @throws IOException when fatal IO error occurs.
   */
  public abstract boolean load(File storeDir) throws IOException;
  
}
