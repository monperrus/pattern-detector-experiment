  Merged /lucene/dev/trunk/solr/src/test/org/apache/solr/client:r1022708-1022710,1022730-1022735,1022748-1022755,1022762-1022793,1022798-1022802,1022927,1022956,1022998
  Merged /lucene/dev/trunk/solr/src/test/org/apache/solr/update/AutoCommitTest.java:r1022708-1022710,1022730-1022735,1022748-1022755,1022762-1022793,1022798-1022802,1022927,1022956,1022998
  Merged /lucene/dev/trunk/solr/src/test/org/apache/solr/analysis/TestRemoveDuplicatesTokenFilter.java:r1022708-1022710,1022730-1022735,1022748-1022755,1022762-1022793,1022798-1022802,1022927,1022956,1022998
  Merged /lucene/dev/trunk/solr/src/test/org/apache/solr/analysis/TestShingleFilterFactory.java:r1022708-1022710,1022730-1022735,1022748-1022755,1022762-1022793,1022798-1022802,1022927,1022956,1022998
  Merged /lucene/dev/trunk/solr/src/test/org/apache/solr/analysis/TestSynonymFilter.java:r1022708-1022710,1022730-1022735,1022748-1022755,1022762-1022793,1022798-1022802,1022927,1022956,1022998
  Merged /lucene/dev/trunk/solr/src/test/org/apache/solr/analysis/TestTrimFilter.java:r1022708-1022710,1022730-1022735,1022748-1022755,1022762-1022793,1022798-1022802,1022927,1022956,1022998
  Merged /lucene/dev/trunk/solr/src/test/org/apache/solr/analysis/TestPatternTokenizerFactory.java:r1022708-1022710,1022730-1022735,1022748-1022755,1022762-1022793,1022798-1022802,1022927,1022956,1022998
  Merged /lucene/dev/trunk/solr/src/test/org/apache/solr/request/SimpleFacetsTest.java:r1022708-1022710,1022730-1022735,1022748-1022755,1022762-1022793,1022798-1022802,1022927,1022956,1022998
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
package org.apache.solr.request;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.JavaBinCodec;
import org.apache.solr.response.BinaryQueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.AbstractSolrTestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Test for BinaryResponseWriter
 *
 * @version $Id$
 * @since solr 1.4
 */
public class TestBinaryResponseWriter extends AbstractSolrTestCase {

  public String getSchemaFile() {
    return "schema12.xml";
  }

  public String getSolrConfigFile() {
    return "solrconfig.xml";
  }

  /**
   * Tests known types implementation by asserting correct encoding/decoding of UUIDField
   */
  public void testUUID() throws Exception {
    String s = UUID.randomUUID().toString().toLowerCase(Locale.ENGLISH);
    assertU(adoc("id", "101", "uuid", s));
    assertU(commit());
    LocalSolrQueryRequest req = lrf.makeRequest("q", "*:*");
    SolrQueryResponse rsp = h.queryAndResponse(req.getParams().get(CommonParams.QT), req);
    BinaryQueryResponseWriter writer = (BinaryQueryResponseWriter) h.getCore().getQueryResponseWriter("javabin");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    writer.write(baos, req, rsp);
    NamedList res = (NamedList) new JavaBinCodec().unmarshal(new ByteArrayInputStream(baos.toByteArray()));
    SolrDocumentList docs = (SolrDocumentList) res.get("response");
    for (Object doc : docs) {
      SolrDocument document = (SolrDocument) doc;
      assertEquals("Returned object must be a string", "java.lang.String", document.getFieldValue("uuid").getClass().getName());
      assertEquals("Wrong UUID string returned", s, document.getFieldValue("uuid"));
    }
  }
}
