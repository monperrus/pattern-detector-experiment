package org.apache.lucene.analysis.miscellaneous;

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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.LengthFilter;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

/**
 * Factory for {@link LengthFilter}. 
 * <pre class="prettyprint">
 * &lt;fieldType name="text_lngth" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.LengthFilterFactory" min="0" max="1" enablePositionIncrements="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
public class LengthFilterFactory extends TokenFilterFactory {
  final int min;
  final int max;
  final boolean enablePositionIncrements;
  public static final String MIN_KEY = "min";
  public static final String MAX_KEY = "max";

  /** Creates a new LengthFilterFactory */
  public LengthFilterFactory(Map<String, String> args) {
    super(args);
    min = getInt(args, MIN_KEY, 0, false);
    max = getInt(args, MAX_KEY, 0, false);
    enablePositionIncrements = getBoolean(args, "enablePositionIncrements", false);
    if (!args.isEmpty()) {
      throw new IllegalArgumentException("Unknown parameters: " + args);
    }
  }
  
  @Override
  public LengthFilter create(TokenStream input) {
    return new LengthFilter(enablePositionIncrements, input,min,max);
  }
}
