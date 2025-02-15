package org.apache.lucene.analysis.commongrams;

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
import java.util.Map;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.commongrams.CommonGramsFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.util.*;

/**
 * Constructs a {@link CommonGramsFilter}.
 * <pre class="prettyprint">
 * &lt;fieldType name="text_cmmngrms" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.CommonGramsFilterFactory" words="commongramsstopwords.txt" ignoreCase="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
public class CommonGramsFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {
  // TODO: shared base class for Stop/Keep/CommonGrams? 
  private CharArraySet commonWords;
  private final String commonWordFiles;
  private final String format;
  private final boolean ignoreCase;
  
  /** Creates a new CommonGramsFilterFactory */
  public CommonGramsFilterFactory(Map<String,String> args) {
    super(args);
    commonWordFiles = args.remove("words");
    format = args.remove("format");
    ignoreCase = getBoolean(args, "ignoreCase", false);
    if (!args.isEmpty()) {
      throw new IllegalArgumentException("Unknown parameters: " + args);
    }
  }

  @Override
  public void inform(ResourceLoader loader) throws IOException {
    if (commonWordFiles != null) {
      if ("snowball".equalsIgnoreCase(format)) {
        commonWords = getSnowballWordSet(loader, commonWordFiles, ignoreCase);
      } else {
        commonWords = getWordSet(loader, commonWordFiles, ignoreCase);
      }
    } else {
      commonWords = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
    }
  }

  public boolean isIgnoreCase() {
    return ignoreCase;
  }

  public CharArraySet getCommonWords() {
    return commonWords;
  }

  @Override
  public TokenFilter create(TokenStream input) {
    CommonGramsFilter commonGrams = new CommonGramsFilter(luceneMatchVersion, input, commonWords);
    return commonGrams;
  }
}
 
  
  
