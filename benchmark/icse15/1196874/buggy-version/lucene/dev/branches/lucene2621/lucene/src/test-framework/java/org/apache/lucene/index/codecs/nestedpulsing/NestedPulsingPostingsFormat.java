package org.apache.lucene.index.codecs.nestedpulsing;

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
import java.util.Set;

import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.codecs.BlockTreeTermsReader;
import org.apache.lucene.index.codecs.BlockTreeTermsWriter;
import org.apache.lucene.index.codecs.FieldsConsumer;
import org.apache.lucene.index.codecs.FieldsProducer;
import org.apache.lucene.index.codecs.PostingsFormat;
import org.apache.lucene.index.codecs.PostingsReaderBase;
import org.apache.lucene.index.codecs.PostingsWriterBase;
import org.apache.lucene.index.codecs.lucene40.Lucene40PostingsReader;
import org.apache.lucene.index.codecs.lucene40.Lucene40PostingsWriter;
import org.apache.lucene.index.codecs.pulsing.PulsingPostingsReader;
import org.apache.lucene.index.codecs.pulsing.PulsingPostingsWriter;
import org.apache.lucene.store.Directory;

/**
 * Pulsing(1, Pulsing(2, Lucene40))
 * @lucene.experimental
 */
// TODO: if we create PulsingPostingsBaseFormat then we
// can simplify this? note: I don't like the *BaseFormat
// hierarchy, maybe we can clean that up...
public class NestedPulsingPostingsFormat extends PostingsFormat {
  public NestedPulsingPostingsFormat() {
    super("NestedPulsing");
  }
  
  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    PostingsWriterBase docsWriter = new Lucene40PostingsWriter(state);

    PostingsWriterBase pulsingWriterInner = new PulsingPostingsWriter(2, docsWriter);
    PostingsWriterBase pulsingWriter = new PulsingPostingsWriter(1, pulsingWriterInner);
    
    // Terms dict
    boolean success = false;
    try {
      FieldsConsumer ret = new BlockTreeTermsWriter(state, pulsingWriter, 
          BlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE, BlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE);
      success = true;
      return ret;
    } finally {
      if (!success) {
        pulsingWriter.close();
      }
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
    PostingsReaderBase docsReader = new Lucene40PostingsReader(state.dir, state.segmentInfo, state.context, state.formatId);
    PostingsReaderBase pulsingReaderInner = new PulsingPostingsReader(docsReader);
    PostingsReaderBase pulsingReader = new PulsingPostingsReader(pulsingReaderInner);
    boolean success = false;
    try {
      FieldsProducer ret = new BlockTreeTermsReader(
                                                    state.dir, state.fieldInfos, state.segmentInfo.name,
                                                    pulsingReader,
                                                    state.context,
                                                    state.formatId,
                                                    state.termsIndexDivisor);
      success = true;
      return ret;
    } finally {
      if (!success) {
        pulsingReader.close();
      }
    }
  }

  @Override
  public void files(Directory dir, SegmentInfo segmentInfo, int id, Set<String> files) throws IOException {
    Lucene40PostingsReader.files(dir, segmentInfo, id, files);
    BlockTreeTermsReader.files(dir, segmentInfo, id, files);
  }
}
