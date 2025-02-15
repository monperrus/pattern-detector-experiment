  - 1.22
  + 1.23
package org.apache.lucene.index;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import org.apache.lucene.util.BitVector;

final class SegmentMergeInfo {
  Term term;
  int base;
  TermEnum termEnum;
  IndexReader reader;
  TermPositions postings;
  int[] docMap = null;				  // maps around deleted docs

  SegmentMergeInfo(int b, TermEnum te, IndexReader r)
    throws IOException {
    base = b;
    reader = r;
    termEnum = te;
    term = te.term();
    postings = reader.termPositions();

    // build array which maps document numbers around deletions 
    if (reader.hasDeletions()) {
      int maxDoc = reader.maxDoc();
      docMap = new int[maxDoc];
      int j = 0;
      for (int i = 0; i < maxDoc; i++) {
        if (reader.isDeleted(i))
          docMap[i] = -1;
        else
          docMap[i] = j++;
      }
    }
  }

  final boolean next() throws IOException {
    if (termEnum.next()) {
      term = termEnum.term();
      return true;
    } else {
      term = null;
      return false;
    }
  }

  final void close() throws IOException {
    termEnum.close();
    postings.close();
  }
}

