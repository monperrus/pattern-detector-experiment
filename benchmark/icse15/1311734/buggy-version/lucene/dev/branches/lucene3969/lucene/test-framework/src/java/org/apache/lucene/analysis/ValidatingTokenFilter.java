package org.apache.lucene.analysis;

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
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.util.Attribute;

// nocommit better name...?

// nocommit BTSTC should just append this to the chain
// instead of checking itself:

/** A TokenFilter that checks consistency of the tokens (eg
 *  offsets are consistent with one another). */
public final class ValidatingTokenFilter extends TokenFilter {

  private int pos;

  // Maps position to the start/end offset:
  private final Map<Integer,Integer> posToStartOffset = new HashMap<Integer,Integer>();
  private final Map<Integer,Integer> posToEndOffset = new HashMap<Integer,Integer>();

  private final PositionIncrementAttribute posIncAtt = getAttrIfExists(PositionIncrementAttribute.class);
  private final PositionLengthAttribute posLenAtt = getAttrIfExists(PositionLengthAttribute.class);
  private final OffsetAttribute offsetAtt = getAttrIfExists(OffsetAttribute.class);
  private final CharTermAttribute termAtt = getAttrIfExists(CharTermAttribute.class);

  private final String name;

  // Returns null if the attr wasn't already added
  private <A extends Attribute> A getAttrIfExists(Class<A> att) {
    if (hasAttribute(att)) {
      return getAttribute(att);
    } else {
      return null;
    }
  }

  /** The name arg is used to identify this stage when
   *  throwing exceptions (useful if you have more than one
   *  instance in your chain). */
  public ValidatingTokenFilter(TokenStream in, String name) {
    super(in);
    this.name = name;
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (!input.incrementToken()) {
      return false;
    }

    if (posIncAtt != null && offsetAtt != null) {

      pos += posIncAtt.getPositionIncrement();
      if (pos == -1) {
        throw new IllegalStateException("first posInc must be > 0");
      }

      final int startOffset = offsetAtt.startOffset();
      final int endOffset = offsetAtt.endOffset();

      if (endOffset < startOffset) {
        throw new IllegalStateException(name + ": startOffset=" + startOffset + " is > endOffset=" + endOffset + " pos=" + pos + "; token=" + termAtt);
      }

      final int posLen = posLenAtt == null ? 1 : posLenAtt.getPositionLength();

      if (!posToStartOffset.containsKey(pos)) {
        // First time we've seen a token leaving from this position:
        posToStartOffset.put(pos, startOffset);
        //System.out.println("  + s " + pos + " -> " + startOffset);
      } else {
        // We've seen a token leaving from this position
        // before; verify the startOffset is the same:
        //System.out.println("  + vs " + pos + " -> " + startOffset);
        final int oldStartOffset = posToStartOffset.get(pos);
        if (oldStartOffset != startOffset) {
          throw new IllegalStateException(name + ": inconsistent startOffset at pos=" + pos + ": " + oldStartOffset + " vs " + startOffset + "; token=" + termAtt);
        }
      }

      final int endPos = pos + posLen;

      if (!posToEndOffset.containsKey(endPos)) {
        // First time we've seen a token arriving to this position:
        posToEndOffset.put(endPos, endOffset);
        //System.out.println("  + e " + endPos + " -> " + endOffset);
      } else {
        // We've seen a token arriving to this position
        // before; verify the endOffset is the same:
        //System.out.println("  + ve " + endPos + " -> " + endOffset);
        final int oldEndOffset = posToEndOffset.get(endPos);
        if (oldEndOffset != endOffset) {
          throw new IllegalStateException(name + ": inconsistent endOffset at pos=" + endPos + ": " + oldEndOffset + " vs " + endOffset + "; token=" + termAtt);
        }
      }
    }

    return true;
  }

  @Override
  public void end() throws IOException {
    super.end();

    // TODO: what else to validate

    // nocommit check that endOffset is >= max(endOffset)
    // we've seen
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    pos = -1;
    posToStartOffset.clear();
    posToEndOffset.clear();
  }
}
