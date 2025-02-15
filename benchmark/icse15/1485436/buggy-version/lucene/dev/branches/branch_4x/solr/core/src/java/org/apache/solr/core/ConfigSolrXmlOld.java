  Merged /lucene/dev/trunk/solr/NOTICE.txt:r1485403
  Merged /lucene/dev/trunk/solr/contrib:r1485403
  Merged /lucene/dev/trunk/solr/LICENSE.txt:r1485403
  Merged /lucene/dev/trunk/solr/site:r1485403
  Merged /lucene/dev/trunk/solr/SYSTEM_REQUIREMENTS.txt:r1485403
  Merged /lucene/dev/trunk/solr/licenses/httpmime-NOTICE.txt:r1485403
  Merged /lucene/dev/trunk/solr/licenses/httpmime-LICENSE-ASL.txt:r1485403
  Merged /lucene/dev/trunk/solr/licenses/httpcore-LICENSE-ASL.txt:r1485403
  Merged /lucene/dev/trunk/solr/licenses/httpclient-NOTICE.txt:r1485403
  Merged /lucene/dev/trunk/solr/licenses/httpcore-NOTICE.txt:r1485403
  Merged /lucene/dev/trunk/solr/licenses/httpclient-LICENSE-ASL.txt:r1485403
  Merged /lucene/dev/trunk/solr/licenses:r1485403
  Merged /lucene/dev/trunk/solr/test-framework:r1485403
  Merged /lucene/dev/trunk/solr/README.txt:r1485403
  Merged /lucene/dev/trunk/solr/webapp:r1485403
  Merged /lucene/dev/trunk/solr/cloud-dev:r1485403
  Merged /lucene/dev/trunk/solr/common-build.xml:r1485403
  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1485403
  Merged /lucene/dev/trunk/solr/scripts:r1485403
  Merged /lucene/dev/trunk/solr/core/src/test/org/apache/solr/core/TestConfig.java:r1485403
package org.apache.solr.core;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.IOUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.util.DOMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 *
 */
public class ConfigSolrXmlOld extends ConfigSolr {
  protected static Logger log = LoggerFactory.getLogger(ConfigSolrXmlOld.class);

  private NodeList coreNodes = null;

  public ConfigSolrXmlOld(Config config, CoreContainer container)
      throws ParserConfigurationException, IOException, SAXException {

    super(config);
    checkForIllegalConfig(container);
    
    fillPropMap();
    initCoreList(container);
  }
  
  private void checkForIllegalConfig(CoreContainer container) throws IOException {
    // Do sanity checks - we don't want to find new style
    // config
    failIfFound("solr/str[@name='adminHandler']");
    failIfFound("solr/int[@name='coreLoadThreads']");
    failIfFound("solr/str[@name='coreRootDirectory']");
    failIfFound("solr/solrcloud/int[@name='distribUpdateConnTimeout']");
    failIfFound("solr/solrcloud/int[@name='distribUpdateSoTimeout']");
    failIfFound("solr/solrcloud/str[@name='host']");
    failIfFound("solr/solrcloud/str[@name='hostContext']");
    failIfFound("solr/solrcloud/int[@name='hostPort']");
    failIfFound("solr/solrcloud/int[@name='leaderVoteWait']");
    failIfFound("solr/str[@name='managementPath']");
    failIfFound("solr/str[@name='sharedLib']");
    failIfFound("solr/str[@name='shareSchema']");
    failIfFound("solr/int[@name='transientCacheSize']");
    failIfFound("solr/solrcloud/int[@name='zkClientTimeout']");
    failIfFound("solr/solrcloud/int[@name='zkHost']");
    
    failIfFound("solr/logging/str[@name='class']");
    failIfFound("solr/logging/str[@name='enabled']");
    
    failIfFound("solr/logging/watcher/int[@name='size']");
    failIfFound("solr/logging/watcher/int[@name='threshold']");
  }
  
  private void failIfFound(String xPath) {

    if (config.getVal(xPath, false) != null) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Should not have found " + xPath +
          " solr.xml may be a mix of old and new style formats.");
    }
  }
  
  private void fillPropMap() {
    
    propMap.put(CfgProp.SOLR_CORELOADTHREADS,
        config.getVal("solr/@coreLoadThreads", false));
    propMap
        .put(CfgProp.SOLR_SHAREDLIB, config.getVal("solr/@sharedLib", false));
    propMap.put(CfgProp.SOLR_ZKHOST, config.getVal("solr/@zkHost", false));
    
    propMap.put(CfgProp.SOLR_LOGGING_CLASS,
        config.getVal("solr/logging/@class", false));
    propMap.put(CfgProp.SOLR_LOGGING_ENABLED,
        config.getVal("solr/logging/@enabled", false));
    propMap.put(CfgProp.SOLR_LOGGING_WATCHER_SIZE,
        config.getVal("solr/logging/watcher/@size", false));
    propMap.put(CfgProp.SOLR_LOGGING_WATCHER_THRESHOLD,
        config.getVal("solr/logging/watcher/@threshold", false));
    
    propMap.put(CfgProp.SOLR_ADMINHANDLER,
        config.getVal("solr/cores/@adminHandler", false));
    propMap.put(CfgProp.SOLR_DISTRIBUPDATECONNTIMEOUT,
        config.getVal("solr/cores/@distribUpdateConnTimeout", false));
    propMap.put(CfgProp.SOLR_DISTRIBUPDATESOTIMEOUT,
        config.getVal("solr/cores/@distribUpdateSoTimeout", false));
    propMap.put(CfgProp.SOLR_HOST, config.getVal("solr/cores/@host", false));
    propMap.put(CfgProp.SOLR_HOSTCONTEXT,
        config.getVal("solr/cores/@hostContext", false));
    propMap.put(CfgProp.SOLR_HOSTPORT,
        config.getVal("solr/cores/@hostPort", false));
    propMap.put(CfgProp.SOLR_LEADERVOTEWAIT,
        config.getVal("solr/cores/@leaderVoteWait", false));
    propMap.put(CfgProp.SOLR_MANAGEMENTPATH,
        config.getVal("solr/cores/@managementPath", false));
    propMap.put(CfgProp.SOLR_SHARESCHEMA,
        config.getVal("solr/cores/@shareSchema", false));
    propMap.put(CfgProp.SOLR_TRANSIENTCACHESIZE,
        config.getVal("solr/cores/@transientCacheSize", false));
    propMap.put(CfgProp.SOLR_ZKCLIENTTIMEOUT,
        config.getVal("solr/cores/@zkClientTimeout", false));
    propMap.put(CfgProp.SOLR_SHARDHANDLERFACTORY_CLASS,
        config.getVal("solr/shardHandlerFactory/@class", false));
    propMap.put(CfgProp.SOLR_SHARDHANDLERFACTORY_NAME,
        config.getVal("solr/shardHandlerFactory/@name", false));
    propMap.put(CfgProp.SOLR_SHARDHANDLERFACTORY_CONNTIMEOUT,
        config.getVal("solr/shardHandlerFactory/int[@connTimeout]", false));
    propMap.put(CfgProp.SOLR_SHARDHANDLERFACTORY_SOCKETTIMEOUT,
        config.getVal("solr/shardHandlerFactory/int[@socketTimeout]", false));
    
    // These have no counterpart in 5.0, asking, for any of these in Solr 5.0
    // will result in an error being
    // thrown.
    propMap.put(CfgProp.SOLR_CORES_DEFAULT_CORE_NAME,
        config.getVal("solr/cores/@defaultCoreName", false));
    propMap.put(CfgProp.SOLR_PERSISTENT,
        config.getVal("solr/@persistent", false));
    propMap.put(CfgProp.SOLR_ADMINPATH,
        config.getVal("solr/cores/@adminPath", false));
    
  }

  private void initCoreList(CoreContainer container) throws IOException {
    
    coreNodes = (NodeList) config.evaluate("solr/cores/core",
        XPathConstants.NODESET);
    // Check a couple of error conditions
    Set<String> names = new HashSet<String>(); // for duplicate names
    Map<String,String> dirs = new HashMap<String,String>(); // for duplicate
                                                            // data dirs.
    
    for (int idx = 0; idx < coreNodes.getLength(); ++idx) {
      Node node = coreNodes.item(idx);
      String name = DOMUtil.getAttr(node, CoreDescriptor.CORE_NAME, null);
      String dataDir = DOMUtil.getAttr(node, CoreDescriptor.CORE_DATADIR, null);
      if (name != null) {
        if (!names.contains(name)) {
          names.add(name);
        } else {
          String msg = String.format(Locale.ROOT,
              "More than one core defined for core named %s", name);
          log.error(msg);
        }
      }

      String instDir = DOMUtil.getAttr(node, CoreDescriptor.CORE_INSTDIR, null);
      if (dataDir != null && instDir != null) { // this won't load anyway if instDir not specified.

        String absData = new File(instDir, dataDir).getCanonicalPath();
        if (!dirs.containsKey(absData)) {
          dirs.put(absData, name);
        } else {
          String msg = String
              .format(
                  Locale.ROOT,
                  "More than one core points to data dir %s. They are in %s and %s",
                  absData, dirs.get(absData), name);
          log.warn(msg);
        }
      }
    }
    
  }

  @Override
  public Map<String, String> readCoreAttributes(String coreName) {
    Map<String, String> attrs = new HashMap<String, String>();

    synchronized (coreNodes) {
      for (int idx = 0; idx < coreNodes.getLength(); ++idx) {
        Node node = coreNodes.item(idx);
        if (coreName.equals(DOMUtil.getAttr(node, CoreDescriptor.CORE_NAME, null))) {
          NamedNodeMap attributes = node.getAttributes();
          for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String val = attribute.getNodeValue();
            if (CoreDescriptor.CORE_DATADIR.equals(attribute.getNodeName()) ||
                CoreDescriptor.CORE_INSTDIR.equals(attribute.getNodeName())) {
              if (val.indexOf('$') == -1) {
                val = (val != null && !val.endsWith("/")) ? val + '/' : val;
              }
            }
            attrs.put(attribute.getNodeName(), val);
          }
          return attrs;
        }
      }
    }
    return attrs;
  }

  @Override
  public List<String> getAllCoreNames() {
    List<String> ret = new ArrayList<String>();
    
    synchronized (coreNodes) {
      for (int idx = 0; idx < coreNodes.getLength(); ++idx) {
        Node node = coreNodes.item(idx);
        ret.add(DOMUtil.getAttr(node, CoreDescriptor.CORE_NAME, null));
      }
    }
    
    return ret;
  }

  @Override
  public String getProperty(String coreName, String property, String defaultVal) {
    
    synchronized (coreNodes) {
      for (int idx = 0; idx < coreNodes.getLength(); ++idx) {
        Node node = coreNodes.item(idx);
        if (coreName.equals(DOMUtil.getAttr(node, CoreDescriptor.CORE_NAME,
            null))) {
          return DOMUtil.getAttr(node, property, defaultVal);
        }
      }
    }
    return defaultVal;
    
  }

  @Override
  public Properties readCoreProperties(String coreName) {
    
    synchronized (coreNodes) {
      for (int idx = 0; idx < coreNodes.getLength(); ++idx) {
        Node node = coreNodes.item(idx);
        if (coreName.equals(DOMUtil.getAttr(node, CoreDescriptor.CORE_NAME,
            null))) {
          try {
            return readProperties(node);
          } catch (XPathExpressionException e) {
            return null;
          }
        }
      }
    }
    
    return null;
  }

  static Properties getCoreProperties(String instanceDir, CoreDescriptor dcore) {
    String file = dcore.getPropertiesName();
    if (file == null) file = "conf" + File.separator + "solrcore.properties";
    File corePropsFile = new File(file);
    if (!corePropsFile.isAbsolute()) {
      corePropsFile = new File(instanceDir, file);
    }
    Properties p = dcore.getCoreProperties();
    if (corePropsFile.exists() && corePropsFile.isFile()) {
      p = new Properties(dcore.getCoreProperties());
      InputStream is = null;
      try {
        is = new FileInputStream(corePropsFile);
        p.load(is);
      } catch (IOException e) {
        log.warn("Error loading properties ", e);
      } finally {
        IOUtils.closeQuietly(is);
      }
    }
    return p;
  }

  public static final String DEF_SOLR_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
      + "<solr persistent=\"false\">\n"
      + "  <cores adminPath=\"/admin/cores\" defaultCoreName=\""
      + CoreContainer.DEFAULT_DEFAULT_CORE_NAME
      + "\""
      + " host=\"${host:}\" hostPort=\"${hostPort:}\" hostContext=\"${hostContext:}\" zkClientTimeout=\"${zkClientTimeout:15000}\""
      + ">\n"
      + "    <core name=\""
      + CoreContainer.DEFAULT_DEFAULT_CORE_NAME
      + "\" shard=\"${shard:}\" collection=\"${collection:}\" instanceDir=\"collection1\" />\n"
      + "  </cores>\n" + "</solr>";

  @Override
  public void substituteProperties() {
    config.substituteProperties();
  }

}
