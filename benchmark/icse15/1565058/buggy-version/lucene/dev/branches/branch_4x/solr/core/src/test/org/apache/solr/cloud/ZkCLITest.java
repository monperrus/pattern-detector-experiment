  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1565056
package org.apache.solr.cloud;

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
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.util.ExternalPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: This test would be a lot faster if it used a solrhome with fewer config
// files - there are a lot of them to upload
public class ZkCLITest extends SolrTestCaseJ4 {
  protected static Logger log = LoggerFactory
      .getLogger(AbstractZkTestCase.class);
  
  private static final boolean VERBOSE = false;
  
  protected ZkTestServer zkServer;
  
  protected String zkDir;
  
  private String solrHome;

  private SolrZkClient zkClient;

  protected static final String SOLR_HOME = SolrTestCaseJ4.TEST_HOME();
  
  @BeforeClass
  public static void beforeClass() {
    System.setProperty("solrcloud.skip.autorecovery", "true");
  }
  
  @AfterClass
  public static void afterClass() throws InterruptedException {
    System.clearProperty("solrcloud.skip.autorecovery");
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    log.info("####SETUP_START " + getTestName());
    createTempDir();
    
    boolean useNewSolrXml = random().nextBoolean();
    
    if (useNewSolrXml) {
      solrHome = ExternalPaths.EXAMPLE_HOME;
    } else {
      File tmpSolrHome = new File(dataDir, "tmp-solr-home");
      FileUtils.copyDirectory(new File(ExternalPaths.EXAMPLE_HOME), tmpSolrHome);
      FileUtils.copyFile(new File(ExternalPaths.SOURCE_HOME, "core/src/test-files/old-solr-example/solr.xml"), new File(tmpSolrHome, "solr.xml"));
      solrHome = tmpSolrHome.getAbsolutePath();
    }
    
    
    zkDir = dataDir.getAbsolutePath() + File.separator
        + "zookeeper/server1/data";
    log.info("ZooKeeper dataDir:" + zkDir);
    zkServer = new ZkTestServer(zkDir);
    zkServer.run();
    System.setProperty("zkHost", zkServer.getZkAddress());
    SolrZkClient zkClient = new SolrZkClient(zkServer.getZkHost(), AbstractZkTestCase.TIMEOUT);
    zkClient.makePath("/solr", false, true);
    zkClient.close();

    
    this.zkClient = new SolrZkClient(zkServer.getZkAddress(),
        AbstractZkTestCase.TIMEOUT);
    
    log.info("####SETUP_END " + getTestName());
  }
  
  @Test
  public void testBootstrap() throws Exception {
    // test bootstrap_conf
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "bootstrap", "-solrhome", this.solrHome};
    ZkCLI.main(args);
    
    assertTrue(zkClient.exists(ZkController.CONFIGS_ZKNODE + "/collection1", true));
    
    args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "bootstrap", "-solrhome", ExternalPaths.EXAMPLE_MULTICORE_HOME};
    ZkCLI.main(args);
    
    assertTrue(zkClient.exists(ZkController.CONFIGS_ZKNODE + "/core0", true));
    assertTrue(zkClient.exists(ZkController.CONFIGS_ZKNODE + "/core1", true));
  }
  
  @Test
  public void testBootstrapWithChroot() throws Exception {
    String chroot = "/foo/bar";
    assertFalse(zkClient.exists(chroot, true));
    
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress() + chroot,
        "-cmd", "bootstrap", "-solrhome", this.solrHome};
    
    ZkCLI.main(args);
    
    assertTrue(zkClient.exists(chroot + ZkController.CONFIGS_ZKNODE
        + "/collection1", true));
  }

  @Test
  public void testMakePath() throws Exception {
    // test bootstrap_conf
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "makepath", "/path/mynewpath"};
    ZkCLI.main(args);


    assertTrue(zkClient.exists("/path/mynewpath", true));
  }

  @Test
  public void testPut() throws Exception {
    // test put
    String data = "my data";
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "put", "/data.txt", data};
    ZkCLI.main(args);

    zkClient.getData("/data.txt", null, null, true);

    assertArrayEquals(zkClient.getData("/data.txt", null, null, true), data.getBytes("UTF-8"));
  }

  @Test
  public void testPutFile() throws Exception {
    // test put file
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "putfile", "/solr.xml", SOLR_HOME + File.separator + "solr-stress-new.xml"};
    ZkCLI.main(args);

    String fromZk = new String(zkClient.getData("/solr.xml", null, null, true), "UTF-8");
    File locFile = new File(SOLR_HOME + File.separator + "solr-stress-new.xml");
    InputStream is = new FileInputStream(locFile);
    String fromLoc;
    try {
      fromLoc = new String(IOUtils.toByteArray(is), "UTF-8");
    } finally {
      IOUtils.closeQuietly(is);
    }
    assertEquals("Should get back what we put in ZK", fromZk, fromLoc);
  }

  @Test
  public void testPutFileNotExists() throws Exception {
    // test put file
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "putfile", "/solr.xml", SOLR_HOME + File.separator + "not-there.xml"};
    try {
      ZkCLI.main(args);
      fail("Should have had a file not found exception");
    } catch (FileNotFoundException fne) {
      String msg = fne.getMessage();
      assertTrue("Didn't find expected error message containing 'not-there.xml' in " + msg,
          msg.indexOf("not-there.xml") != -1);
    }
  }

  @Test
  public void testList() throws Exception {
    zkClient.makePath("/test", true);
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "list"};
    ZkCLI.main(args);
  }
  
  @Test
  public void testUpConfigLinkConfigClearZk() throws Exception {
    // test upconfig
    String confsetname = "confsetone";
    String[] args = new String[] {
        "-zkhost",
        zkServer.getZkAddress(),
        "-cmd",
        "upconfig",
        "-confdir",
        ExternalPaths.EXAMPLE_HOME + File.separator + "collection1"
            + File.separator + "conf", "-confname", confsetname};
    ZkCLI.main(args);
    
    assertTrue(zkClient.exists(ZkController.CONFIGS_ZKNODE + "/" + confsetname, true));

    // print help
    // ZkCLI.main(new String[0]);
    
    // test linkconfig
    args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "linkconfig", "-collection", "collection1", "-confname", confsetname};
    ZkCLI.main(args);
    
    ZkNodeProps collectionProps = ZkNodeProps.load(zkClient.getData(ZkStateReader.COLLECTIONS_ZKNODE + "/collection1", null, null, true));
    assertTrue(collectionProps.containsKey("configName"));
    assertEquals(confsetname, collectionProps.getStr("configName"));
    
    // test down config
    File confDir = new File(TEMP_DIR,
        "solrtest-confdropspot-" + this.getClass().getName() + "-" + System.currentTimeMillis());
    
    assertFalse(confDir.exists());
    
    args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "downconfig", "-confdir", confDir.getAbsolutePath(), "-confname", confsetname};
    ZkCLI.main(args);
    
    File[] files = confDir.listFiles();
    List<String> zkFiles = zkClient.getChildren(ZkController.CONFIGS_ZKNODE + "/" + confsetname, null, true);
    assertEquals(files.length, zkFiles.size());
    
    File sourceConfDir = new File(ExternalPaths.EXAMPLE_HOME + File.separator + "collection1"
            + File.separator + "conf");
    // filter out all directories starting with . (e.g. .svn)
    Collection<File> sourceFiles = FileUtils.listFiles(sourceConfDir, TrueFileFilter.INSTANCE, new RegexFileFilter("[^\\.].*"));
    for (File sourceFile :sourceFiles){
        int indexOfRelativePath = sourceFile.getAbsolutePath().lastIndexOf("collection1" + File.separator + "conf");
        String relativePathofFile = sourceFile.getAbsolutePath().substring(indexOfRelativePath + 17, sourceFile.getAbsolutePath().length());
        File downloadedFile = new File(confDir,relativePathofFile);
        assertTrue(downloadedFile.getAbsolutePath() + " does not exist source:" + sourceFile.getAbsolutePath(), downloadedFile.exists());
        assertTrue("Content didn't change",FileUtils.contentEquals(sourceFile,downloadedFile));
    }
    
   
    // test reset zk
    args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "clear", "/"};
    ZkCLI.main(args);

    assertEquals(0, zkClient.getChildren("/", null, true).size());
  }
  
  @Test
  public void testGet() throws Exception {
    String getNode = "/getNode";
    byte [] data = new String("getNode-data").getBytes("UTF-8");
    this.zkClient.create(getNode, data, CreateMode.PERSISTENT, true);
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "get", getNode};
    ZkCLI.main(args);
  }

  @Test
  public void testGetFile() throws Exception {
    String getNode = "/getFileNode";
    byte [] data = new String("getFileNode-data").getBytes("UTF-8");
    this.zkClient.create(getNode, data, CreateMode.PERSISTENT, true);

    File file = new File(TEMP_DIR,
        "solrtest-getfile-" + this.getClass().getName() + "-" + System.currentTimeMillis());
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "getfile", getNode, file.getAbsolutePath()};
    ZkCLI.main(args);

    byte [] readData = FileUtils.readFileToByteArray(file);
    assertArrayEquals(data, readData);
  }

  @Test
  public void testGetFileNotExists() throws Exception {
    String getNode = "/getFileNotExistsNode";

    File file = new File(TEMP_DIR,
        "solrtest-getfilenotexists-" + this.getClass().getName() + "-" + System.currentTimeMillis());
    String[] args = new String[] {"-zkhost", zkServer.getZkAddress(), "-cmd",
        "getfile", getNode, file.getAbsolutePath()};
    try {
      ZkCLI.main(args);
      fail("Expected NoNodeException");
    } catch (KeeperException.NoNodeException ex) {
    }
  }

  @Override
  public void tearDown() throws Exception {
    if (VERBOSE) {
      printLayout(zkServer.getZkHost());
    }
    zkClient.close();
    zkServer.shutdown();
    super.tearDown();
  }
  
  private void printLayout(String zkHost) throws Exception {
    SolrZkClient zkClient = new SolrZkClient(zkHost, AbstractZkTestCase.TIMEOUT);
    zkClient.printLayoutToStdOut();
    zkClient.close();
  }
}
