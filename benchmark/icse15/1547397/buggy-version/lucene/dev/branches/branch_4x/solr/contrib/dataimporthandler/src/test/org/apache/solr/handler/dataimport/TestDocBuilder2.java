  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1547394
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
package org.apache.solr.handler.dataimport;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.solr.request.LocalSolrQueryRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * <p>
 * Test for DocBuilder using the test harness
 * </p>
 *
 *
 * @since solr 1.3
 */
public class TestDocBuilder2 extends AbstractDataImportHandlerTestCase {

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("dataimport-solrconfig.xml", "dataimport-schema.xml");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSingleEntity() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "1", "desc", "one"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(loadDataConfig("single-entity-data-config.xml"));

    assertQ(req("id:1"), "//*[@numFound='1']");
    
    assertTrue("Update request processor processAdd was not called", TestUpdateRequestProcessor.processAddCalled);
    assertTrue("Update request processor processCommit was not callled", TestUpdateRequestProcessor.processCommitCalled);
    assertTrue("Update request processor finish was not called", TestUpdateRequestProcessor.finishCalled);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSingleEntity_CaseInsensitive() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "1", "desC", "one"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(dataConfigWithCaseInsensitiveFields);

    assertQ(req("id:1"), "//*[@numFound='1']");
    assertTrue("Start event listener was not called", StartEventListener.executed);
    assertTrue("End event listener was not called", EndEventListener.executed);
    assertTrue("Update request processor processAdd was not called", TestUpdateRequestProcessor.processAddCalled);
    assertTrue("Update request processor finish was not called", TestUpdateRequestProcessor.finishCalled);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDynamicFields() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "1", "desc", "one"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(dataConfigWithDynamicTransformer);

    assertQ(req("id:1"), "//*[@numFound='1']");
    assertQ(req("dynamic_s:test"), "//*[@numFound='1']");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRequestParamsAsVariable() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "101", "desc", "ApacheSolr"));
    MockDataSource.setIterator("select * from books where category='search'", rows.iterator());

    LocalSolrQueryRequest request = lrf.makeRequest("command", "full-import",
            "debug", "on", "clean", "true", "commit", "true",
            "category", "search",
            "dataConfig", requestParamAsVariable);
    h.query("/dataimport", request);
    assertQ(req("desc:ApacheSolr"), "//*[@numFound='1']");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRequestParamsAsFieldName() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("mypk", "101", "text", "ApacheSolr"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    LocalSolrQueryRequest request = lrf.makeRequest("command", "full-import",
            "debug", "on", "clean", "true", "commit", "true",
            "mypk", "id", "text", "desc",
            "dataConfig", dataConfigWithTemplatizedFieldNames);
    h.query("/dataimport", request);
    assertQ(req("id:101"), "//*[@numFound='1']");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testContext() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "1", "desc", "one"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(loadDataConfig("data-config-with-transformer.xml"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSkipDoc() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "1", "desc", "one"));
    rows.add(createMap("id", "2", "desc", "two", "$skipDoc", "true"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(dataConfigWithDynamicTransformer);

    assertQ(req("id:1"), "//*[@numFound='1']");
    assertQ(req("id:2"), "//*[@numFound='0']");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSkipRow() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "1", "desc", "one"));
    rows.add(createMap("id", "2", "desc", "two", "$skipRow", "true"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(dataConfigWithDynamicTransformer);

    assertQ(req("id:1"), "//*[@numFound='1']");
    assertQ(req("id:2"), "//*[@numFound='0']");

    MockDataSource.clearCache();

    rows = new ArrayList();
    rows.add(createMap("id", "3", "desc", "one"));
    rows.add(createMap("id", "4", "desc", "two"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    rows = new ArrayList();
    rows.add(createMap("name_s", "abcd"));
    MockDataSource.setIterator("3", rows.iterator());

    rows = new ArrayList();
    rows.add(createMap("name_s", "xyz", "$skipRow", "true"));
    MockDataSource.setIterator("4", rows.iterator());

    runFullImport(dataConfigWithTwoEntities);
    assertQ(req("id:3"), "//*[@numFound='1']");
    assertQ(req("id:4"), "//*[@numFound='1']");
    assertQ(req("name_s:abcd"), "//*[@numFound='1']");
    assertQ(req("name_s:xyz"), "//*[@numFound='0']");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testStopTransform() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "1", "desc", "one"));
    rows.add(createMap("id", "2", "desc", "two", "$stopTransform", "true"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(dataConfigForSkipTransform);

    assertQ(req("id:1"), "//*[@numFound='1']");
    assertQ(req("id:2"), "//*[@numFound='1']");
    assertQ(req("name_s:xyz"), "//*[@numFound='1']");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDeleteDocs() throws Exception {
    List rows = new ArrayList();
    rows.add(createMap("id", "1", "desc", "one"));
    rows.add(createMap("id", "2", "desc", "two"));
    rows.add(createMap("id", "3", "desc", "two", "$deleteDocById", "2"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(dataConfigForSkipTransform);

    assertQ(req("id:1"), "//*[@numFound='1']");
    assertQ(req("id:2"), "//*[@numFound='0']");
    assertQ(req("id:3"), "//*[@numFound='1']");

    assertTrue("Update request processor processDelete was not called", TestUpdateRequestProcessor.processDeleteCalled);
    assertTrue("Update request processor finish was not called", TestUpdateRequestProcessor.finishCalled);
    
    MockDataSource.clearCache();
    rows = new ArrayList();
    rows.add(createMap("id", "1", "desc", "one"));
    rows.add(createMap("id", "2", "desc", "one"));
    rows.add(createMap("id", "3", "desc", "two", "$deleteDocByQuery", "desc:one"));
    MockDataSource.setIterator("select * from x", rows.iterator());

    runFullImport(dataConfigForSkipTransform);

    assertQ(req("id:1"), "//*[@numFound='0']");
    assertQ(req("id:2"), "//*[@numFound='0']");
    assertQ(req("id:3"), "//*[@numFound='1']");
    
    assertTrue("Update request processor processDelete was not called", TestUpdateRequestProcessor.processDeleteCalled);
    assertTrue("Update request processor finish was not called", TestUpdateRequestProcessor.finishCalled);
    
    MockDataSource.clearCache();
    rows = new ArrayList();
    rows.add(createMap("$deleteDocById", "3"));
    MockDataSource.setIterator("select * from x", rows.iterator());
    runFullImport(dataConfigForSkipTransform, createMap("clean","false"));
    assertQ(req("id:3"), "//*[@numFound='0']");
    
    assertTrue("Update request processor processDelete was not called", TestUpdateRequestProcessor.processDeleteCalled);
    assertTrue("Update request processor finish was not called", TestUpdateRequestProcessor.finishCalled);
    
  }

  @Test
  @Ignore("Fix Me. See SOLR-4103.")
  public void testFileListEntityProcessor_lastIndexTime() throws Exception  {
    File tmpdir = File.createTempFile("test", "tmp", TEMP_DIR);
    tmpdir.delete();
    tmpdir.mkdir();
    tmpdir.deleteOnExit();

    Map<String, String> params = createMap("baseDir", tmpdir.getAbsolutePath());

    createFile(tmpdir, "a.xml", "a.xml".getBytes("UTF-8"), true);
    createFile(tmpdir, "b.xml", "b.xml".getBytes("UTF-8"), true);
    createFile(tmpdir, "c.props", "c.props".getBytes("UTF-8"), true);
    runFullImport(dataConfigFileList, params);
    assertQ(req("*:*"), "//*[@numFound='3']");

    // Add a new file after a full index is done
    createFile(tmpdir, "t.xml", "t.xml".getBytes("UTF-8"), false);
    runFullImport(dataConfigFileList, params);
    // we should find only 1 because by default clean=true is passed
    // and this particular import should find only one file t.xml
    assertQ(req("*:*"), "//*[@numFound='1']");
  }

  public static class MockTransformer extends Transformer {
    @Override
    public Object transformRow(Map<String, Object> row, Context context) {
      assertTrue("Context gave incorrect data source", context.getDataSource("mockDs") instanceof MockDataSource2);
      return row;
    }
  }

  public static class AddDynamicFieldTransformer extends Transformer  {
    @Override
    public Object transformRow(Map<String, Object> row, Context context) {
      // Add a dynamic field
      row.put("dynamic_s", "test");
      return row;
    }
  }

  public static class MockDataSource2 extends MockDataSource  {

  }

  public static class StartEventListener implements EventListener {
    public static boolean executed = false;

    @Override
    public void onEvent(Context ctx) {
      executed = true;
    }
  }

  public static class EndEventListener implements EventListener {
    public static boolean executed = false;

    @Override
    public void onEvent(Context ctx) {
      executed = true;
    }
  }

  private final String requestParamAsVariable = "<dataConfig>\n" +
          "    <dataSource type=\"MockDataSource\" />\n" +
          "    <document>\n" +
          "        <entity name=\"books\" query=\"select * from books where category='${dataimporter.request.category}'\">\n" +
          "            <field column=\"id\" />\n" +
          "            <field column=\"desc\" />\n" +
          "        </entity>\n" +
          "    </document>\n" +
          "</dataConfig>";

   private final String dataConfigWithDynamicTransformer = "<dataConfig> <dataSource type=\"MockDataSource\"/>\n" +
          "    <document>\n" +
          "        <entity name=\"books\" query=\"select * from x\"" +
           "                transformer=\"TestDocBuilder2$AddDynamicFieldTransformer\">\n" +
          "            <field column=\"id\" />\n" +
          "            <field column=\"desc\" />\n" +
          "        </entity>\n" +
          "    </document>\n" +
          "</dataConfig>";

  private final String dataConfigForSkipTransform = "<dataConfig> <dataSource  type=\"MockDataSource\"/>\n" +
          "    <document>\n" +
          "        <entity name=\"books\" query=\"select * from x\"" +
           "                transformer=\"TemplateTransformer\">\n" +
          "            <field column=\"id\" />\n" +
          "            <field column=\"desc\" />\n" +
          "            <field column=\"name_s\" template=\"xyz\" />\n" +
          "        </entity>\n" +
          "    </document>\n" +
          "</dataConfig>";

  private final String dataConfigWithTwoEntities = "<dataConfig><dataSource type=\"MockDataSource\"/>\n" +
          "    <document>\n" +
          "        <entity name=\"books\" query=\"select * from x\">" +
          "            <field column=\"id\" />\n" +
          "            <field column=\"desc\" />\n" +
          "            <entity name=\"authors\" query=\"${books.id}\">" +
          "               <field column=\"name_s\" />" +
          "            </entity>" +
          "        </entity>\n" +
          "    </document>\n" +
          "</dataConfig>";

  private final String dataConfigWithCaseInsensitiveFields = "<dataConfig> <dataSource  type=\"MockDataSource\"/>\n" +
          "    <document onImportStart=\"TestDocBuilder2$StartEventListener\" onImportEnd=\"TestDocBuilder2$EndEventListener\">\n" +
          "        <entity name=\"books\" query=\"select * from x\">\n" +
          "            <field column=\"ID\" />\n" +
          "            <field column=\"Desc\" />\n" +
          "        </entity>\n" +
          "    </document>\n" +
          "</dataConfig>";

  private final String dataConfigWithTemplatizedFieldNames = "<dataConfig><dataSource  type=\"MockDataSource\"/>\n" +
          "    <document>\n" +
          "        <entity name=\"books\" query=\"select * from x\">\n" +
          "            <field column=\"mypk\" name=\"${dih.request.mypk}\" />\n" +
          "            <field column=\"text\" name=\"${dih.request.text}\" />\n" +
          "        </entity>\n" +
          "    </document>\n" +
          "</dataConfig>";

  private final String dataConfigFileList = "<dataConfig>\n" +
          "\t<document>\n" +
          "\t\t<entity name=\"x\" processor=\"FileListEntityProcessor\" \n" +
          "\t\t\t\tfileName=\".*\" newerThan=\"${dih.last_index_time}\" \n" +
          "\t\t\t\tbaseDir=\"${dih.request.baseDir}\" transformer=\"TemplateTransformer\">\n" +
          "\t\t\t<field column=\"id\" template=\"${x.file}\" />\n" +
          "\t\t</entity>\n" +
          "\t</document>\n" +
          "</dataConfig>";
}
