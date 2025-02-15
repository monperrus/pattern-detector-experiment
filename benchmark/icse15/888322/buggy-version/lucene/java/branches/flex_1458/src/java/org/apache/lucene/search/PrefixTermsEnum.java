package org.apache.lucene.search;

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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermRef;

/**
 * Subclass of FilteredTermEnum for enumerating all terms that match the
 * specified prefix filter term.
 * <p>Term enumerations are always ordered by
 * {@link #getTermComparator}.  Each term in the enumeration is
 * greater than all that precede it.</p>
 */
public class PrefixTermsEnum extends FilteredTermsEnum {

  private final Term prefix;
  private final TermRef prefixRef;

  public PrefixTermsEnum(IndexReader reader, Term prefix) throws IOException {
    super(reader, prefix.field());
    this.prefix = prefix;
    setInitialSeekTerm(prefixRef = new TermRef(prefix.text()));
  }

  protected Term getPrefixTerm() {
    return prefix;
  }

  @Override
  protected AcceptStatus accept(TermRef term) {
    if (term.startsWith(prefixRef)) {
      return AcceptStatus.YES;
    } else {
      return AcceptStatus.END;
    }
  }
}
