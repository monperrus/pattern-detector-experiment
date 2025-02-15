  Merged /lucene/dev/trunk/solr/testlogging.properties:r1448204
  Merged /lucene/dev/trunk/solr/cloud-dev:r1448204
  Merged /lucene/dev/trunk/solr/common-build.xml:r1448204
  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1448204
  Merged /lucene/dev/trunk/solr/scripts:r1448204
  Merged /lucene/dev/trunk/solr/core:r1448204
  Merged /lucene/dev/trunk/solr/solrj:r1448204
  Merged /lucene/dev/trunk/solr/example:r1448204
  Merged /lucene/dev/trunk/solr/build.xml:r1448204
  Merged /lucene/dev/trunk/solr/NOTICE.txt:r1448204
  Merged /lucene/dev/trunk/solr/LICENSE.txt:r1448204
  Merged /lucene/dev/trunk/solr/contrib:r1448204
  Merged /lucene/dev/trunk/solr/site:r1448204
  Merged /lucene/dev/trunk/solr/SYSTEM_REQUIREMENTS.txt:r1448204
  Merged /lucene/dev/trunk/solr/licenses/httpmime-NOTICE.txt:r1448204
  Merged /lucene/dev/trunk/solr/licenses/httpcore-LICENSE-ASL.txt:r1448204
  Merged /lucene/dev/trunk/solr/licenses/httpclient-NOTICE.txt:r1448204
  Merged /lucene/dev/trunk/solr/licenses/httpclient-LICENSE-ASL.txt:r1448204
  Merged /lucene/dev/trunk/solr/licenses/httpmime-LICENSE-ASL.txt:r1448204
  Merged /lucene/dev/trunk/solr/licenses/httpcore-NOTICE.txt:r1448204
  Merged /lucene/dev/trunk/solr/licenses:r1448204
  Merged /lucene/dev/trunk/solr/test-framework:r1448204
  Merged /lucene/dev/trunk/solr/README.txt:r1448204
  Merged /lucene/dev/trunk/solr/webapp:r1448204
  Merged /lucene/dev/trunk/solr:r1448204
  Merged /lucene/dev/trunk/lucene/benchmark:r1448204
  Merged /lucene/dev/trunk/lucene/spatial:r1448204
  Merged /lucene/dev/trunk/lucene/build.xml:r1448204
  Merged /lucene/dev/trunk/lucene/join:r1448204
  Merged /lucene/dev/trunk/lucene/tools:r1448204
  Merged /lucene/dev/trunk/lucene/backwards:r1448204
  Merged /lucene/dev/trunk/lucene/site:r1448204
  Merged /lucene/dev/trunk/lucene/licenses:r1448204
  Merged /lucene/dev/trunk/lucene/memory:r1448204
  Merged /lucene/dev/trunk/lucene/JRE_VERSION_MIGRATION.txt:r1448204
  Merged /lucene/dev/trunk/lucene/BUILD.txt:r1448204
  Merged /lucene/dev/trunk/lucene/suggest:r1448204
  Merged /lucene/dev/trunk/lucene/analysis/icu/src/java/org/apache/lucene/collation/ICUCollationKeyFilterFactory.java:r1448204
  Merged /lucene/dev/trunk/lucene/analysis:r1448204
  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1448204
  Merged /lucene/dev/trunk/lucene/grouping:r1448204
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
package org.apache.lucene.classification;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A simplistic Lucene based NaiveBayes classifier, see <code>http://en.wikipedia.org/wiki/Naive_Bayes_classifier</code>
 *
 * @lucene.experimental
 */
public class SimpleNaiveBayesClassifier implements Classifier<BytesRef> {

  private AtomicReader atomicReader;
  private String textFieldName;
  private String classFieldName;
  private int docsWithClassSize;
  private Analyzer analyzer;
  private IndexSearcher indexSearcher;

  /**
   * Creates a new NaiveBayes classifier.
   * Note that you must call {@link #train(AtomicReader, String, String, Analyzer) train()} before you can
   * classify any documents.
   */
  public SimpleNaiveBayesClassifier() {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void train(AtomicReader atomicReader, String textFieldName, String classFieldName, Analyzer analyzer)
      throws IOException {
    this.atomicReader = atomicReader;
    this.indexSearcher = new IndexSearcher(this.atomicReader);
    this.textFieldName = textFieldName;
    this.classFieldName = classFieldName;
    this.analyzer = analyzer;
    this.docsWithClassSize = MultiFields.getTerms(this.atomicReader, this.classFieldName).getDocCount();
  }

  private String[] tokenizeDoc(String doc) throws IOException {
    Collection<String> result = new LinkedList<String>();
    TokenStream tokenStream = analyzer.tokenStream(textFieldName, new StringReader(doc));
    CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();
    while (tokenStream.incrementToken()) {
      result.add(charTermAttribute.toString());
    }
    tokenStream.end();
    tokenStream.close();
    return result.toArray(new String[result.size()]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ClassificationResult<BytesRef> assignClass(String inputDocument) throws IOException {
    if (atomicReader == null) {
      throw new RuntimeException("need to train the classifier first");
    }
    double max = 0d;
    BytesRef foundClass = new BytesRef();

    Terms terms = MultiFields.getTerms(atomicReader, classFieldName);
    TermsEnum termsEnum = terms.iterator(null);
    BytesRef next;
    String[] tokenizedDoc = tokenizeDoc(inputDocument);
    while ((next = termsEnum.next()) != null) {
      // TODO : turn it to be in log scale
      double clVal = calculatePrior(next) * calculateLikelihood(tokenizedDoc, next);
      if (clVal > max) {
        max = clVal;
        foundClass = next.clone();
      }
    }
    return new ClassificationResult<BytesRef>(foundClass, max);
  }


  private double calculateLikelihood(String[] tokenizedDoc, BytesRef c) throws IOException {
    // for each word
    double result = 1d;
    for (String word : tokenizedDoc) {
      // search with text:word AND class:c
      int hits = getWordFreqForClass(word, c);

      // num : count the no of times the word appears in documents of class c (+1)
      double num = hits + 1; // +1 is added because of add 1 smoothing

      // den : for the whole dictionary, count the no of times a word appears in documents of class c (+|V|)
      double den = getTextTermFreqForClass(c) + docsWithClassSize;

      // P(w|c) = num/den
      double wordProbability = num / den;
      result *= wordProbability;
    }

    // P(d|c) = P(w1|c)*...*P(wn|c)
    return result;
  }

  private double getTextTermFreqForClass(BytesRef c) throws IOException {
    Terms terms = MultiFields.getTerms(atomicReader, textFieldName);
    long numPostings = terms.getSumDocFreq(); // number of term/doc pairs
    double avgNumberOfUniqueTerms = numPostings / (double) terms.getDocCount(); // avg # of unique terms per doc
    int docsWithC = atomicReader.docFreq(new Term(classFieldName, c));
    return avgNumberOfUniqueTerms * docsWithC; // avg # of unique terms in text field per doc * # docs with c
  }

  private int getWordFreqForClass(String word, BytesRef c) throws IOException {
    BooleanQuery booleanQuery = new BooleanQuery();
    booleanQuery.add(new BooleanClause(new TermQuery(new Term(textFieldName, word)), BooleanClause.Occur.MUST));
    booleanQuery.add(new BooleanClause(new TermQuery(new Term(classFieldName, c)), BooleanClause.Occur.MUST));
    TotalHitCountCollector totalHitCountCollector = new TotalHitCountCollector();
    indexSearcher.search(booleanQuery, totalHitCountCollector);
    return totalHitCountCollector.getTotalHits();
  }

  private double calculatePrior(BytesRef currentClass) throws IOException {
    return (double) docCount(currentClass) / docsWithClassSize;
  }

  private int docCount(BytesRef countedClass) throws IOException {
    return atomicReader.docFreq(new Term(classFieldName, countedClass));
  }
}
