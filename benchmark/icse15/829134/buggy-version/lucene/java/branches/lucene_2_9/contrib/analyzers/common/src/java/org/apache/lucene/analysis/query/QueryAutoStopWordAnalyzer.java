package org.apache.lucene.analysis.query;
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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.util.StringHelper;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * An {@link Analyzer} used primarily at query time to wrap another analyzer and provide a layer of protection
 * which prevents very common words from being passed into queries. 
 * <p>
 * For very large indexes the cost
 * of reading TermDocs for a very common word can be  high. This analyzer was created after experience with
 * a 38 million doc index which had a term in around 50% of docs and was causing TermQueries for 
 * this term to take 2 seconds.
 * </p>
 * <p>
 * Use the various "addStopWords" methods in this class to automate the identification and addition of 
 * stop words found in an already existing index.
 * </p>
 */
public class QueryAutoStopWordAnalyzer extends Analyzer {
  Analyzer delegate;
  HashMap stopWordsPerField = new HashMap();
  //The default maximum percentage (40%) of index documents which
  //can contain a term, after which the term is considered to be a stop word.
  public static final float defaultMaxDocFreqPercent = 0.4f;

  /**
   * Initializes this analyzer with the Analyzer object that actually produces the tokens
   *
   * @param delegate The choice of {@link Analyzer} that is used to produce the token stream which needs filtering
   */
  public QueryAutoStopWordAnalyzer(Analyzer delegate) {
    this.delegate = delegate;
    setOverridesTokenStreamMethod(QueryAutoStopWordAnalyzer.class);
  }

  /**
   * Automatically adds stop words for all fields with terms exceeding the defaultMaxDocFreqPercent
   *
   * @param reader The {@link IndexReader} which will be consulted to identify potential stop words that
   *               exceed the required document frequency
   * @return The number of stop words identified.
   * @throws IOException
   */
  public int addStopWords(IndexReader reader) throws IOException {
    return addStopWords(reader, defaultMaxDocFreqPercent);
  }

  /**
   * Automatically adds stop words for all fields with terms exceeding the maxDocFreqPercent
   *
   * @param reader     The {@link IndexReader} which will be consulted to identify potential stop words that
   *                   exceed the required document frequency
   * @param maxDocFreq The maximum number of index documents which can contain a term, after which
   *                   the term is considered to be a stop word
   * @return The number of stop words identified.
   * @throws IOException
   */
  public int addStopWords(IndexReader reader, int maxDocFreq) throws IOException {
    int numStopWords = 0;
    Collection fieldNames = reader.getFieldNames(IndexReader.FieldOption.INDEXED);
    for (Iterator iter = fieldNames.iterator(); iter.hasNext();) {
      String fieldName = (String) iter.next();
      numStopWords += addStopWords(reader, fieldName, maxDocFreq);
    }
    return numStopWords;
  }

  /**
   * Automatically adds stop words for all fields with terms exceeding the maxDocFreqPercent
   *
   * @param reader        The {@link IndexReader} which will be consulted to identify potential stop words that
   *                      exceed the required document frequency
   * @param maxPercentDocs The maximum percentage (between 0.0 and 1.0) of index documents which
   *                      contain a term, after which the word is considered to be a stop word.
   * @return The number of stop words identified.
   * @throws IOException
   */
  public int addStopWords(IndexReader reader, float maxPercentDocs) throws IOException {
    int numStopWords = 0;
    Collection fieldNames = reader.getFieldNames(IndexReader.FieldOption.INDEXED);
    for (Iterator iter = fieldNames.iterator(); iter.hasNext();) {
      String fieldName = (String) iter.next();
      numStopWords += addStopWords(reader, fieldName, maxPercentDocs);
    }
    return numStopWords;
  }

  /**
   * Automatically adds stop words for the given field with terms exceeding the maxPercentDocs
   *
   * @param reader         The {@link IndexReader} which will be consulted to identify potential stop words that
   *                       exceed the required document frequency
   * @param fieldName      The field for which stopwords will be added
   * @param maxPercentDocs The maximum percentage (between 0.0 and 1.0) of index documents which
   *                       contain a term, after which the word is considered to be a stop word.
   * @return The number of stop words identified.
   * @throws IOException
   */
  public int addStopWords(IndexReader reader, String fieldName, float maxPercentDocs) throws IOException {
    return addStopWords(reader, fieldName, (int) (reader.numDocs() * maxPercentDocs));
  }

  /**
   * Automatically adds stop words for the given field with terms exceeding the maxPercentDocs
   *
   * @param reader     The {@link IndexReader} which will be consulted to identify potential stop words that
   *                   exceed the required document frequency
   * @param fieldName  The field for which stopwords will be added
   * @param maxDocFreq The maximum number of index documents which
   *                   can contain a term, after which the term is considered to be a stop word.
   * @return The number of stop words identified.
   * @throws IOException
   */
  public int addStopWords(IndexReader reader, String fieldName, int maxDocFreq) throws IOException {
    HashSet stopWords = new HashSet();
    String internedFieldName = StringHelper.intern(fieldName);
    TermEnum te = reader.terms(new Term(fieldName));
    Term term = te.term();
    while (term != null) {
      if (term.field() != internedFieldName) {
        break;
      }
      if (te.docFreq() > maxDocFreq) {
        stopWords.add(term.text());
      }
      if (!te.next()) {
        break;
      }
      term = te.term();
    }
    stopWordsPerField.put(fieldName, stopWords);
    
    /* if the stopwords for a field are changed,
     * then saved streams for that field are erased.
     */
    Map streamMap = (Map) getPreviousTokenStream();
    if (streamMap != null)
      streamMap.remove(fieldName);
    
    return stopWords.size();
  }

  public TokenStream tokenStream(String fieldName, Reader reader) {
    TokenStream result;
    try {
      result = delegate.reusableTokenStream(fieldName, reader);
    } catch (IOException e) {
      result = delegate.tokenStream(fieldName, reader);
    }
    HashSet stopWords = (HashSet) stopWordsPerField.get(fieldName);
    if (stopWords != null) {
      result = new StopFilter(result, stopWords);
    }
    return result;
  }
  
  private class SavedStreams {
    /* the underlying stream */
    TokenStream wrapped;

    /*
     * when there are no stopwords for the field, refers to wrapped.
     * if there stopwords, it is a StopFilter around wrapped.
     */
    TokenStream withStopFilter;
  };
  
  public TokenStream reusableTokenStream(String fieldName, Reader reader)
      throws IOException {
    if (overridesTokenStreamMethod) {
      // LUCENE-1678: force fallback to tokenStream() if we
      // have been subclassed and that subclass overrides
      // tokenStream but not reusableTokenStream
      return tokenStream(fieldName, reader);
    }

    /* map of SavedStreams for each field */
    Map streamMap = (Map) getPreviousTokenStream();
    if (streamMap == null) {
      streamMap = new HashMap();
      setPreviousTokenStream(streamMap);
    }

    SavedStreams streams = (SavedStreams) streamMap.get(fieldName);
    if (streams == null) {
      /* an entry for this field does not exist, create one */
      streams = new SavedStreams();
      streamMap.put(fieldName, streams);
      streams.wrapped = delegate.reusableTokenStream(fieldName, reader);

      /* if there are any stopwords for the field, save the stopfilter */
      HashSet stopWords = (HashSet) stopWordsPerField.get(fieldName);
      if (stopWords != null)
        streams.withStopFilter = new StopFilter(streams.wrapped, stopWords);
      else
        streams.withStopFilter = streams.wrapped;

    } else {
      /*
       * an entry for this field exists, verify the wrapped stream has not
       * changed. if it has not, reuse it, otherwise wrap the new stream.
       */
      TokenStream result = delegate.reusableTokenStream(fieldName, reader);
      if (result == streams.wrapped) {
        /* the wrapped analyzer reused the stream */
        streams.withStopFilter.reset();
      } else {
        /*
         * the wrapped analyzer did not. if there are any stopwords for the
         * field, create a new StopFilter around the new stream
         */
        streams.wrapped = result;
        HashSet stopWords = (HashSet) stopWordsPerField.get(fieldName);
        if (stopWords != null)
          streams.withStopFilter = new StopFilter(streams.wrapped, stopWords);
        else
          streams.withStopFilter = streams.wrapped;
      }
    }

    return streams.withStopFilter;
  }

  /**
   * Provides information on which stop words have been identified for a field
   *
   * @param fieldName The field for which stop words identified in "addStopWords"
   *                  method calls will be returned
   * @return the stop words identified for a field
   */
  public String[] getStopWords(String fieldName) {
    String[] result;
    HashSet stopWords = (HashSet) stopWordsPerField.get(fieldName);
    if (stopWords != null) {
      result = (String[]) stopWords.toArray(new String[stopWords.size()]);
    } else {
      result = new String[0];
    }
    return result;
  }

  /**
   * Provides information on which stop words have been identified for all fields
   *
   * @return the stop words (as terms)
   */
  public Term[] getStopWords() {
    ArrayList allStopWords = new ArrayList();
    for (Iterator iter = stopWordsPerField.keySet().iterator(); iter.hasNext();) {
      String fieldName = (String) iter.next();
      HashSet stopWords = (HashSet) stopWordsPerField.get(fieldName);
      for (Iterator iterator = stopWords.iterator(); iterator.hasNext();) {
        String text = (String) iterator.next();
        allStopWords.add(new Term(fieldName, text));
      }
    }
    return (Term[]) allStopWords.toArray(new Term[allStopWords.size()]);
	}

}
