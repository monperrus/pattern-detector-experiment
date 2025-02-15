  + Date Author Id Revision HeadURL
  + native
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

package org.apache.solr.core;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;
import org.apache.solr.common.params.EventParams;
import org.apache.lucene.store.Directory;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestQuerySenderListener extends SolrTestCaseJ4 {

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig-querysender.xml","schema.xml");
  }

  @Test
  public void testRequestHandlerRegistry() {
    // property values defined in build.xml
    SolrCore core = h.getCore();

    assertEquals( 1, core.firstSearcherListeners.size() );
    assertEquals( 1, core.newSearcherListeners.size() );
  }

  @Test
  public void testSearcherEvents() throws Exception {
    SolrCore core = h.getCore();
    SolrEventListener newSearcherListener = core.newSearcherListeners.get(0);
    assertTrue("Not an instance of QuerySenderListener", newSearcherListener instanceof QuerySenderListener);
    QuerySenderListener qsl = (QuerySenderListener) newSearcherListener;

    RefCounted<SolrIndexSearcher> currentSearcherRef = core.getSearcher();
    SolrIndexSearcher currentSearcher = currentSearcherRef.get();
    qsl.newSearcher(currentSearcher, null);//test new Searcher
    MockQuerySenderListenerReqHandler mock = (MockQuerySenderListenerReqHandler) core.getRequestHandler("mock");
    assertNotNull("Mock is null", mock);
    String evt = mock.req.getParams().get(EventParams.EVENT);
    assertNotNull("Event is null", evt);
    assertTrue(evt + " is not equal to " + EventParams.FIRST_SEARCHER, evt.equals(EventParams.FIRST_SEARCHER) == true);
    Directory dir = currentSearcher.getReader().directory();
    SolrIndexSearcher newSearcher = new SolrIndexSearcher(core, core.getSchema(), "testQuerySenderListener", dir, true, false);

    qsl.newSearcher(newSearcher, currentSearcher);
    evt = mock.req.getParams().get(EventParams.EVENT);
    assertNotNull("Event is null", evt);
    assertTrue(evt + " is not equal to " + EventParams.NEW_SEARCHER, evt.equals(EventParams.NEW_SEARCHER) == true);
    newSearcher.close();
    currentSearcherRef.decref();
  }

}

