package org.apache.lucene.analysis.it;

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
import java.io.Reader;
import java.util.Arrays;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.fr.ElisionFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordMarkerFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.StopwordAnalyzerBase;
import org.apache.lucene.analysis.util.WordlistLoader;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.Version;
import org.tartarus.snowball.ext.ItalianStemmer;

/**
 * {@link Analyzer} for Italian.
 * <p>
 * <a name="version"/>
 * <p>You must specify the required {@link Version}
 * compatibility when creating ItalianAnalyzer:
 * <ul>
 *   <li> As of 3.6, ItalianLightStemFilter is used for less aggressive stemming.
 *   <li> As of 3.2, ElisionFilter with a set of Italian 
 *        contractions is used by default.
 * </ul>
 */
public final class ItalianAnalyzer extends StopwordAnalyzerBase {
  private final Set<?> stemExclusionSet;
  
  /** File containing default Italian stopwords. */
  public final static String DEFAULT_STOPWORD_FILE = "italian_stop.txt";
  
  private static final CharArraySet DEFAULT_ARTICLES = CharArraySet.unmodifiableSet(
      new CharArraySet(Version.LUCENE_CURRENT, 
          Arrays.asList(
          "c", "l", "all", "dall", "dell", "nell", "sull", "coll", "pell", 
          "gl", "agl", "dagl", "degl", "negl", "sugl", "un", "m", "t", "s", "v", "d"
          ), true));

  /**
   * Returns an unmodifiable instance of the default stop words set.
   * @return default stop words set.
   */
  public static Set<?> getDefaultStopSet(){
    return DefaultSetHolder.DEFAULT_STOP_SET;
  }
  
  /**
   * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class 
   * accesses the static final set the first time.;
   */
  private static class DefaultSetHolder {
    static final Set<?> DEFAULT_STOP_SET;

    static {
      try {
        DEFAULT_STOP_SET = WordlistLoader.getSnowballWordSet(IOUtils.getDecodingReader(SnowballFilter.class, 
            DEFAULT_STOPWORD_FILE, IOUtils.CHARSET_UTF_8), Version.LUCENE_CURRENT);
      } catch (IOException ex) {
        // default set should always be present as it is part of the
        // distribution (JAR)
        throw new RuntimeException("Unable to load default stopword set");
      }
    }
  }

  /**
   * Builds an analyzer with the default stop words: {@link #DEFAULT_STOPWORD_FILE}.
   */
  public ItalianAnalyzer(Version matchVersion) {
    this(matchVersion, DefaultSetHolder.DEFAULT_STOP_SET);
  }
  
  /**
   * Builds an analyzer with the given stop words.
   * 
   * @param matchVersion lucene compatibility version
   * @param stopwords a stopword set
   */
  public ItalianAnalyzer(Version matchVersion, Set<?> stopwords) {
    this(matchVersion, stopwords, CharArraySet.EMPTY_SET);
  }

  /**
   * Builds an analyzer with the given stop words. If a non-empty stem exclusion set is
   * provided this analyzer will add a {@link KeywordMarkerFilter} before
   * stemming.
   * 
   * @param matchVersion lucene compatibility version
   * @param stopwords a stopword set
   * @param stemExclusionSet a set of terms not to be stemmed
   */
  public ItalianAnalyzer(Version matchVersion, Set<?> stopwords, Set<?> stemExclusionSet) {
    super(matchVersion, stopwords);
    this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(
        matchVersion, stemExclusionSet));
  }

  /**
   * Creates a
   * {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
   * which tokenizes all the text in the provided {@link Reader}.
   * 
   * @return A
   *         {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
   *         built from an {@link StandardTokenizer} filtered with
   *         {@link StandardFilter}, {@link ElisionFilter}, {@link LowerCaseFilter}, {@link StopFilter}
   *         , {@link KeywordMarkerFilter} if a stem exclusion set is
   *         provided and {@link ItalianLightStemFilter}.
   */
  @Override
  protected TokenStreamComponents createComponents(String fieldName,
      Reader reader) {
    final Tokenizer source = new StandardTokenizer(matchVersion, reader);
    TokenStream result = new StandardFilter(matchVersion, source);
    if (matchVersion.onOrAfter(Version.LUCENE_32)) {
      result = new ElisionFilter(matchVersion, result, DEFAULT_ARTICLES);
    }
    result = new LowerCaseFilter(matchVersion, result);
    result = new StopFilter(matchVersion, result, stopwords);
    if(!stemExclusionSet.isEmpty())
      result = new KeywordMarkerFilter(result, stemExclusionSet);
    if (matchVersion.onOrAfter(Version.LUCENE_36)) {
      result = new ItalianLightStemFilter(result);
    } else {
      result = new SnowballFilter(result, new ItalianStemmer());
    }
    return new TokenStreamComponents(source, result);
  }
}
