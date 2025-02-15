  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1568337
package org.apache.solr.cloud;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;

import org.apache.lucene.util.LuceneTestCase.Slow;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.cloud.ConnectionManager;
import org.apache.solr.common.cloud.SolrZkClient;
import org.junit.Ignore;

@Slow
public class ConnectionManagerTest extends SolrTestCaseJ4 {
  
  static final int TIMEOUT = 3000;
  
  @Ignore
  public void testConnectionManager() throws Exception {
    
    createTempDir();
    // setup a SolrZkClient to do some getBaseUrlForNodeName testing
    String zkDir = dataDir.getAbsolutePath() + File.separator
        + "zookeeper/server1/data";
    
    ZkTestServer server = new ZkTestServer(zkDir);
    try {
      server.run();
      
      AbstractZkTestCase.tryCleanSolrZkNode(server.getZkHost());
      AbstractZkTestCase.makeSolrZkNode(server.getZkHost());
      
      SolrZkClient zkClient = new SolrZkClient(server.getZkAddress(), TIMEOUT);
      ConnectionManager cm = zkClient.getConnectionManager();
      try {
        System.err.println("ISEXPIRED:" + cm.isLikelyExpired());
        assertFalse(cm.isLikelyExpired());
        
        zkClient.getSolrZooKeeper().pauseCnxn(TIMEOUT);
        
        long sessionId = zkClient.getSolrZooKeeper().getSessionId();
        server.expire(sessionId);
        Thread.sleep(TIMEOUT);
        
        assertTrue(cm.isLikelyExpired());
      } finally {
        cm.close();
        zkClient.close();
      }
    } finally {
      server.shutdown();
    }
  }
  
}
