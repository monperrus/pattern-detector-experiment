  Merged /lucene/dev/trunk/lucene/src/test/org/apache/lucene/index/TestBackwardsCompatibility.java:r999842
  Merged /lucene/dev/trunk/lucene/src/test/org/apache/lucene/index:r999842
  Merged /lucene/dev/trunk/lucene/src/test/org/apache/lucene/util/TestAttributeSource.java:r999842
  Merged /lucene/dev/trunk/lucene/src/test/org/apache/lucene/document/TestNumberTools.java:r999842
  Merged /lucene/dev/trunk/lucene/src/test/org/apache/lucene/document/TestDateTools.java:r999842
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

import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.UnicodeUtil;

final class TermVectorsTermsWriterPerField extends TermsHashConsumerPerField {

  final TermVectorsTermsWriterPerThread perThread;
  final TermsHashPerField termsHashPerField;
  final TermVectorsTermsWriter termsWriter;
  final FieldInfo fieldInfo;
  final DocumentsWriter.DocState docState;
  final FieldInvertState fieldState;

  boolean doVectors;
  boolean doVectorPositions;
  boolean doVectorOffsets;

  int maxNumPostings;
  OffsetAttribute offsetAttribute = null;
  
  public TermVectorsTermsWriterPerField(TermsHashPerField termsHashPerField, TermVectorsTermsWriterPerThread perThread, FieldInfo fieldInfo) {
    this.termsHashPerField = termsHashPerField;
    this.perThread = perThread;
    this.termsWriter = perThread.termsWriter;
    this.fieldInfo = fieldInfo;
    docState = termsHashPerField.docState;
    fieldState = termsHashPerField.fieldState;
  }

  @Override
  int getStreamCount() {
    return 2;
  }

  @Override
  boolean start(Fieldable[] fields, int count) {
    doVectors = false;
    doVectorPositions = false;
    doVectorOffsets = false;

    for(int i=0;i<count;i++) {
      Fieldable field = fields[i];
      if (field.isIndexed() && field.isTermVectorStored()) {
        doVectors = true;
        doVectorPositions |= field.isStorePositionWithTermVector();
        doVectorOffsets |= field.isStoreOffsetWithTermVector();
      }
    }

    if (doVectors) {
      if (perThread.doc == null) {
        perThread.doc = termsWriter.getPerDoc();
        perThread.doc.docID = docState.docID;
        assert perThread.doc.numVectorFields == 0;
        assert 0 == perThread.doc.perDocTvf.length();
        assert 0 == perThread.doc.perDocTvf.getFilePointer();
      } else {
        assert perThread.doc.docID == docState.docID;

        if (termsHashPerField.numPostings != 0)
          // Only necessary if previous doc hit a
          // non-aborting exception while writing vectors in
          // this field:
          termsHashPerField.reset();
      }
    }

    // TODO: only if needed for performance
    //perThread.postingsCount = 0;

    return doVectors;
  }     

  public void abort() {}

  /** Called once per field per document if term vectors
   *  are enabled, to write the vectors to
   *  RAMOutputStream, which is then quickly flushed to
   *  * the real term vectors files in the Directory. */
  @Override
  void finish() throws IOException {

    assert docState.testPoint("TermVectorsTermsWriterPerField.finish start");

    final int numPostings = termsHashPerField.numPostings;

    assert numPostings >= 0;

    if (!doVectors || numPostings == 0)
      return;

    if (numPostings > maxNumPostings)
      maxNumPostings = numPostings;

    final IndexOutput tvf = perThread.doc.perDocTvf;

    // This is called once, after inverting all occurrences
    // of a given field in the doc.  At this point we flush
    // our hash into the DocWriter.

    assert fieldInfo.storeTermVector;
    assert perThread.vectorFieldsInOrder(fieldInfo);

    perThread.doc.addField(termsHashPerField.fieldInfo.number);
    TermVectorsPostingsArray postings = (TermVectorsPostingsArray) termsHashPerField.postingsArray;

    final int[] termIDs = termsHashPerField.sortPostings();

    tvf.writeVInt(numPostings);
    byte bits = 0x0;
    if (doVectorPositions)
      bits |= TermVectorsReader.STORE_POSITIONS_WITH_TERMVECTOR;
    if (doVectorOffsets) 
      bits |= TermVectorsReader.STORE_OFFSET_WITH_TERMVECTOR;
    tvf.writeByte(bits);

    int encoderUpto = 0;
    int lastTermBytesCount = 0;

    final ByteSliceReader reader = perThread.vectorSliceReader;
    final char[][] charBuffers = perThread.termsHashPerThread.charPool.buffers;
    for(int j=0;j<numPostings;j++) {
      final int termID = termIDs[j];
      final int freq = postings.freqs[termID];
          
      final char[] text2 = charBuffers[postings.textStarts[termID] >> DocumentsWriter.CHAR_BLOCK_SHIFT];
      final int start2 = postings.textStarts[termID] & DocumentsWriter.CHAR_BLOCK_MASK;

      // We swap between two encoders to save copying
      // last Term's byte array
      final UnicodeUtil.UTF8Result utf8Result = perThread.utf8Results[encoderUpto];

      // TODO: we could do this incrementally
      UnicodeUtil.UTF16toUTF8(text2, start2, utf8Result);
      final int termBytesCount = utf8Result.length;

      // TODO: UTF16toUTF8 could tell us this prefix
      // Compute common prefix between last term and
      // this term
      int prefix = 0;
      if (j > 0) {
        final byte[] lastTermBytes = perThread.utf8Results[1-encoderUpto].result;
        final byte[] termBytes = perThread.utf8Results[encoderUpto].result;
        while(prefix < lastTermBytesCount && prefix < termBytesCount) {
          if (lastTermBytes[prefix] != termBytes[prefix])
            break;
          prefix++;
        }
      }
      encoderUpto = 1-encoderUpto;
      lastTermBytesCount = termBytesCount;

      final int suffix = termBytesCount - prefix;
      tvf.writeVInt(prefix);
      tvf.writeVInt(suffix);
      tvf.writeBytes(utf8Result.result, prefix, suffix);
      tvf.writeVInt(freq);

      if (doVectorPositions) {
        termsHashPerField.initReader(reader, termID, 0);
        reader.writeTo(tvf);
      }

      if (doVectorOffsets) {
        termsHashPerField.initReader(reader, termID, 1);
        reader.writeTo(tvf);
      }
    }

    termsHashPerField.reset();
    perThread.termsHashPerThread.reset(false);
  }

  void shrinkHash() {
    termsHashPerField.shrinkHash(maxNumPostings);
    maxNumPostings = 0;
  }
  
  @Override
  void start(Fieldable f) {
    if (doVectorOffsets) {
      offsetAttribute = fieldState.attributeSource.addAttribute(OffsetAttribute.class);
    } else {
      offsetAttribute = null;
    }
  }

  @Override
  void newTerm(final int termID) {

    assert docState.testPoint("TermVectorsTermsWriterPerField.newTerm start");

    TermVectorsPostingsArray postings = (TermVectorsPostingsArray) termsHashPerField.postingsArray;

    postings.freqs[termID] = 1;

    if (doVectorOffsets) {
      int startOffset = fieldState.offset + offsetAttribute.startOffset();
      int endOffset = fieldState.offset + offsetAttribute.endOffset();
      
      termsHashPerField.writeVInt(1, startOffset);
      termsHashPerField.writeVInt(1, endOffset - startOffset);
      postings.lastOffsets[termID] = endOffset;
    }

    if (doVectorPositions) {
      termsHashPerField.writeVInt(0, fieldState.position);
      postings.lastPositions[termID] = fieldState.position;
    }
  }

  @Override
  void addTerm(final int termID) {

    assert docState.testPoint("TermVectorsTermsWriterPerField.addTerm start");

    TermVectorsPostingsArray postings = (TermVectorsPostingsArray) termsHashPerField.postingsArray;
    
    postings.freqs[termID]++;

    if (doVectorOffsets) {
      int startOffset = fieldState.offset + offsetAttribute.startOffset();
      int endOffset = fieldState.offset + offsetAttribute.endOffset();
      
      termsHashPerField.writeVInt(1, startOffset - postings.lastOffsets[termID]);
      termsHashPerField.writeVInt(1, endOffset - startOffset);
      postings.lastOffsets[termID] = endOffset;
    }

    if (doVectorPositions) {
      termsHashPerField.writeVInt(0, fieldState.position - postings.lastPositions[termID]);
      postings.lastPositions[termID] = fieldState.position;
    }
  }

  @Override
  void skippingLongTerm() {}

  @Override
  ParallelPostingsArray createPostingsArray(int size) {
    return new TermVectorsPostingsArray(size);
  }

  static final class TermVectorsPostingsArray extends ParallelPostingsArray {
    public TermVectorsPostingsArray(int size) {
      super(size);
      freqs = new int[size];
      lastOffsets = new int[size];
      lastPositions = new int[size];
    }

    int[] freqs;                                       // How many times this term occurred in the current doc
    int[] lastOffsets;                                 // Last offset we saw
    int[] lastPositions;                               // Last position where this term occurred
    
    ParallelPostingsArray newInstance(int size) {
      return new TermVectorsPostingsArray(size);
    }

    @Override
    void copyTo(ParallelPostingsArray toArray, int numToCopy) {
      assert toArray instanceof TermVectorsPostingsArray;
      TermVectorsPostingsArray to = (TermVectorsPostingsArray) toArray;

      super.copyTo(toArray, numToCopy);

      System.arraycopy(freqs, 0, to.freqs, 0, size);
      System.arraycopy(lastOffsets, 0, to.lastOffsets, 0, size);
      System.arraycopy(lastPositions, 0, to.lastPositions, 0, size);
    }

    @Override
    int bytesPerPosting() {
      return super.bytesPerPosting() + 3 * DocumentsWriter.INT_NUM_BYTE;
    }
  }
}
