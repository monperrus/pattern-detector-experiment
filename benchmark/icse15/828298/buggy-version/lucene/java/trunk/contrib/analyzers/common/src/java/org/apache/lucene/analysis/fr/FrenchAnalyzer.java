package org.apache.lucene.analysis.fr;

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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link Analyzer} for French language. 
 * <p>
 * Supports an external list of stopwords (words that
 * will not be indexed at all) and an external list of exclusions (word that will
 * not be stemmed, but indexed).
 * A default set of stopwords is used unless an alternative list is specified, but the
 * exclusion list is empty by default.
 * </p>
 */
public final class FrenchAnalyzer extends Analyzer {

  /**
   * Extended list of typical French stopwords.
   */
  public final static String[] FRENCH_STOP_WORDS = {
    "a", "afin", "ai", "ainsi", "après", "attendu", "au", "aujourd", "auquel", "aussi",
    "autre", "autres", "aux", "auxquelles", "auxquels", "avait", "avant", "avec", "avoir",
    "c", "car", "ce", "ceci", "cela", "celle", "celles", "celui", "cependant", "certain",
    "certaine", "certaines", "certains", "ces", "cet", "cette", "ceux", "chez", "ci",
    "combien", "comme", "comment", "concernant", "contre", "d", "dans", "de", "debout",
    "dedans", "dehors", "delà", "depuis", "derrière", "des", "désormais", "desquelles",
    "desquels", "dessous", "dessus", "devant", "devers", "devra", "divers", "diverse",
    "diverses", "doit", "donc", "dont", "du", "duquel", "durant", "dès", "elle", "elles",
    "en", "entre", "environ", "est", "et", "etc", "etre", "eu", "eux", "excepté", "hormis",
    "hors", "hélas", "hui", "il", "ils", "j", "je", "jusqu", "jusque", "l", "la", "laquelle",
    "le", "lequel", "les", "lesquelles", "lesquels", "leur", "leurs", "lorsque", "lui", "là",
    "ma", "mais", "malgré", "me", "merci", "mes", "mien", "mienne", "miennes", "miens", "moi",
    "moins", "mon", "moyennant", "même", "mêmes", "n", "ne", "ni", "non", "nos", "notre",
    "nous", "néanmoins", "nôtre", "nôtres", "on", "ont", "ou", "outre", "où", "par", "parmi",
    "partant", "pas", "passé", "pendant", "plein", "plus", "plusieurs", "pour", "pourquoi",
    "proche", "près", "puisque", "qu", "quand", "que", "quel", "quelle", "quelles", "quels",
    "qui", "quoi", "quoique", "revoici", "revoilà", "s", "sa", "sans", "sauf", "se", "selon",
    "seront", "ses", "si", "sien", "sienne", "siennes", "siens", "sinon", "soi", "soit",
    "son", "sont", "sous", "suivant", "sur", "ta", "te", "tes", "tien", "tienne", "tiennes",
    "tiens", "toi", "ton", "tous", "tout", "toute", "toutes", "tu", "un", "une", "va", "vers",
    "voici", "voilà", "vos", "votre", "vous", "vu", "vôtre", "vôtres", "y", "à", "ça", "ès",
    "été", "être", "ô"
  };

  /**
   * Contains the stopwords used with the {@link StopFilter}.
   */
  private Set stoptable = new HashSet();
  /**
   * Contains words that should be indexed but not stemmed.
   */
  private Set excltable = new HashSet();

  /**
   * Builds an analyzer with the default stop words ({@link #FRENCH_STOP_WORDS}).
   */
  public FrenchAnalyzer() {
    stoptable = StopFilter.makeStopSet(FRENCH_STOP_WORDS);
  }

  /**
   * Builds an analyzer with the given stop words.
   */
  public FrenchAnalyzer(String... stopwords) {
    stoptable = StopFilter.makeStopSet(stopwords);
  }

  /**
   * Builds an analyzer with the given stop words.
   * @throws IOException
   */
  public FrenchAnalyzer(File stopwords) throws IOException {
    stoptable = new HashSet(WordlistLoader.getWordSet(stopwords));
  }

  /**
   * Builds an exclusionlist from an array of Strings.
   */
  public void setStemExclusionTable(String... exclusionlist) {
    excltable = StopFilter.makeStopSet(exclusionlist);
    setPreviousTokenStream(null); // force a new stemmer to be created
  }

  /**
   * Builds an exclusionlist from a Map.
   */
  public void setStemExclusionTable(Map exclusionlist) {
    excltable = new HashSet(exclusionlist.keySet());
    setPreviousTokenStream(null); // force a new stemmer to be created
  }

  /**
   * Builds an exclusionlist from the words contained in the given file.
   * @throws IOException
   */
  public void setStemExclusionTable(File exclusionlist) throws IOException {
    excltable = new HashSet(WordlistLoader.getWordSet(exclusionlist));
    setPreviousTokenStream(null); // force a new stemmer to be created
  }

  /**
   * Creates a {@link TokenStream} which tokenizes all the text in the provided
   * {@link Reader}.
   *
   * @return A {@link TokenStream} built from a {@link StandardTokenizer} 
   *         filtered with {@link StandardFilter}, {@link StopFilter}, 
   *         {@link FrenchStemFilter} and {@link LowerCaseFilter}
   */
  public final TokenStream tokenStream(String fieldName, Reader reader) {

    if (fieldName == null) throw new IllegalArgumentException("fieldName must not be null");
    if (reader == null) throw new IllegalArgumentException("reader must not be null");

    TokenStream result = new StandardTokenizer(reader);
    result = new StandardFilter(result);
    result = new StopFilter(false, result, stoptable);
    result = new FrenchStemFilter(result, excltable);
    // Convert to lowercase after stemming!
    result = new LowerCaseFilter(result);
    return result;
  }
  
  private class SavedStreams {
    Tokenizer source;
    TokenStream result;
  };
  
  /**
   * Returns a (possibly reused) {@link TokenStream} which tokenizes all the 
   * text in the provided {@link Reader}.
   *
   * @return A {@link TokenStream} built from a {@link StandardTokenizer} 
   *         filtered with {@link StandardFilter}, {@link StopFilter}, 
   *         {@link FrenchStemFilter} and {@link LowerCaseFilter}
   */
  public TokenStream reusableTokenStream(String fieldName, Reader reader)
      throws IOException {
    SavedStreams streams = (SavedStreams) getPreviousTokenStream();
    if (streams == null) {
      streams = new SavedStreams();
      streams.source = new StandardTokenizer(reader);
      streams.result = new StandardFilter(streams.source);
      streams.result = new StopFilter(false, streams.result, stoptable);
      streams.result = new FrenchStemFilter(streams.result, excltable);
      // Convert to lowercase after stemming!
      streams.result = new LowerCaseFilter(streams.result);
      setPreviousTokenStream(streams);
    } else {
      streams.source.reset(reader);
    }
    return streams.result;
  }
}

