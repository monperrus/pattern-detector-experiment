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

package org.apache.lucene.analysis.synonym;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.*;
import org.apache.lucene.analysis.ReusableAnalyzerBase;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util._TestUtil;

public class TestSynonymMapFilter extends BaseTokenStreamTestCase {

  private SynonymMap.Builder b;
  private Tokenizer tokensIn;
  private SynonymFilter tokensOut;
  private CharTermAttribute termAtt;
  private PositionIncrementAttribute posIncrAtt;
  private OffsetAttribute offsetAtt;

  private void add(String input, String output, boolean keepOrig) {
    b.add(new CharsRef(input.replaceAll(" +", "\u0000")),
          new CharsRef(output.replaceAll(" +", "\u0000")),
          keepOrig);
  }

  private void assertEquals(CharTermAttribute term, String expected) {
    assertEquals(expected.length(), term.length());
    final char[] buffer = term.buffer();
    for(int chIDX=0;chIDX<expected.length();chIDX++) {
      assertEquals(expected.charAt(chIDX), buffer[chIDX]);
    }
  }

  // For the output string: separate positions with a space,
  // and separate multiple tokens at each position with a
  // /.  If a token should have end offset != the input
  // token's end offset then add :X to it:

  // TODO: we should probably refactor this guy to use/take analyzer,
  // the tests are a little messy
  private void verify(String input, String output) throws Exception {
    if (VERBOSE) {
      System.out.println("TEST: verify input=" + input + " expectedOutput=" + output);
    }

    tokensIn.reset(new StringReader(input));
    tokensOut.reset();
    final String[] expected = output.split(" ");
    int expectedUpto = 0;
    while(tokensOut.incrementToken()) {

      if (VERBOSE) {
        System.out.println("  incr token=" + termAtt.toString() + " posIncr=" + posIncrAtt.getPositionIncrement() + " startOff=" + offsetAtt.startOffset() + " endOff=" + offsetAtt.endOffset());
      }

      assertTrue(expectedUpto < expected.length);
      final int startOffset = offsetAtt.startOffset();
      final int endOffset = offsetAtt.endOffset();

      final String[] expectedAtPos = expected[expectedUpto++].split("/");
      for(int atPos=0;atPos<expectedAtPos.length;atPos++) {
        if (atPos > 0) {
          assertTrue(tokensOut.incrementToken());
          if (VERBOSE) {
            System.out.println("  incr token=" + termAtt.toString() + " posIncr=" + posIncrAtt.getPositionIncrement() + " startOff=" + offsetAtt.startOffset() + " endOff=" + offsetAtt.endOffset());
          }
        }
        final int colonIndex = expectedAtPos[atPos].indexOf(':');
        final String expectedToken;
        final int expectedEndOffset;
        if (colonIndex != -1) {
          expectedToken = expectedAtPos[atPos].substring(0, colonIndex);
          expectedEndOffset = Integer.parseInt(expectedAtPos[atPos].substring(1+colonIndex));
        } else {
          expectedToken = expectedAtPos[atPos];
          expectedEndOffset = endOffset;
        }
        assertEquals(expectedToken, termAtt.toString());
        assertEquals(atPos == 0 ? 1 : 0,
                     posIncrAtt.getPositionIncrement());
        // start/end offset of all tokens at same pos should
        // be the same:
        assertEquals(startOffset, offsetAtt.startOffset());
        assertEquals(expectedEndOffset, offsetAtt.endOffset());
      }
    }
    tokensOut.end();
    tokensOut.close();
    if (VERBOSE) {
      System.out.println("  incr: END");
    }
    assertEquals(expectedUpto, expected.length);
  }

  public void testBasic() throws Exception {
    b = new SynonymMap.Builder(true);
    add("a", "foo", true);
    add("a b", "bar fee", true);
    add("b c", "dog collar", true);
    add("c d", "dog harness holder extras", true);
    add("m c e", "dog barks loudly", false);
    add("i j k", "feep", true);

    add("e f", "foo bar", false);
    add("e f", "baz bee", false);

    add("z", "boo", false);
    add("y", "bee", true);

    tokensIn = new MockTokenizer(new StringReader("a"),
                                 MockTokenizer.WHITESPACE,
                                 true);
    tokensIn.reset();
    assertTrue(tokensIn.incrementToken());
    assertFalse(tokensIn.incrementToken());
    tokensIn.end();
    tokensIn.close();

    tokensOut = new SynonymFilter(tokensIn,
                                     b.build(),
                                     true);
    termAtt = tokensOut.addAttribute(CharTermAttribute.class);
    posIncrAtt = tokensOut.addAttribute(PositionIncrementAttribute.class);
    offsetAtt = tokensOut.addAttribute(OffsetAttribute.class);

    verify("a b c", "a/bar b/fee c");

    // syn output extends beyond input tokens
    verify("x a b c d", "x a/bar b/fee c/dog d/harness holder extras");

    verify("a b a", "a/bar b/fee a/foo");

    // outputs that add to one another:
    verify("c d c d", "c/dog d/harness c/holder/dog d/extras/harness holder extras");

    // two outputs for same input
    verify("e f", "foo/baz bar/bee");

    // verify multi-word / single-output offsets:
    verify("g i j k g", "g i/feep:7 j k g");

    // mixed keepOrig true/false:
    verify("a m c e x", "a/foo dog barks loudly x");
    verify("c d m c e x", "c/dog d/harness holder/dog extras/barks loudly x");
    assertTrue(tokensOut.getCaptureCount() > 0);

    // no captureStates when no syns matched
    verify("p q r s t", "p q r s t");
    assertEquals(0, tokensOut.getCaptureCount());

    // no captureStates when only single-input syns, w/ no
    // lookahead needed, matched
    verify("p q z y t", "p q boo y/bee t");
    assertEquals(0, tokensOut.getCaptureCount());
  }

  private String getRandomString(char start, int alphabetSize, int length) {
    assert alphabetSize <= 26;
    char[] s = new char[2*length];
    for(int charIDX=0;charIDX<length;charIDX++) {
      s[2*charIDX] = (char) (start + random.nextInt(alphabetSize));
      s[2*charIDX+1] = ' ';
    }
    return new String(s);
  }

  private static class OneSyn {
    String in;
    List<String> out;
    boolean keepOrig;
  }

  public String slowSynMatcher(String doc, List<OneSyn> syns, int maxOutputLength) {
    assertTrue(doc.length() % 2 == 0);
    final int numInputs = doc.length()/2;
    boolean[] keepOrigs = new boolean[numInputs];
    boolean[] hasMatch = new boolean[numInputs];
    Arrays.fill(keepOrigs, false);
    String[] outputs = new String[numInputs + maxOutputLength];
    OneSyn[] matches = new OneSyn[numInputs];
    for(OneSyn syn : syns) {
      int idx = -1;
      while(true) {
        idx = doc.indexOf(syn.in, 1+idx);
        if (idx == -1) {
          break;
        }
        assertTrue(idx % 2 == 0);
        final int matchIDX = idx/2;
        assertTrue(syn.in.length() % 2 == 1);
        if (matches[matchIDX] == null) {
          matches[matchIDX] = syn;
        } else if (syn.in.length() > matches[matchIDX].in.length()) {
          // Greedy conflict resolution: longer match wins:
          matches[matchIDX] = syn;
        } else {
          assertTrue(syn.in.length() < matches[matchIDX].in.length());
        }
      }
    }

    // Greedy conflict resolution: if syn matches a range of inputs,
    // it prevents other syns from matching that range
    for(int inputIDX=0;inputIDX<numInputs;inputIDX++) {
      final OneSyn match = matches[inputIDX];
      if (match != null) {
        final int synInLength = (1+match.in.length())/2;
        for(int nextInputIDX=inputIDX+1;nextInputIDX<numInputs && nextInputIDX<(inputIDX+synInLength);nextInputIDX++) {
          matches[nextInputIDX] = null;
        }
      }
    }

    // Fill overlapping outputs:
    for(int inputIDX=0;inputIDX<numInputs;inputIDX++) {
      final OneSyn syn = matches[inputIDX];
      if (syn == null) {
        continue;
      }
      for(int idx=0;idx<(1+syn.in.length())/2;idx++) {
        hasMatch[inputIDX+idx] = true;
        keepOrigs[inputIDX+idx] |= syn.keepOrig;
      }
      for(String synOut : syn.out) {
        final String[] synOutputs = synOut.split(" ");
        assertEquals(synOutputs.length, (1+synOut.length())/2);
        final int matchEnd = inputIDX + synOutputs.length;
        int synUpto = 0;
        for(int matchIDX=inputIDX;matchIDX<matchEnd;matchIDX++) {
          if (outputs[matchIDX] == null) {
            outputs[matchIDX] = synOutputs[synUpto++];
          } else {
            outputs[matchIDX] = outputs[matchIDX] + "/" + synOutputs[synUpto++];
          }
          if (synOutputs.length == 1) {
            // Add endOffset
            outputs[matchIDX] = outputs[matchIDX] + ":" + ((inputIDX*2) + syn.in.length());
          }
        }
      }
    }

    StringBuilder sb = new StringBuilder();
    String[] inputTokens = doc.split(" ");
    final int limit = inputTokens.length + maxOutputLength;
    for(int inputIDX=0;inputIDX<limit;inputIDX++) {
      boolean posHasOutput = false;
      if (inputIDX >= numInputs && outputs[inputIDX] == null) {
        break;
      }
      if (inputIDX < numInputs && (!hasMatch[inputIDX] || keepOrigs[inputIDX])) {
        assertTrue(inputTokens[inputIDX].length() != 0);
        sb.append(inputTokens[inputIDX]);
        posHasOutput = true;
      }
      
      if (outputs[inputIDX] != null) {
        if (posHasOutput) {
          sb.append('/');
        }
        sb.append(outputs[inputIDX]);
      } else if (!posHasOutput) {
        continue;
      }
      if (inputIDX < limit-1) {
        sb.append(' ');
      }
    }

    return sb.toString();
  }

  public void testRandom() throws Exception {
    
    final int alphabetSize = _TestUtil.nextInt(random, 2, 7);

    final int docLen = atLeast(3000);
    //final int docLen = 50;

    final String document = getRandomString('a', alphabetSize, docLen);

    if (VERBOSE) {
      System.out.println("TEST: doc=" + document);
    }

    final int numSyn = atLeast(5);
    //final int numSyn = 2;

    final Map<String,OneSyn> synMap = new HashMap<String,OneSyn>();
    final List<OneSyn> syns = new ArrayList<OneSyn>();
    final boolean dedup = random.nextBoolean();
    if (VERBOSE) {
      System.out.println("  dedup=" + dedup);
    }
    b = new SynonymMap.Builder(dedup);
    for(int synIDX=0;synIDX<numSyn;synIDX++) {
      final String synIn = getRandomString('a', alphabetSize, _TestUtil.nextInt(random, 1, 5)).trim();
      OneSyn s = synMap.get(synIn);
      if (s == null) {
        s = new OneSyn();
        s.in = synIn;
        syns.add(s);
        s.out = new ArrayList<String>();
        synMap.put(synIn, s);
        s.keepOrig = random.nextBoolean();
      }
      final String synOut = getRandomString('0', 10, _TestUtil.nextInt(random, 1, 5)).trim();
      s.out.add(synOut);
      add(synIn, synOut, s.keepOrig);
      if (VERBOSE) {
        System.out.println("  syns[" + synIDX + "] = " + s.in + " -> " + s.out + " keepOrig=" + s.keepOrig);
      }
    }

    tokensIn = new MockTokenizer(new StringReader("a"),
                                 MockTokenizer.WHITESPACE,
                                 true);
    tokensIn.reset();
    assertTrue(tokensIn.incrementToken());
    assertFalse(tokensIn.incrementToken());
    tokensIn.end();
    tokensIn.close();

    tokensOut = new SynonymFilter(tokensIn,
                                     b.build(),
                                     true);
    termAtt = tokensOut.addAttribute(CharTermAttribute.class);
    posIncrAtt = tokensOut.addAttribute(PositionIncrementAttribute.class);
    offsetAtt = tokensOut.addAttribute(OffsetAttribute.class);

    if (dedup) {
      pruneDups(syns);
    }

    final String expected = slowSynMatcher(document, syns, 5);

    if (VERBOSE) {
      System.out.println("TEST: expected=" + expected);
    }

    verify(document, expected);
  }

  private void pruneDups(List<OneSyn> syns) {
    Set<String> seen = new HashSet<String>();
    for(OneSyn syn : syns) {
      int idx = 0;
      while(idx < syn.out.size()) {
        String out = syn.out.get(idx);
        if (!seen.contains(out)) {
          seen.add(out);
          idx++;
        } else {
          syn.out.remove(idx);
        }
      }
      seen.clear();
    }
  }

  private String randomNonEmptyString() {
    while(true) {
      final String s = _TestUtil.randomUnicodeString(random).trim();
      if (s.length() != 0 && s.indexOf('\u0000') == -1) {
        return s;
      }
    }
  }

  /** simple random test, doesn't verify correctness.
   *  does verify it doesnt throw exceptions, or that the stream doesn't misbehave
   */
  public void testRandom2() throws Exception {
    final int numIters = atLeast(10);
    for (int i = 0; i < numIters; i++) {
      b = new SynonymMap.Builder(random.nextBoolean());
      final int numEntries = atLeast(10);
      for (int j = 0; j < numEntries; j++) {
        add(randomNonEmptyString(), randomNonEmptyString(), random.nextBoolean());
      }
      final SynonymMap map = b.build();
      final boolean ignoreCase = random.nextBoolean();
      
      final Analyzer analyzer = new ReusableAnalyzerBase() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
          Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.SIMPLE, true);
          return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, ignoreCase));
        }
      };

      checkRandomData(random, analyzer, 1000*RANDOM_MULTIPLIER);
    }
  }
  
  // LUCENE-3375
  public void testVanishingTerms() throws Exception {
    String testFile = 
      "aaa => aaaa1 aaaa2 aaaa3\n" + 
      "bbb => bbbb1 bbbb2\n";
      
    SolrSynonymParser parser = new SolrSynonymParser(true, true, new MockAnalyzer(random));
    parser.add(new StringReader(testFile));
    final SynonymMap map = parser.build();
      
    Analyzer analyzer = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, true);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };
    
    // where did my pot go?!
    assertAnalyzesTo(analyzer, "xyzzy bbb pot of gold",
                     new String[] { "xyzzy", "bbbb1", "pot", "bbbb2", "of", "gold" });
    
    // this one nukes 'pot' and 'of'
    // xyzzy aaa pot of gold -> xyzzy aaaa1 aaaa2 aaaa3 gold
    assertAnalyzesTo(analyzer, "xyzzy aaa pot of gold",
                     new String[] { "xyzzy", "aaaa1", "pot", "aaaa2", "of", "aaaa3", "gold" });
  }

  public void testBasic2() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = false;
    add("aaa", "aaaa1 aaaa2 aaaa3", keepOrig);
    add("bbb", "bbbb1 bbbb2", keepOrig);
    tokensIn = new MockTokenizer(new StringReader("a"),
                                 MockTokenizer.WHITESPACE,
                                 true);
    tokensIn.reset();
    assertTrue(tokensIn.incrementToken());
    assertFalse(tokensIn.incrementToken());
    tokensIn.end();
    tokensIn.close();

    tokensOut = new SynonymFilter(tokensIn,
                                     b.build(),
                                     true);
    termAtt = tokensOut.addAttribute(CharTermAttribute.class);
    posIncrAtt = tokensOut.addAttribute(PositionIncrementAttribute.class);
    offsetAtt = tokensOut.addAttribute(OffsetAttribute.class);

    if (keepOrig) {
      verify("xyzzy bbb pot of gold", "xyzzy bbb/bbbb1 pot/bbbb2 of gold");
      verify("xyzzy aaa pot of gold", "xyzzy aaa/aaaa1 pot/aaaa2 of/aaaa3 gold");
    } else {
      verify("xyzzy bbb pot of gold", "xyzzy bbbb1 pot/bbbb2 of gold");
      verify("xyzzy aaa pot of gold", "xyzzy aaaa1 pot/aaaa2 of/aaaa3 gold");
    }
  }
  
  public void testMatching() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = false;
    add("a b", "ab", keepOrig);
    add("a c", "ac", keepOrig);
    add("a", "aa", keepOrig);
    add("b", "bb", keepOrig);
    add("z x c v", "zxcv", keepOrig);
    add("x c", "xc", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };

    checkOneTerm(a, "$", "$");
    checkOneTerm(a, "a", "aa");
    checkOneTerm(a, "b", "bb");
    
    assertAnalyzesTo(a, "a $",
       new String[] { "aa", "$" },
       new int[] { 1, 1 });
    
    assertAnalyzesTo(a, "$ a",
        new String[] { "$", "aa" },
        new int[] { 1, 1 });
    
    assertAnalyzesTo(a, "a a",
        new String[] { "aa", "aa" },
        new int[] { 1, 1 });
    
    assertAnalyzesTo(a, "z x c v",
        new String[] { "zxcv" },
        new int[] { 1 });
    
    assertAnalyzesTo(a, "z x c $",
        new String[] { "z", "xc", "$" },
        new int[] { 1, 1, 1 });
  }
  
  public void testRepeatsOff() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = false;
    add("a b", "ab", keepOrig);
    add("a b", "ab", keepOrig);
    add("a b", "ab", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };

    assertAnalyzesTo(a, "a b",
        new String[] { "ab" },
        new int[] { 1 });
  }
  
  public void testRepeatsOn() throws Exception {
    b = new SynonymMap.Builder(false);
    final boolean keepOrig = false;
    add("a b", "ab", keepOrig);
    add("a b", "ab", keepOrig);
    add("a b", "ab", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };

    assertAnalyzesTo(a, "a b",
        new String[] { "ab", "ab", "ab" },
        new int[] { 1, 0, 0 });
  }
  
  public void testRecursion() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = false;
    add("zoo", "zoo", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };
    
    assertAnalyzesTo(a, "zoo zoo $ zoo",
        new String[] { "zoo", "zoo", "$", "zoo" },
        new int[] { 1, 1, 1, 1 });
  }
 
  public void testRecursion2() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = false;
    add("zoo", "zoo", keepOrig);
    add("zoo", "zoo zoo", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };

    // verify("zoo zoo $ zoo", "zoo/zoo zoo/zoo/zoo $/zoo zoo/zoo zoo");
    assertAnalyzesTo(a, "zoo zoo $ zoo",
        new String[] { "zoo", "zoo", "zoo", "zoo", "zoo", "$", "zoo", "zoo", "zoo", "zoo" },
        new int[] { 1, 0, 1, 0, 0, 1, 0, 1, 0, 1 });
  }
  
  public void testIncludeOrig() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = true;
    add("a b", "ab", keepOrig);
    add("a c", "ac", keepOrig);
    add("a", "aa", keepOrig);
    add("b", "bb", keepOrig);
    add("z x c v", "zxcv", keepOrig);
    add("x c", "xc", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };
    
    assertAnalyzesTo(a, "$", 
        new String[] { "$" },
        new int[] { 1 });
    assertAnalyzesTo(a, "a", 
        new String[] { "a", "aa" },
        new int[] { 1, 0 });
    assertAnalyzesTo(a, "a", 
        new String[] { "a", "aa" },
        new int[] { 1, 0 });
    assertAnalyzesTo(a, "$ a", 
        new String[] { "$", "a", "aa" },
        new int[] { 1, 1, 0 });
    assertAnalyzesTo(a, "a $", 
        new String[] { "a", "aa", "$" },
        new int[] { 1, 0, 1 });
    assertAnalyzesTo(a, "$ a !", 
        new String[] { "$", "a", "aa", "!" },
        new int[] { 1, 1, 0, 1 });
    assertAnalyzesTo(a, "a a", 
        new String[] { "a", "aa", "a", "aa" },
        new int[] { 1, 0, 1, 0 });
    assertAnalyzesTo(a, "b", 
        new String[] { "b", "bb" },
        new int[] { 1, 0 });
    assertAnalyzesTo(a, "z x c v",
        new String[] { "z", "zxcv", "x", "c", "v" },
        new int[] { 1, 0, 1, 1, 1 });
    assertAnalyzesTo(a, "z x c $",
        new String[] { "z", "x", "xc", "c", "$" },
        new int[] { 1, 1, 0, 1, 1 });
  }
  
  public void testRecursion3() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = true;
    add("zoo zoo", "zoo", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };
    
    assertAnalyzesTo(a, "zoo zoo $ zoo",
        new String[] { "zoo", "zoo", "zoo", "$", "zoo" },
        new int[] { 1, 0, 1, 1, 1 });
  }
  
  public void testRecursion4() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = true;
    add("zoo zoo", "zoo", keepOrig);
    add("zoo", "zoo zoo", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };
    
    assertAnalyzesTo(a, "zoo zoo $ zoo",
        new String[] { "zoo", "zoo", "zoo", "$", "zoo", "zoo", "zoo" },
        new int[] { 1, 0, 1, 1, 1, 0, 1 });
  }
  
  public void testMultiwordOffsets() throws Exception {
    b = new SynonymMap.Builder(true);
    final boolean keepOrig = true;
    add("national hockey league", "nhl", keepOrig);
    final SynonymMap map = b.build();
    Analyzer a = new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new SynonymFilter(tokenizer, map, true));
      }
    };
    
    assertAnalyzesTo(a, "national hockey league",
        new String[] { "national", "nhl", "hockey", "league" },
        new int[] { 0, 0, 9, 16 },
        new int[] { 8, 22, 15, 22 },
        new int[] { 1, 0, 1, 1 });
  }
}
