  Merged /lucene/dev/trunk/lucene/JRE_VERSION_MIGRATION.txt:r1524809
  Merged /lucene/dev/trunk/lucene/BUILD.txt:r1524809
  Merged /lucene/dev/trunk/lucene/suggest:r1524809
  Merged /lucene/dev/trunk/lucene/analysis/icu/src/java/org/apache/lucene/collation/ICUCollationKeyFilterFactory.java:r1524809
package org.apache.lucene.analysis.core;

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

import org.apache.lucene.analysis.util.BaseTokenStreamFactoryTestCase;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoader;

public class TestStopFilterFactory extends BaseTokenStreamFactoryTestCase {

  public void testInform() throws Exception {
    ResourceLoader loader = new ClasspathResourceLoader(getClass());
    assertTrue("loader is null and it shouldn't be", loader != null);
    StopFilterFactory factory = (StopFilterFactory) tokenFilterFactory("Stop",
        "words", "stop-1.txt",
        "ignoreCase", "true");
    CharArraySet words = factory.getStopWords();
    assertTrue("words is null and it shouldn't be", words != null);
    assertTrue("words Size: " + words.size() + " is not: " + 2, words.size() == 2);
    assertTrue(factory.isIgnoreCase() + " does not equal: " + true, factory.isIgnoreCase() == true);

    factory = (StopFilterFactory) tokenFilterFactory("Stop",
        "words", "stop-1.txt, stop-2.txt",
        "ignoreCase", "true");
    words = factory.getStopWords();
    assertTrue("words is null and it shouldn't be", words != null);
    assertTrue("words Size: " + words.size() + " is not: " + 4, words.size() == 4);
    assertTrue(factory.isIgnoreCase() + " does not equal: " + true, factory.isIgnoreCase() == true);

    factory = (StopFilterFactory) tokenFilterFactory("Stop",
        "words", "stop-snowball.txt",
        "format", "snowball",
        "ignoreCase", "true");
    words = factory.getStopWords();
    assertEquals(8, words.size());
    assertTrue(words.contains("he"));
    assertTrue(words.contains("him"));
    assertTrue(words.contains("his"));
    assertTrue(words.contains("himself"));
    assertTrue(words.contains("she"));
    assertTrue(words.contains("her"));
    assertTrue(words.contains("hers"));
    assertTrue(words.contains("herself"));
  }
  
  /** Test that bogus arguments result in exception */
  public void testBogusArguments() throws Exception {
    try {
      tokenFilterFactory("Stop", "bogusArg", "bogusValue");
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Unknown parameters"));
    }
  }
}
