package org.apache.lucene.analysis.payloads;

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

import org.apache.lucene.analysis.payloads.NumericPayloadTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.InitializationException;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import java.util.Map;

/** 
 * Factory for {@link NumericPayloadTokenFilter}.
 * <pre class="prettyprint" >
 * &lt;fieldType name="text_numpayload" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.NumericPayloadTokenFilterFactory" payload="24" typeMatch="word"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 */
public class NumericPayloadTokenFilterFactory extends TokenFilterFactory {
  private float payload;
  private String typeMatch;
  @Override
  public void init(Map<String, String> args) {
    super.init(args);
    String payloadArg = args.get("payload");
    typeMatch = args.get("typeMatch");
    if (payloadArg == null || typeMatch == null) {
      throw new InitializationException("Both payload and typeMatch are required");
    }
    payload = Float.parseFloat(payloadArg);
  }
  public NumericPayloadTokenFilter create(TokenStream input) {
    return new NumericPayloadTokenFilter(input,payload,typeMatch);
  }
}

