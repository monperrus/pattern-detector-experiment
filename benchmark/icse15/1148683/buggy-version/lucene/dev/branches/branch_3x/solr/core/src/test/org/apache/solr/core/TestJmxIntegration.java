  Merged /lucene/dev/trunk/lucene/backwards/src/test:r1148681
  Merged /lucene/dev/trunk/lucene/backwards/src/test-framework:r1148681
  Merged /lucene/dev/trunk/lucene/backwards:r1148681
  Merged /lucene/dev/trunk/lucene:r1148681
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

import org.apache.solr.core.JmxMonitoredMap.SolrDynamicMBean;
import org.apache.solr.util.AbstractSolrTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;
import java.util.Hashtable;

/**
 * Test for JMX Integration
 *
 * @version $Id$
 * @since solr 1.3
 */
public class TestJmxIntegration extends AbstractSolrTestCase {

  @Override
  public String getSchemaFile() {
    return "schema.xml";
  }

  @Override
  public String getSolrConfigFile() {
    return "solrconfig.xml";
  }

  @Override
  @Before
  public void setUp() throws Exception {
    // Make sure that at least one MBeanServer is available
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    super.setUp();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testJmxRegistration() throws Exception {
    List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
    log.info("Servers in testJmxRegistration: " + servers);
    assertNotNull("MBeanServers were null", servers);
    assertFalse("No MBeanServer was found", servers.isEmpty());

    MBeanServer mbeanServer = servers.get(0);
    assertTrue("No MBeans found in server", mbeanServer.getMBeanCount() > 0);

    Set<ObjectInstance> objects = mbeanServer.queryMBeans(null, null);
    assertFalse("No SolrInfoMBean objects found in mbean server", objects
            .isEmpty());
    for (ObjectInstance o : objects) {
      MBeanInfo mbeanInfo = mbeanServer.getMBeanInfo(o.getObjectName());
      if (mbeanInfo.getClassName().endsWith(SolrDynamicMBean.class.getName())) {
        assertTrue("No Attributes found for mbean: " + mbeanInfo, mbeanInfo
                .getAttributes().length > 0);
      }
    }
  }

  @Test
  public void testJmxUpdate() throws Exception {
    List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
    log.info("Servers in testJmxUpdate: " + servers);
    log.info(h.getCore().getInfoRegistry().toString());

    SolrInfoMBean bean = null;
    // wait until searcher is registered
    for (int i=0; i<100; i++) {
      bean = h.getCore().getInfoRegistry().get("searcher");
      if (bean != null) break;
      Thread.sleep(250);
    }
    if (bean==null) throw new RuntimeException("searcher was never registered");
    ObjectName searcher = getObjectName("searcher", bean);

    MBeanServer mbeanServer = servers.get(0);
    log.info("Mbeans in server: " + mbeanServer.queryNames(null, null));

    assertFalse("No mbean found for SolrIndexSearcher", mbeanServer.queryMBeans(searcher, null).isEmpty());

    int oldNumDocs = Integer.valueOf((String) mbeanServer.getAttribute(searcher, "numDocs"));
    assertU(adoc("id", "1"));
    assertU("commit", commit());
    int numDocs = Integer.valueOf((String) mbeanServer.getAttribute(searcher, "numDocs"));
    assertTrue("New numDocs is same as old numDocs as reported by JMX",
            numDocs > oldNumDocs);
  }

  @Test
  public void testJmxOnCoreReload() throws Exception {
    List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
    MBeanServer mbeanServer = servers.get(0);
    log.info("Servers in testJmxUpdate: " + servers);
    log.info(h.getCore().getInfoRegistry().toString());

    String coreName = h.getCore().getName();
    if (coreName.length() == 0) {
      coreName = h.getCoreContainer().getDefaultCoreName().length() > 0 ? h.getCoreContainer().getDefaultCoreName() : "";
    }

    Set<ObjectInstance> oldBeans = mbeanServer.queryMBeans(null, null);
    int oldNumberOfObjects = oldBeans.size();
    h.getCoreContainer().reload(coreName);

    Set<ObjectInstance> newBeans = mbeanServer.queryMBeans(null, null);
    int newNumberOfObjects = newBeans.size();

    assertEquals("Number of registered MBeans is not the same after Solr core reload", oldNumberOfObjects, newNumberOfObjects);
  }

  private ObjectName getObjectName(String key, SolrInfoMBean infoBean)
          throws MalformedObjectNameException {
    Hashtable<String, String> map = new Hashtable<String, String>();
    map.put("type", key);
    map.put("id", infoBean.getName());
    return ObjectName.getInstance("solr", map);
  }
}

