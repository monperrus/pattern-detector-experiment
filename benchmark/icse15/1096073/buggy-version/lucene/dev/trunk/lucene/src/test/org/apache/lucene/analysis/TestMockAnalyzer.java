package org.apache.lucene.analysis;

import java.util.Arrays;

import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.BasicOperations;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;

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

public class TestMockAnalyzer extends BaseTokenStreamTestCase {

  /** Test a configuration that behaves a lot like WhitespaceAnalyzer */
  public void testWhitespace() throws Exception {
    Analyzer a = new MockAnalyzer(random);
    assertAnalyzesTo(a, "A bc defg hiJklmn opqrstuv wxy z ",
        new String[] { "a", "bc", "defg", "hijklmn", "opqrstuv", "wxy", "z" });
    assertAnalyzesToReuse(a, "aba cadaba shazam",
        new String[] { "aba", "cadaba", "shazam" });
    assertAnalyzesToReuse(a, "break on whitespace",
        new String[] { "break", "on", "whitespace" });
  }
  
  /** Test a configuration that behaves a lot like SimpleAnalyzer */
  public void testSimple() throws Exception {
    Analyzer a = new MockAnalyzer(random, MockTokenizer.SIMPLE, true);
    assertAnalyzesTo(a, "a-bc123 defg+hijklmn567opqrstuv78wxy_z ",
        new String[] { "a", "bc", "defg", "hijklmn", "opqrstuv", "wxy", "z" });
    assertAnalyzesToReuse(a, "aba4cadaba-Shazam",
        new String[] { "aba", "cadaba", "shazam" });
    assertAnalyzesToReuse(a, "break+on/Letters",
        new String[] { "break", "on", "letters" });
  }
  
  /** Test a configuration that behaves a lot like KeywordAnalyzer */
  public void testKeyword() throws Exception {
    Analyzer a = new MockAnalyzer(random, MockTokenizer.KEYWORD, false);
    assertAnalyzesTo(a, "a-bc123 defg+hijklmn567opqrstuv78wxy_z ",
        new String[] { "a-bc123 defg+hijklmn567opqrstuv78wxy_z " });
    assertAnalyzesToReuse(a, "aba4cadaba-Shazam",
        new String[] { "aba4cadaba-Shazam" });
    assertAnalyzesToReuse(a, "break+on/Nothing",
        new String[] { "break+on/Nothing" });
  }
  
  /** Test a configuration that behaves a lot like StopAnalyzer */
  public void testStop() throws Exception {
    Analyzer a = new MockAnalyzer(random, MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET, true);
    assertAnalyzesTo(a, "the quick brown a fox",
        new String[] { "quick", "brown", "fox" },
        new int[] { 2, 1, 2 });
    
    // disable positions
    a = new MockAnalyzer(random, MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET, false);
    assertAnalyzesTo(a, "the quick brown a fox",
        new String[] { "quick", "brown", "fox" },
        new int[] { 1, 1, 1 });
  }
  
  /** Test a configuration that behaves a lot like KeepWordFilter */
  public void testKeep() throws Exception {
    CharacterRunAutomaton keepWords = 
      new CharacterRunAutomaton(
          BasicOperations.complement(
              Automaton.union(
                  Arrays.asList(BasicAutomata.makeString("foo"), BasicAutomata.makeString("bar")))));
    Analyzer a = new MockAnalyzer(random, MockTokenizer.SIMPLE, true, keepWords, true);
    assertAnalyzesTo(a, "quick foo brown bar bar fox foo",
        new String[] { "foo", "bar", "bar", "foo" },
        new int[] { 2, 2, 1, 2 });
  }
  
  /** Test a configuration that behaves a lot like LengthFilter */
  public void testLength() throws Exception {
    CharacterRunAutomaton length5 = new CharacterRunAutomaton(new RegExp(".{5,}").toAutomaton());
    Analyzer a = new MockAnalyzer(random, MockTokenizer.WHITESPACE, true, length5, true);
    assertAnalyzesTo(a, "ok toolong fine notfine",
        new String[] { "ok", "fine" },
        new int[] { 1, 2 });
  }
}
