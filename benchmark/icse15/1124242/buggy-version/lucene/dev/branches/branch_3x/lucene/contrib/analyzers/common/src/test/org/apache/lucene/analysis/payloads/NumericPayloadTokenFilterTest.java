package org.apache.lucene.analysis.payloads;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.io.StringReader;

public class NumericPayloadTokenFilterTest extends BaseTokenStreamTestCase {

  public void test() throws IOException {
    String test = "The quick red fox jumped over the lazy brown dogs";

    NumericPayloadTokenFilter nptf = new NumericPayloadTokenFilter(new WordTokenFilter(new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(test))), 3, "D");
    boolean seenDogs = false;
    CharTermAttribute termAtt = nptf.getAttribute(CharTermAttribute.class);
    TypeAttribute typeAtt = nptf.getAttribute(TypeAttribute.class);
    PayloadAttribute payloadAtt = nptf.getAttribute(PayloadAttribute.class);
    while (nptf.incrementToken()) {
      if (termAtt.toString().equals("dogs")) {
        seenDogs = true;
        assertTrue(typeAtt.type() + " is not equal to " + "D", typeAtt.type().equals("D") == true);
        assertTrue("payloadAtt.getPayload() is null and it shouldn't be", payloadAtt.getPayload() != null);
        byte [] bytes = payloadAtt.getPayload().getData();//safe here to just use the bytes, otherwise we should use offset, length
        assertTrue(bytes.length + " does not equal: " + payloadAtt.getPayload().length(), bytes.length == payloadAtt.getPayload().length());
        assertTrue(payloadAtt.getPayload().getOffset() + " does not equal: " + 0, payloadAtt.getPayload().getOffset() == 0);
        float pay = PayloadHelper.decodeFloat(bytes);
        assertTrue(pay + " does not equal: " + 3, pay == 3);
      } else {
        assertTrue(typeAtt.type() + " is not null and it should be", typeAtt.type().equals("word"));
      }
    }
    assertTrue(seenDogs + " does not equal: " + true, seenDogs == true);
  }

  private final class WordTokenFilter extends TokenFilter {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    
    private WordTokenFilter(TokenStream input) {
      super(input);
    }
    
    @Override
    public boolean incrementToken() throws IOException {
      if (input.incrementToken()) {
        if (termAtt.toString().equals("dogs"))
          typeAtt.setType("D");
        return true;
      } else {
        return false;
      }
    }
  }

}
