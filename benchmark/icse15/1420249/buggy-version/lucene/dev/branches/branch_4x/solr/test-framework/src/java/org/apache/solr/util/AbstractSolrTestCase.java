  Merged /lucene/dev/trunk/solr/contrib:r1420195
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


package org.apache.solr.util;


import java.io.*;
import java.util.*;

import javax.xml.xpath.XPathExpressionException;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.QuickPatchThreadsFilter;
import org.apache.solr.SolrIgnoredThreadsFilter;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.*;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.XML;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import com.carrotsearch.randomizedtesting.rules.SystemPropertiesRestoreRule;

/**
 * An Abstract base class that makes writing Solr JUnit tests "easier"
 *
 * <p>
 * Test classes that subclass this need only specify the path to the
 * schema.xml file (:TODO: the solrconfig.xml as well) and write some
 * testMethods.  This class takes care of creating/destroying the index,
 * and provides several assert methods to assist you.
 * </p>
 *
 * @see #setUp
 * @see #tearDown
 */
@ThreadLeakFilters(defaultFilters = true, filters = {
    SolrIgnoredThreadsFilter.class,
    QuickPatchThreadsFilter.class
})
public abstract class AbstractSolrTestCase extends LuceneTestCase {
  protected SolrConfig solrConfig;

  /**
   * Harness initialized by initTestHarness.
   *
   * <p>
   * For use in test methods as needed.
   * </p>
   */
  protected TestHarness h;

  /**
   * LocalRequestFactory initialized by initTestHarness using sensible
   * defaults.
   *
   * <p>
   * For use in test methods as needed.
   * </p>
   */
  protected TestHarness.LocalRequestFactory lrf;
    
  /**
   * Subclasses must define this method to return the name of the
   * schema.xml they wish to use.
   */
  public abstract String getSchemaFile();
    
  /**
   * Subclasses must define this method to return the name of the
   * solrconfig.xml they wish to use.
   */
  public abstract String getSolrConfigFile();

  /**
   * Subclasses can override this to change a test's solr home
   * (default is in test-files)
   */
  public String getSolrHome() {
    return SolrTestCaseJ4.TEST_HOME();
  }
  
  @ClassRule
  public static TestRule solrClassRules = 
    RuleChain.outerRule(new SystemPropertiesRestoreRule())
             .around(new RevertDefaultThreadHandlerRule());

  @Rule
  public TestRule solrTestRules = 
    RuleChain.outerRule(new SystemPropertiesRestoreRule());
  
  @BeforeClass
  public static void beforeClassAbstractSolrTestCase() {
    SolrTestCaseJ4.startTrackingSearchers();
  }
  
  @AfterClass
  public static void afterClassAbstractSolrTestCase() {
    SolrTestCaseJ4.endTrackingSearchers();
  }
  
  /**
   * The directory used to story the index managed by the TestHarness h
   */
  protected File dataDir;

  public static Logger log = LoggerFactory.getLogger(AbstractSolrTestCase.class);

  private String factoryProp;

  /**
   * Initializes things your test might need
   *
   * <ul>
   * <li>Creates a dataDir in the "java.io.tmpdir"</li>
   * <li>initializes the TestHarness h using this data directory, and getSchemaPath()</li>
   * <li>initializes the LocalRequestFactory lrf using sensible defaults.</li>
   * </ul>
   */
  @Override
  public void setUp() throws Exception {
    super.setUp();
    log.info("####SETUP_START " + getTestName());
    ignoreException("ignore_exception");
    factoryProp = System.getProperty("solr.directoryFactory");
    if (factoryProp == null) {
      System.setProperty("solr.directoryFactory","solr.RAMDirectoryFactory");
    }
    dataDir = new File(TEMP_DIR,
            getClass().getName() + "-" + System.currentTimeMillis());
    dataDir.mkdirs();
    String configFile = getSolrConfigFile();
    System.setProperty("solr.solr.home", getSolrHome());
    if (configFile != null) {

      solrConfig = TestHarness.createConfig(getSolrHome(), getSolrConfigFile());
      h = new TestHarness( dataDir.getAbsolutePath(),
              solrConfig,
              getSchemaFile());
      lrf = h.getRequestFactory
              ("standard",0,20,CommonParams.VERSION,"2.2");
    }
    log.info("####SETUP_END " + getTestName());
  }

    /** Causes an exception matching the regex pattern to not be logged. */
  public static void ignoreException(String pattern) {
    if (SolrException.ignorePatterns == null)
      SolrException.ignorePatterns = new HashSet<String>();
    SolrException.ignorePatterns.add(pattern);
  }

  public static void resetExceptionIgnores() {
    SolrException.ignorePatterns = null;
    ignoreException("ignore_exception");  // always ignore "ignore_exception"
  }

  /** Subclasses that override setUp can optionally call this method
   * to log the fact that their setUp process has ended.
   */
  public void postSetUp() {
    log.info("####POSTSETUP " + getTestName());
  }


  /** Subclasses that override tearDown can optionally call this method
   * to log the fact that the tearDown process has started.  This is necessary
   * since subclasses will want to call super.tearDown() at the *end* of their
   * tearDown method.
   */
  public void preTearDown() {
    log.info("####PRETEARDOWN " + getTestName());      
  }

  /**
   * Shuts down the test harness, and makes the best attempt possible
   * to delete dataDir, unless the system property "solr.test.leavedatadir"
   * is set.
   */
  @Override
  public void tearDown() throws Exception {
    log.info("####TEARDOWN_START " + getTestName());
    if (factoryProp == null) {
      System.clearProperty("solr.directoryFactory");
    }

    if (h != null) { h.close(); }
    String skip = System.getProperty("solr.test.leavedatadir");
    if (null != skip && 0 != skip.trim().length()) {
      System.err.println("NOTE: per solr.test.leavedatadir, dataDir will not be removed: " + dataDir.getAbsolutePath());
    } else {
      if (!recurseDelete(dataDir)) {
        System.err.println("!!!! WARNING: best effort to remove " + dataDir.getAbsolutePath() + " FAILED !!!!!");
      }
    }

    resetExceptionIgnores();  
    super.tearDown();
  }

  /** Validates an update XML String is successful
   */
  public void assertU(String update) {
    assertU(null, update);
  }

  /** Validates an update XML String is successful
   */
  public void assertU(String message, String update) {
    checkUpdateU(message, update, true);
  }

  /** Validates an update XML String failed
   */
  public void assertFailedU(String update) {
    assertFailedU(null, update);
  }

  /** Validates an update XML String failed
   */
  public void assertFailedU(String message, String update) {
    checkUpdateU(message, update, false);
  }

  /** Checks the success or failure of an update message
   */
  private void checkUpdateU(String message, String update, boolean shouldSucceed) {
    try {
      String m = (null == message) ? "" : message + " ";
      if (shouldSucceed) {
           String res = h.validateUpdate(update);
         if (res != null) fail(m + "update was not successful: " + res);
      } else {
           String res = h.validateErrorUpdate(update);
         if (res != null) fail(m + "update succeeded, but should have failed: " + res);        
      }
    } catch (SAXException e) {
      throw new RuntimeException("Invalid XML", e);
    }
  }

  /** Validates a query matches some XPath test expressions and closes the query */
  public void assertQ(SolrQueryRequest req, String... tests) {
    assertQ(null, req, tests);
  }
  
  /** Validates a query matches some XPath test expressions and closes the query */
  public void assertQ(String message, SolrQueryRequest req, String... tests) {
    try {
      String m = (null == message) ? "" : message + " ";
      String response = h.query(req);
      String results = h.validateXPath(response, tests);
      if (null != results) {
        fail(m + "query failed XPath: " + results +
             "\n xml response was: " + response +
             "\n request was: " + req.getParamString());
      }
    } catch (XPathExpressionException e1) {
      throw new RuntimeException("XPath is invalid", e1);
    } catch (Exception e2) {
      throw new RuntimeException("Exception during query", e2);
    }
  }

  /** Makes sure a query throws a SolrException with the listed response code */
  public void assertQEx(String message, SolrQueryRequest req, int code ) {
    try {
      h.query(req);
      fail( message );
    } catch (SolrException sex) {
      assertEquals( code, sex.code() );
    } catch (Exception e2) {
      throw new RuntimeException("Exception during query", e2);
    }
  }

  public void assertQEx(String message, SolrQueryRequest req, SolrException.ErrorCode code ) {
    try {
      h.query(req);
      fail( message );
    } catch (SolrException e) {
      assertEquals( code.code, e.code() );
    } catch (Exception e2) {
      throw new RuntimeException("Exception during query", e2);
    }
  }

  
  /**
   * @see TestHarness#optimize
   */
  public String optimize(String... args) {
    return TestHarness.optimize(args);
  }
  /**
   * @see TestHarness#commit
   */
  public String commit(String... args) {
    return TestHarness.commit(args);
  }

  /**
   * Generates a simple &lt;add&gt;&lt;doc&gt;... XML String with no options
   *
   * @param fieldsAndValues 0th and Even numbered args are fields names odds are field values.
   * @see #add
   * @see #doc
   */
  public String adoc(String... fieldsAndValues) {
    Doc d = doc(fieldsAndValues);
    return add(d);
  }
  
  /**
   * Generates a simple &lt;add&gt;&lt;doc&gt;... XML String with the
   * commitWithin attribute.
   *
   * @param commitWithin the value of the commitWithin attribute 
   * @param fieldsAndValues 0th and Even numbered args are fields names odds are field values.
   * @see #add
   * @see #doc
   */
  public String adoc(int commitWithin, String... fieldsAndValues) {
    Doc d = doc(fieldsAndValues);
    return add(d, "commitWithin", String.valueOf(commitWithin));
  }

  /**
   * Generates a simple &lt;add&gt;&lt;doc&gt;... XML String with no options
   */
  public String adoc(SolrInputDocument sdoc) {
    List<String> fields = new ArrayList<String>();
    for (SolrInputField sf : sdoc) {
      for (Object o : sf.getValues()) {
        fields.add(sf.getName());
        fields.add(o.toString());
      }
    }
    return adoc(fields.toArray(new String[fields.size()]));
  }

    
  /**
   * Generates an &lt;add&gt;&lt;doc&gt;... XML String with options
   * on the add.
   *
   * @param doc the Document to add
   * @param args 0th and Even numbered args are param names, Odds are param values.
   * @see #add
   * @see #doc
   */
  public String add(Doc doc, String... args) {
    try {
      StringWriter r = new StringWriter();
            
      // this is anoying
      if (null == args || 0 == args.length) {
        r.write("<add>");
        r.write(doc.xml);
        r.write("</add>");
      } else {
        XML.writeUnescapedXML(r, "add", doc.xml, (Object[])args);
      }
            
      return r.getBuffer().toString();
    } catch (IOException e) {
      throw new RuntimeException
        ("this should never happen with a StringWriter", e);
    }
  }

  /**
   * Generates a &lt;delete&gt;... XML string for an ID
   *
   * @see TestHarness#deleteById
   */
  public String delI(String id, String... args) {
    return TestHarness.deleteById(id, args);
  }
  
  /**
   * Generates a &lt;delete&gt;... XML string for an query
   *
   * @see TestHarness#deleteByQuery
   */
  public String delQ(String q, String... args) {
    return TestHarness.deleteByQuery(q, args);
  }
  
  /**
   * Generates a simple &lt;doc&gt;... XML String with no options
   *
   * @param fieldsAndValues 0th and Even numbered args are fields names, Odds are field values.
   * @see TestHarness#makeSimpleDoc
   */
  public Doc doc(String... fieldsAndValues) {
    Doc d = new Doc();
    d.xml = TestHarness.makeSimpleDoc(fieldsAndValues).toString();
    return d;
  }

  /**
   * Generates a SolrQueryRequest using the LocalRequestFactory
   * @see #lrf
   */
  public SolrQueryRequest req(String... q) {
    return lrf.makeRequest(q);
  }

  /**
   * Generates a SolrQueryRequest using the LocalRequestFactory
   * @see #lrf
   */
  public SolrQueryRequest req(String[] params, String... moreParams) {
    String[] allParams = moreParams;
    if (params.length!=0) {
      int len = params.length + moreParams.length;
      allParams = new String[len];
      System.arraycopy(params,0,allParams,0,params.length);
      System.arraycopy(moreParams,0,allParams,params.length,moreParams.length);
    }

    return lrf.makeRequest(allParams);
  }

  /** Neccessary to make method signatures un-ambiguous */
  public static class Doc {
    public String xml;
    @Override
    public String toString() { return xml; }
  }

  public static boolean recurseDelete(File f) {
    if (f.isDirectory()) {
      for (File sub : f.listFiles()) {
        if (!recurseDelete(sub)) {
          System.err.println("!!!! WARNING: best effort to remove " + sub.getAbsolutePath() + " FAILED !!!!!");
          return false;
        }
      }
    }
    return f.delete();
  }

  /** @see SolrTestCaseJ4#getFile */
  public static File getFile(String name) {
    return SolrTestCaseJ4.getFile(name);
  }
}
