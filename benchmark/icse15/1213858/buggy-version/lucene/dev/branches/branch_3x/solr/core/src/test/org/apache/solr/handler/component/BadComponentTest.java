  Merged /lucene/dev/trunk/lucene:r1213704
package org.apache.solr.handler.component;


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

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.SolrException;
import org.junit.Test;

/**
 * SOLR-1730, tests what happens when a component fails to initialize properly
 *
 **/
public class BadComponentTest extends SolrTestCaseJ4{
  @Test
  public void testBadElevate() throws Exception {
    try {
      System.setProperty("elevate.file", "foo.xml");
      initCore("solrconfig-elevate.xml", "schema12.xml");
      assertTrue(false);
    } catch (Throwable e) {
      log.error("Exception", e);
      assertTrue(true);
    } finally {
      System.clearProperty("elevate.file");
    }
  }
}
