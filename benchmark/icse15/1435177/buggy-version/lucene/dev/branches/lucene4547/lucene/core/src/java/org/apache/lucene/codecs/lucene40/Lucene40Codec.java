  + native
package org.apache.lucene.codecs.lucene40;

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

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FieldInfosFormat;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.LiveDocsFormat;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.SegmentInfoFormat;
import org.apache.lucene.codecs.SimpleDocValuesFormat;
import org.apache.lucene.codecs.SimpleNormsFormat;
import org.apache.lucene.codecs.StoredFieldsFormat;
import org.apache.lucene.codecs.TermVectorsFormat;
import org.apache.lucene.codecs.lucene41.Lucene41SimpleNormsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;

/**
 * Implements the Lucene 4.0 index format, with configurable per-field postings formats.
 * <p>
 * If you want to reuse functionality of this codec in another codec, extend
 * {@link FilterCodec}.
 *
 * @see org.apache.lucene.codecs.lucene40 package documentation for file format details.
 * @deprecated Only for reading old 4.0 segments
 */
// NOTE: if we make largish changes in a minor release, easier to just make Lucene42Codec or whatever
// if they are backwards compatible or smallish we can probably do the backwards in the postingsreader
// (it writes a minor version, etc).
@Deprecated
public final class Lucene40Codec extends Codec {
  private final StoredFieldsFormat fieldsFormat = new Lucene40StoredFieldsFormat();
  private final TermVectorsFormat vectorsFormat = new Lucene40TermVectorsFormat();
  private final FieldInfosFormat fieldInfosFormat = new Lucene40FieldInfosFormat();
  private final SegmentInfoFormat infosFormat = new Lucene40SegmentInfoFormat();
  private final LiveDocsFormat liveDocsFormat = new Lucene40LiveDocsFormat();
  
  private final PostingsFormat postingsFormat = new PerFieldPostingsFormat() {
    @Override
    public PostingsFormat getPostingsFormatForField(String field) {
      return Lucene40Codec.this.getPostingsFormatForField(field);
    }
  };

  /** Sole constructor. */
  public Lucene40Codec() {
    super("Lucene40");
  }
  
  @Override
  public final StoredFieldsFormat storedFieldsFormat() {
    return fieldsFormat;
  }
  
  @Override
  public final TermVectorsFormat termVectorsFormat() {
    return vectorsFormat;
  }

  @Override
  public final PostingsFormat postingsFormat() {
    return postingsFormat;
  }
  
  @Override
  public final FieldInfosFormat fieldInfosFormat() {
    return fieldInfosFormat;
  }
  
  @Override
  public final SegmentInfoFormat segmentInfoFormat() {
    return infosFormat;
  }
  
  // nocommit need a read-only Lucene40SimpleDVFormat
  private final SimpleDocValuesFormat defaultDVFormat = SimpleDocValuesFormat.forName("Disk");

  @Override
  public SimpleDocValuesFormat simpleDocValuesFormat() {
    // nocommit
    return defaultDVFormat;
  }

  // nocommit need a read-only Lucene40SimpleNormsFormat:
  private final SimpleNormsFormat simpleNormsFormat = new Lucene41SimpleNormsFormat();

  @Override
  public SimpleNormsFormat simpleNormsFormat() {
    return simpleNormsFormat;
  }

  @Override
  public final LiveDocsFormat liveDocsFormat() {
    return liveDocsFormat;
  }

  /** Returns the postings format that should be used for writing 
   *  new segments of <code>field</code>.
   *  
   *  The default implementation always returns "Lucene40"
   */
  public PostingsFormat getPostingsFormatForField(String field) {
    return defaultFormat;
  }
  
  private final PostingsFormat defaultFormat = PostingsFormat.forName("Lucene40");
}
