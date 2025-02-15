package org.apache.lucene.codecs.simpletext;

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

import java.io.IOException;

import org.apache.lucene.codecs.DocValuesConsumer;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;

/**
 * plain text doc values format.
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * <p>
 * the .dat file contains the data.
 *  for numbers this is a "fixed-width" file, for example a single byte range:
 *  <pre>
 *  field myField
 *    minvalue 0
 *    pattern 000
 *  005
 *  234
 *  123
 *  ...
 *  </pre>
 *  so a document's value (delta encoded from minvalue) can be retrieved by 
 *  seeking to startOffset + (1+pattern.length())*docid. The extra 1 is the newline.
 *  
 *  for bytes this is also a "fixed-width" file, for example:
 *  <pre>
 *  field myField
 *    maxlength 6
 *    pattern 0
 *  length 6
 *  foobar[space][space]
 *  length 3
 *  baz[space][space][space][space][space]
 *  ...
 *  </pre>
 *  so a doc's value can be retrieved by seeking to startOffset + (9+pattern.length+maxlength)*doc
 *  the extra 9 is 2 newlines, plus "length " itself.
 *  
 *  for sorted bytes this is a fixed-width file, for example:
 *  <pre>
 *  field myField
 *    numvalues 10
 *    maxLength 8
 *    pattern 0
 *    ordpattern 00
 *  length 6
 *  foobar[space][space]
 *  length 3
 *  baz[space][space][space][space][space]
 *  ...
 *  03
 *  06
 *  01
 *  10
 *  ...
 *  </pre>
 *  so the "ord section" begins at startOffset + (9+pattern.length+maxlength)*numValues.
 *  a document's ord can be retrieved by seeking to "ord section" + (1+ordpattern.length())*docid
 *  an ord's value can be retrieved by seeking to startOffset + (9+pattern.length+maxlength)*ord
 *   
 *  the reader can just scan this file when it opens, skipping over the data blocks
 *  and saving the offset/etc for each field. 
 *  @lucene.experimental
 */
public class SimpleTextDocValuesFormat extends DocValuesFormat {
  
  public SimpleTextDocValuesFormat() {
    super("SimpleText");
  }

  @Override
  public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    return new SimpleTextDocValuesWriter(state, "dat");
  }

  @Override
  public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
    return new SimpleTextDocValuesReader(state, "dat");
  }
}
