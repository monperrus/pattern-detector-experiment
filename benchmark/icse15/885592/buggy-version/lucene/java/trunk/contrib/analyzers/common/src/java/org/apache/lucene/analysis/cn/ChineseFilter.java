package org.apache.lucene.analysis.cn;

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

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 * A {@link TokenFilter} with a stop word table.  
 * <ul>
 * <li>Numeric tokens are removed.
 * <li>English tokens must be larger than 1 character.
 * <li>One Chinese character as one Chinese word.
 * </ul>
 * TO DO:
 * <ol>
 * <li>Add Chinese stop words, such as \ue400
 * <li>Dictionary based Chinese word extraction
 * <li>Intelligent Chinese word extraction
 * </ol>
 * 
 * @version 1.0
 *
 */

public final class ChineseFilter extends TokenFilter {


    // Only English now, Chinese to be added later.
    public static final String[] STOP_WORDS = {
    "and", "are", "as", "at", "be", "but", "by",
    "for", "if", "in", "into", "is", "it",
    "no", "not", "of", "on", "or", "such",
    "that", "the", "their", "then", "there", "these",
    "they", "this", "to", "was", "will", "with"
    };


    private CharArraySet stopTable;

    private TermAttribute termAtt;
    
    public ChineseFilter(TokenStream in) {
        super(in);

        stopTable = new CharArraySet(Arrays.asList(STOP_WORDS), false);
        termAtt = addAttribute(TermAttribute.class);
    }

    @Override
    public boolean incrementToken() throws IOException {

        while (input.incrementToken()) {
            char text[] = termAtt.termBuffer();
            int termLength = termAtt.termLength();

          // why not key off token type here assuming ChineseTokenizer comes first?
            if (!stopTable.contains(text, 0, termLength)) {
                switch (Character.getType(text[0])) {

                case Character.LOWERCASE_LETTER:
                case Character.UPPERCASE_LETTER:

                    // English word/token should larger than 1 character.
                    if (termLength>1) {
                        return true;
                    }
                    break;
                case Character.OTHER_LETTER:

                    // One Chinese character as one Chinese word.
                    // Chinese word extraction to be added later here.

                    return true;
                }

            }

        }
        return false;
    }

}
