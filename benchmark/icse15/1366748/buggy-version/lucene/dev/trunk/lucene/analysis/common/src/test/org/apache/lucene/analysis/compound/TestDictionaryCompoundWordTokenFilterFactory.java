  + native
  + Date Author Id Revision HeadURL
package org.apache.lucene.analysis.compound;

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

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.ResourceAsStreamResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoader;

/**
 * Simple tests to ensure the Dictionary compound filter factory is working.
 */
public class TestDictionaryCompoundWordTokenFilterFactory extends BaseTokenStreamTestCase {
  /**
   * Ensure the filter actually decompounds text.
   */
  public void testDecompounding() throws Exception {
    Reader reader = new StringReader("I like to play softball");
    Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
    DictionaryCompoundWordTokenFilterFactory factory = new DictionaryCompoundWordTokenFilterFactory();
    ResourceLoader loader = new ResourceAsStreamResourceLoader(getClass());
    Map<String,String> args = new HashMap<String,String>();
    args.put("dictionary", "compoundDictionary.txt");
    factory.setLuceneMatchVersion(TEST_VERSION_CURRENT);
    factory.init(args);
    factory.inform(loader);
    TokenStream stream = factory.create(tokenizer);
    assertTokenStreamContents(stream, 
        new String[] { "I", "like", "to", "play", "softball", "soft", "ball" });
  }
  
}
