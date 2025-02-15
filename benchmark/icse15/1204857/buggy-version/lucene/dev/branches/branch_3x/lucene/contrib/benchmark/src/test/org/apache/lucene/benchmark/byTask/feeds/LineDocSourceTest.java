package org.apache.lucene.benchmark.byTask.feeds;

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;

import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.benchmark.BenchmarkTestCase;
import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.feeds.LineDocSource.HeaderLineParser;
import org.apache.lucene.benchmark.byTask.feeds.LineDocSource.LineParser;
import org.apache.lucene.benchmark.byTask.tasks.AddDocTask;
import org.apache.lucene.benchmark.byTask.tasks.CloseIndexTask;
import org.apache.lucene.benchmark.byTask.tasks.CreateIndexTask;
import org.apache.lucene.benchmark.byTask.tasks.TaskSequence;
import org.apache.lucene.benchmark.byTask.tasks.WriteLineDocTask;
import org.apache.lucene.benchmark.byTask.utils.Config;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

/** Tests the functionality of {@link LineDocSource}. */
public class LineDocSourceTest extends BenchmarkTestCase {

  private static final CompressorStreamFactory csFactory = new CompressorStreamFactory();

  private void createBZ2LineFile(File file, boolean addHeader) throws Exception {
    OutputStream out = new FileOutputStream(file);
    out = csFactory.createCompressorOutputStream("bzip2", out);
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "utf-8"));
    writeDocsToFile(writer, addHeader, null);
    writer.close();
  }

  private void writeDocsToFile(BufferedWriter writer, boolean addHeader, Properties otherFields) throws IOException {
    if (addHeader) {
      writer.write(WriteLineDocTask.FIELDS_HEADER_INDICATOR);
      writer.write(WriteLineDocTask.SEP);
      writer.write(DocMaker.TITLE_FIELD);
      writer.write(WriteLineDocTask.SEP);
      writer.write(DocMaker.DATE_FIELD);
      writer.write(WriteLineDocTask.SEP);
      writer.write(DocMaker.BODY_FIELD);
      if (otherFields!=null) {
        // additional field names in the header 
        for (Object fn : otherFields.keySet()) {
          writer.write(WriteLineDocTask.SEP);
          writer.write(fn.toString());
        }
      }
      writer.newLine();
    }
    StringBuilder doc = new StringBuilder();
    doc.append("title").append(WriteLineDocTask.SEP).append("date").append(WriteLineDocTask.SEP).append(DocMaker.BODY_FIELD);
    if (otherFields!=null) {
      // additional field values in the doc line 
      for (Object fv : otherFields.values()) {
        doc.append(WriteLineDocTask.SEP).append(fv.toString());
      }
    }
    writer.write(doc.toString());
    writer.newLine();
  }

  private void createRegularLineFile(File file, boolean addHeader) throws Exception {
    OutputStream out = new FileOutputStream(file);
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "utf-8"));
    writeDocsToFile(writer, addHeader, null);
    writer.close();
  }

  private void createRegularLineFileWithMoreFields(File file, String...extraFields) throws Exception {
    OutputStream out = new FileOutputStream(file);
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "utf-8"));
    Properties p = new Properties();
    for (String f : extraFields) {
      p.setProperty(f, f);
    }
    writeDocsToFile(writer, true, p);
    writer.close();
  }
  
  private void doIndexAndSearchTest(File file, Class<? extends LineParser> lineParserClass, String storedField) throws Exception {
    doIndexAndSearchTestWithRepeats(file, lineParserClass, 1, storedField); // no extra repetitions
    doIndexAndSearchTestWithRepeats(file, lineParserClass, 2, storedField); // 1 extra repetition
    doIndexAndSearchTestWithRepeats(file, lineParserClass, 4, storedField); // 3 extra repetitions
  }
  
  private void doIndexAndSearchTestWithRepeats(File file, 
      Class<? extends LineParser> lineParserClass, int numAdds, String storedField) throws Exception {

    Properties props = new Properties();
    
    // LineDocSource specific settings.
    props.setProperty("docs.file", file.getAbsolutePath());
    if (lineParserClass != null) {
      props.setProperty("line.parser", lineParserClass.getName());
    }
    
    // Indexing configuration.
    props.setProperty("analyzer", WhitespaceAnalyzer.class.getName());
    props.setProperty("content.source", LineDocSource.class.getName());
    props.setProperty("directory", "RAMDirectory");
    props.setProperty("doc.stored", "true");
    props.setProperty("doc.index.props", "true");
    
    // Create PerfRunData
    Config config = new Config(props);
    PerfRunData runData = new PerfRunData(config);

    TaskSequence tasks = new TaskSequence(runData, "testBzip2", null, false);
    tasks.addTask(new CreateIndexTask(runData));
    for (int i=0; i<numAdds; i++) {
      tasks.addTask(new AddDocTask(runData));
    }
    tasks.addTask(new CloseIndexTask(runData));
    tasks.doLogic();
    tasks.close();
    
    IndexReader r = runData.getIndexReader();
    if (r == null) {
      r = IndexReader.open(runData.getDirectory(), true);
    }
    IndexSearcher searcher = new IndexSearcher(r);
    TopDocs td = searcher.search(new TermQuery(new Term("body", "body")), 10);
    assertEquals(numAdds, td.totalHits);
    assertNotNull(td.scoreDocs[0]);
    
    if (storedField==null) {
      storedField = DocMaker.BODY_FIELD; // added to all docs and satisfies field-name == value
    }
    assertEquals("Wrong field value", storedField, searcher.doc(0).get(storedField));

    searcher.close();
    r.close();
  }
  
  /* Tests LineDocSource with a bzip2 input stream. */
  public void testBZip2() throws Exception {
    File file = new File(getWorkDir(), "one-line.bz2");
    createBZ2LineFile(file,true);
    doIndexAndSearchTest(file, null, null);
  }

  public void testBZip2NoHeaderLine() throws Exception {
    File file = new File(getWorkDir(), "one-line.bz2");
    createBZ2LineFile(file,false);
    doIndexAndSearchTest(file, null, null);
  }
  
  public void testRegularFile() throws Exception {
    File file = new File(getWorkDir(), "one-line");
    createRegularLineFile(file,true);
    doIndexAndSearchTest(file, null, null);
  }

  public void testRegularFileSpecialHeader() throws Exception {
    File file = new File(getWorkDir(), "one-line");
    createRegularLineFile(file,true);
    doIndexAndSearchTest(file, HeaderLineParser.class, null);
  }

  public void testRegularFileNoHeaderLine() throws Exception {
    File file = new File(getWorkDir(), "one-line");
    createRegularLineFile(file,false);
    doIndexAndSearchTest(file, null, null);
  }

  public void testInvalidFormat() throws Exception {
    String[] testCases = new String[] {
      "", // empty line
      "title", // just title
      "title" + WriteLineDocTask.SEP, // title + SEP
      "title" + WriteLineDocTask.SEP + "body", // title + SEP + body
      // note that title + SEP + body + SEP is a valid line, which results in an
      // empty body
    };
    
    for (int i = 0; i < testCases.length; i++) {
      File file = new File(getWorkDir(), "one-line");
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
      writer.write(testCases[i]);
      writer.newLine();
      writer.close();
      try {
        doIndexAndSearchTest(file, null, null);
        fail("Some exception should have been thrown for: [" + testCases[i] + "]");
      } catch (Exception e) {
        // expected.
      }
    }
  }
  
  /** Doc Name is not part of the default header */
  public void testWithDocsName()  throws Exception {
    File file = new File(getWorkDir(), "one-line");
    createRegularLineFileWithMoreFields(file, DocMaker.NAME_FIELD);
    doIndexAndSearchTest(file, null, DocMaker.NAME_FIELD);
  }

  /** Use fields names that are not defined in Docmaker and so will go to Properties */
  public void testWithProperties()  throws Exception {
    File file = new File(getWorkDir(), "one-line");
    String specialField = "mySpecialField";
    createRegularLineFileWithMoreFields(file, specialField);
    doIndexAndSearchTest(file, null, specialField);
  }
  
}
