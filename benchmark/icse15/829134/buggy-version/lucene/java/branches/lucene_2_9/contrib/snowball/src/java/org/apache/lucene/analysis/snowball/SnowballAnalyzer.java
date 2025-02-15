package org.apache.lucene.analysis.snowball;

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

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.*;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

/** Filters {@link StandardTokenizer} with {@link StandardFilter}, {@link
 * LowerCaseFilter}, {@link StopFilter} and {@link SnowballFilter}.
 *
 * Available stemmers are listed in org.tartarus.snowball.ext.  The name of a
 * stemmer is the part of the class name before "Stemmer", e.g., the stemmer in
 * {@link org.tartarus.snowball.ext.EnglishStemmer} is named "English".
 */
public class SnowballAnalyzer extends Analyzer {
  private String name;
  private Set stopSet;

  /** Builds the named analyzer with no stop words. */
  public SnowballAnalyzer(String name) {
    this.name = name;
    setOverridesTokenStreamMethod(SnowballAnalyzer.class);
  }

  /** Builds the named analyzer with the given stop words. */
  public SnowballAnalyzer(String name, String[] stopWords) {
    this(name);
    stopSet = StopFilter.makeStopSet(stopWords);
  }

  /** Constructs a {@link StandardTokenizer} filtered by a {@link
      StandardFilter}, a {@link LowerCaseFilter}, a {@link StopFilter},
      and a {@link SnowballFilter} */
  public TokenStream tokenStream(String fieldName, Reader reader) {
    TokenStream result = new StandardTokenizer(reader);
    result = new StandardFilter(result);
    result = new LowerCaseFilter(result);
    if (stopSet != null)
      result = new StopFilter(result, stopSet);
    result = new SnowballFilter(result, name);
    return result;
  }
  
  private class SavedStreams {
    Tokenizer source;
    TokenStream result;
  };
  
  /** Returns a (possibly reused) {@link StandardTokenizer} filtered by a 
   * {@link StandardFilter}, a {@link LowerCaseFilter}, 
   * a {@link StopFilter}, and a {@link SnowballFilter} */
  public TokenStream reusableTokenStream(String fieldName, Reader reader)
      throws IOException {
    if (overridesTokenStreamMethod) {
      // LUCENE-1678: force fallback to tokenStream() if we
      // have been subclassed and that subclass overrides
      // tokenStream but not reusableTokenStream
      return tokenStream(fieldName, reader);
    }
    
    SavedStreams streams = (SavedStreams) getPreviousTokenStream();
    if (streams == null) {
      streams = new SavedStreams();
      streams.source = new StandardTokenizer(reader);
      streams.result = new StandardFilter(streams.source);
      streams.result = new LowerCaseFilter(streams.result);
      if (stopSet != null)
        streams.result = new StopFilter(streams.result, stopSet);
      streams.result = new SnowballFilter(streams.result, name);
      setPreviousTokenStream(streams);
    } else {
      streams.source.reset(reader);
    }
    return streams.result;
  }
}
