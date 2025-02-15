  + native
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

package org.apache.solr.analysis;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.util.CharFilterFactory;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.analysis.util.TokenizerFactory;

import java.io.IOException;
import java.io.Reader;

/**
 *
 */

//
// An analyzer that uses a tokenizer and a list of token filters to
// create a TokenStream.
//
public final class TokenizerChain extends SolrAnalyzer {
  final private CharFilterFactory[] charFilters;
  final private TokenizerFactory tokenizer;
  final private TokenFilterFactory[] filters;

  public TokenizerChain(TokenizerFactory tokenizer, TokenFilterFactory[] filters) {
    this(null,tokenizer,filters);
  }

  public TokenizerChain(CharFilterFactory[] charFilters, TokenizerFactory tokenizer, TokenFilterFactory[] filters) {
    this.charFilters = charFilters;
    this.tokenizer = tokenizer;
    this.filters = filters;
  }

  public CharFilterFactory[] getCharFilterFactories() { return charFilters; }
  public TokenizerFactory getTokenizerFactory() { return tokenizer; }
  public TokenFilterFactory[] getTokenFilterFactories() { return filters; }

  class SolrTokenStreamComponents extends TokenStreamComponents {
    public SolrTokenStreamComponents(final Tokenizer source, final TokenStream result) {
      super(source, result);
    }

    // TODO: what is going on here?
    @Override
    protected void reset(Reader reader) throws IOException {
      // the tokenizers are currently reset by the indexing process, so only
      // the tokenizer needs to be reset.
      Reader r = initReader(null, reader);
      super.reset(r);
    }
  }
  
  
  @Override
  public Reader initReader(String fieldName, Reader reader) {
    if (charFilters != null && charFilters.length > 0) {
      CharStream cs = CharReader.get( reader );
      for (CharFilterFactory charFilter : charFilters) {
        cs = charFilter.create(cs);
      }
      reader = cs;
    }
    return reader;
  }

  @Override
  protected TokenStreamComponents createComponents(String fieldName, Reader aReader) {
    Tokenizer tk = tokenizer.create( initReader(fieldName, aReader) );
    TokenStream ts = tk;
    for (TokenFilterFactory filter : filters) {
      ts = filter.create(ts);
    }
    return new SolrTokenStreamComponents(tk, ts);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("TokenizerChain(");
    for (CharFilterFactory filter: charFilters) {
      sb.append(filter);
      sb.append(", ");
    }
    sb.append(tokenizer);
    for (TokenFilterFactory filter: filters) {
      sb.append(", ");
      sb.append(filter);
    }
    sb.append(')');
    return sb.toString();
  }

}
