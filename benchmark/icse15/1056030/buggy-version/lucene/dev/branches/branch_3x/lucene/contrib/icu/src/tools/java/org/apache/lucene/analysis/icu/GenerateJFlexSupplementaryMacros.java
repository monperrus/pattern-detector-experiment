  + native
  Merged /lucene/dev/trunk/modules/analysis/common/src/java/org/apache/lucene/analysis/standard:r1056014
package org.apache.lucene.analysis.icu;

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

import java.util.HashMap;

import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;

/** creates a macro to augment jflex's unicode wordbreak support for > BMP */
public class GenerateJFlexSupplementaryMacros {
  private static final UnicodeSet BMP = new UnicodeSet("[\u0000-\uFFFF]");
  
  public static void main(String args[]) throws Exception {
    outputMacro("ALetterSupp",         "[:WordBreak=ALetter:]");
    outputMacro("FormatSupp",          "[:WordBreak=Format:]");
    outputMacro("ExtendSupp",          "[:WordBreak=Extend:]");
    outputMacro("NumericSupp",         "[:WordBreak=Numeric:]");
    outputMacro("KatakanaSupp",        "[:WordBreak=Katakana:]");
    outputMacro("MidLetterSupp",       "[:WordBreak=MidLetter:]");
    outputMacro("MidNumSupp",          "[:WordBreak=MidNum:]");
    outputMacro("MidNumLetSupp",       "[:WordBreak=MidNumLet:]");
    outputMacro("ExtendNumLetSupp",    "[:WordBreak=ExtendNumLet:]");
    outputMacro("ExtendNumLetSupp",    "[:WordBreak=ExtendNumLet:]");
    outputMacro("ComplexContextSupp",  "[:LineBreak=Complex_Context:]");
    outputMacro("HanSupp",             "[:Script=Han:]");
    outputMacro("HiraganaSupp",        "[:Script=Hiragana:]");
  }
  
  // we have to carefully output the possibilities as compact utf-16
  // range expressions, or jflex will OOM!
  static void outputMacro(String name, String pattern) {
    UnicodeSet set = new UnicodeSet(pattern);
    set.removeAll(BMP);
    System.out.println(name + " = (");
    // if the set is empty, we have to do this or jflex will barf
    if (set.isEmpty()) {
      System.out.println("\t  []");
    }
    
    HashMap<Character,UnicodeSet> utf16ByLead = new HashMap<Character,UnicodeSet>();
    for (UnicodeSetIterator it = new UnicodeSetIterator(set); it.next();) {    
      char utf16[] = Character.toChars(it.codepoint);
      UnicodeSet trails = utf16ByLead.get(utf16[0]);
      if (trails == null) {
        trails = new UnicodeSet();
        utf16ByLead.put(utf16[0], trails);
      }
      trails.add(utf16[1]);
    }
    
    boolean isFirst = true;
    for (Character c : utf16ByLead.keySet()) {
      UnicodeSet trail = utf16ByLead.get(c);
      System.out.print( isFirst ? "\t  " : "\t| ");
      isFirst = false;
      System.out.println("([\\u" + Integer.toHexString(c) + "]" + trail.getRegexEquivalent() + ")");
    }
    System.out.println(")");
  }
}
