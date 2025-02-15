  Merged /lucene/dev/trunk/solr/core:r1365865
  Merged /lucene/dev/trunk/solr:r1365865
package org.apache.lucene.analysis.ja;

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
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.JapanesePartOfSpeechStopFilter;
import org.apache.lucene.analysis.util.*;

/**
 * Factory for {@link org.apache.lucene.analysis.ja.JapanesePartOfSpeechStopFilter}.
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ja" class="solr.TextField"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.JapaneseTokenizerFactory"/&gt;
 *     &lt;filter class="solr.JapanesePartOfSpeechStopFilterFactory"
 *             tags="stopTags.txt" 
 *             enablePositionIncrements="true"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;
 * </pre>
 */
public class JapanesePartOfSpeechStopFilterFactory extends TokenFilterFactory implements ResourceLoaderAware  {
  private boolean enablePositionIncrements;
  private Set<String> stopTags;

  public void inform(ResourceLoader loader) {
    String stopTagFiles = args.get("tags");
    enablePositionIncrements = getBoolean("enablePositionIncrements", false);
    stopTags = null;
    try {
      CharArraySet cas = getWordSet(loader, stopTagFiles, false);
      if (cas != null) {
        stopTags = new HashSet<String>();
        for (Object element : cas) {
          char chars[] = (char[]) element;
          stopTags.add(new String(chars));
        }
      }
    } catch (IOException e) {
      throw new InitializationException("IOException thrown while loading tags", e);
    }
  }

  public TokenStream create(TokenStream stream) {
    // if stoptags is null, it means the file is empty
    return stopTags == null ? stream : new JapanesePartOfSpeechStopFilter(enablePositionIncrements, stream, stopTags);
  }
}
