  Merged /lucene/dev/trunk/solr/test-framework:r1518018
  Merged /lucene/dev/trunk/solr/README.txt:r1518018
  Merged /lucene/dev/trunk/solr/webapp:r1518018
  Merged /lucene/dev/trunk/solr/cloud-dev:r1518018
  Merged /lucene/dev/trunk/solr/common-build.xml:r1518018
  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1518018
  Merged /lucene/dev/trunk/solr/scripts:r1518018
  Merged /lucene/dev/trunk/solr/core/src/test/org/apache/solr/core/TestConfig.java:r1518018
  Merged /lucene/dev/trunk/solr/core:r1518018
  Merged /lucene/dev/trunk/solr/solrj:r1518018
  Merged /lucene/dev/trunk/solr/example:r1518018
  Merged /lucene/dev/trunk/solr/build.xml:r1518018
  Merged /lucene/dev/trunk/solr/NOTICE.txt:r1518018
  Merged /lucene/dev/trunk/solr/LICENSE.txt:r1518018
package org.apache.solr.uima.processor;

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

import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;

/**
 * Factory for {@link UIMAUpdateRequestProcessor}
 * 
 *
 */
public class UIMAUpdateRequestProcessorFactory extends UpdateRequestProcessorFactory {

  private NamedList<Object> args;

  @SuppressWarnings("unchecked")
  @Override
  public void init(@SuppressWarnings("rawtypes") NamedList args) {
    this.args = (NamedList<Object>) args.get("uimaConfig");
  }

  @Override
  public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp,
          UpdateRequestProcessor next) {
    return new UIMAUpdateRequestProcessor(next, req.getCore(),
            new SolrUIMAConfigurationReader(args).readSolrUIMAConfiguration());
  }

}
