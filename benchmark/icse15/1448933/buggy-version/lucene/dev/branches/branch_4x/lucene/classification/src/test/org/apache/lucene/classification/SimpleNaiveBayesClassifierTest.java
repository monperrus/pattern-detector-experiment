  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1448932
  Merged /lucene/dev/trunk/lucene/grouping:r1448932
  Merged /lucene/dev/trunk/lucene/misc:r1448932
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
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenizer;
import org.junit.Test;

import java.io.Reader;

/**
 * Testcase for {@link SimpleNaiveBayesClassifier}
 */
public class SimpleNaiveBayesClassifierTest extends ClassificationTestBase {

  @Test
  public void testBasicUsage() throws Exception {
    checkCorrectClassification(new SimpleNaiveBayesClassifier(), new MockAnalyzer(random()));
  }

  @Test
  public void testNGramUsage() throws Exception {
    checkCorrectClassification(new SimpleNaiveBayesClassifier(), new NGramAnalyzer());
  }

  private class NGramAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
      return new TokenStreamComponents(new EdgeNGramTokenizer(reader, EdgeNGramTokenizer.Side.BACK,
          10, 20));
    }
  }

}
