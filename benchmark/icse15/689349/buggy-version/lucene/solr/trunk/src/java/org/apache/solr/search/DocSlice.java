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

package org.apache.solr.search;

/**
 * <code>DocSlice</code> implements DocList as an array of docids and optional scores.
 *
 * @version $Id$
 * @since solr 0.9
 */
public class DocSlice extends DocSetBase implements DocList {
  final int offset;    // starting position of the docs (zero based)
  final int len;       // number of positions used in arrays
  final int[] docs;    // a slice of documents (docs 0-100 of the query)

  final float[] scores;  // optional score list
  final int matches;
  final float maxScore;

  /**
   * Primary constructor for a DocSlice instance.
   *
   * @param offset  starting offset for this range of docs
   * @param len     length of results
   * @param docs    array of docids starting at position 0
   * @param scores  array of scores that corresponds to docs, may be null
   * @param matches total number of matches for the query
   */
  public DocSlice(int offset, int len, int[] docs, float[] scores, int matches, float maxScore) {
    this.offset=offset;
    this.len=len;
    this.docs=docs;
    this.scores=scores;
    this.matches=matches;
    this.maxScore=maxScore;
  }

  public DocList subset(int offset, int len) {
    if (this.offset == offset && this.len==len) return this;

    // if we didn't store enough (and there was more to store)
    // then we can't take a subset.
    int requestedEnd = offset + len;
    if (requestedEnd > docs.length && this.matches > docs.length) return null;
    int realEndDoc = Math.min(requestedEnd, docs.length);
    int realLen = Math.max(realEndDoc-offset,0);
    if (this.offset == offset && this.len == realLen) return this;
    return new DocSlice(offset, realLen, docs, scores, matches, maxScore);
  }

  public boolean hasScores() {
    return scores!=null;
  }

  public float maxScore() {
    return maxScore;
  }


  public int offset()  { return offset; }
  public int size()    { return len; }
  public int matches() { return matches; }


  public long memSize() {
    return (docs.length<<2)
            + (scores==null ? 0 : (scores.length<<2))
            + 24;
  }


  public boolean exists(int doc) {
    for (int i: docs) {
      if (i==doc) return true;
    }
    return false;
  }

  // Hmmm, maybe I could have reused the scorer interface here...
  // except that it carries Similarity baggage...
  public DocIterator iterator() {
    return new DocIterator() {
      int pos=offset;
      final int end=offset+len;
      public boolean hasNext() {
        return pos < end;
      }

      public Integer next() {
        return nextDoc();
      }

      public void remove() {
      }

      public int nextDoc() {
        return docs[pos++];
      }

      public float score() {
        return scores[pos-1];
      }
    };
  }
}
