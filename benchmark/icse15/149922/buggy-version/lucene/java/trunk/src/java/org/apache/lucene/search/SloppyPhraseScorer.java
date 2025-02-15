  - 1.3
  + 1.4
package org.apache.lucene.search;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache Lucene" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache Lucene", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import java.io.IOException;
import java.util.Vector;
import org.apache.lucene.util.*;
import org.apache.lucene.index.*;

final class SloppyPhraseScorer extends PhraseScorer {
  private int slop;

  SloppyPhraseScorer(TermPositions[] tps, Similarity similarity,
                     int slop, byte[] norms, float weight) throws IOException {
    super(tps, similarity, norms, weight);
    this.slop = slop;
  }

  protected final float phraseFreq() throws IOException {
    pq.clear();
    int end = 0;
    for (PhrasePositions pp = first; pp != null; pp = pp.next) {
      pp.firstPosition();
      if (pp.position > end)
	end = pp.position;
      pq.put(pp);				  // build pq from list
    }

    float freq = 0.0f;
    boolean done = false;
    do {
      PhrasePositions pp = (PhrasePositions)pq.pop();
      int start = pp.position;
      int next = ((PhrasePositions)pq.top()).position;
      for (int pos = start; pos <= next; pos = pp.position) {
	start = pos;				  // advance pp to min window
	if (!pp.nextPosition()) {
	  done = true;				  // ran out of a term -- done
	  break;
	}
      }

      int matchLength = end - start;
      if (matchLength <= slop)
	freq += getSimilarity().sloppyFreq(matchLength); // score match

      if (pp.position > end)
	end = pp.position;
      pq.put(pp);				  // restore pq
    } while (!done);
    
    return freq;
  }
}
