package org.apache.lucene.search.grouping.term;

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

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.grouping.AbstractFirstPassGroupingCollector;
import org.apache.lucene.util.BytesRef;

/**
 * Concrete implementation of {@link org.apache.lucene.search.grouping.AbstractFirstPassGroupingCollector} that groups based on
 * field values and more specifically uses {@link org.apache.lucene.search.FieldCache.DocTermsIndex}
 * to collect groups.
 *
 * @lucene.experimental
 */
public class TermFirstPassGroupingCollector extends AbstractFirstPassGroupingCollector<BytesRef> {

  private final BytesRef scratchBytesRef = new BytesRef();
  private SortedDocValues index;

  private String groupField;

  /**
   * Create the first pass collector.
   *
   *  @param groupField The field used to group
   *    documents. This field must be single-valued and
   *    indexed (FieldCache is used to access its value
   *    per-document).
   *  @param groupSort The {@link Sort} used to sort the
   *    groups.  The top sorted document within each group
   *    according to groupSort, determines how that group
   *    sorts against other groups.  This must be non-null,
   *    ie, if you want to groupSort by relevance use
   *    Sort.RELEVANCE.
   *  @param topNGroups How many top groups to keep.
   *  @throws IOException When I/O related errors occur
   */
  public TermFirstPassGroupingCollector(String groupField, Sort groupSort, int topNGroups) throws IOException {
    super(groupSort, topNGroups);
    this.groupField = groupField;
  }

  @Override
  protected BytesRef getDocGroupValue(int doc) {
    final int ord = index.getOrd(doc);
    if (ord == -1) {
      return null;
    } else {
      index.lookupOrd(ord, scratchBytesRef);
      return scratchBytesRef;
    }
  }

  @Override
  protected BytesRef copyDocGroupValue(BytesRef groupValue, BytesRef reuse) {
    if (groupValue == null) {
      return null;
    } else if (reuse != null) {
      reuse.copyBytes(groupValue);
      return reuse;
    } else {
      return BytesRef.deepCopyOf(groupValue);
    }
  }

  @Override
  public void setNextReader(AtomicReaderContext readerContext) throws IOException {
    super.setNextReader(readerContext);
    index = FieldCache.DEFAULT.getTermsIndex(readerContext.reader(), groupField);
  }
}
