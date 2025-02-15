  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1523725
  Merged /lucene/dev/trunk/solr/scripts:r1523725
package org.apache.solr.util;

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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.util.SimplePostTool.PageFetcher;
import org.apache.solr.util.SimplePostTool.PageFetcherResult;
import org.junit.Before;
import org.junit.Test;

public class SimplePostToolTest extends SolrTestCaseJ4 {
  SimplePostTool t_file, t_file_auto, t_file_rec, t_web, t_test;
  PageFetcher pf;
  
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    String[] args = {"-"};
    System.setProperty("data", "files");
    t_file = SimplePostTool.parseArgsAndInit(args);

    System.setProperty("auto", "yes");
    t_file_auto = SimplePostTool.parseArgsAndInit(args);

    System.setProperty("recursive", "yes");
    t_file_rec = SimplePostTool.parseArgsAndInit(args);
    
    System.setProperty("data", "web");
    t_web = SimplePostTool.parseArgsAndInit(args);

    System.setProperty("params", "param1=foo&param2=bar");
    System.setProperty("url", "http://localhost:5150/solr/update");
    t_test = SimplePostTool.parseArgsAndInit(args);

    pf = new MockPageFetcher();
    SimplePostTool.pageFetcher = pf;
    SimplePostTool.mockMode = true;
  }
  
  @Test
  public void testParseArgsAndInit() {
    assertEquals(false, t_file.auto);
    assertEquals(true, t_file_auto.auto);
    assertEquals(0, t_file_auto.recursive);
    assertEquals(999, t_file_rec.recursive);
    assertEquals(true, t_file.commit);
    assertEquals(false, t_file.optimize);
    assertEquals(null, t_file.out);

    assertEquals(1, t_web.recursive);
    assertEquals(10, t_web.delay);
    
    assertEquals("http://localhost:5150/solr/update?param1=foo&param2=bar",t_test.solrUrl.toExternalForm());
  }
  
  @Test
  public void testNormalizeUrlEnding() {
    assertEquals("http://example.com", SimplePostTool.normalizeUrlEnding("http://example.com/"));
    assertEquals("http://example.com", SimplePostTool.normalizeUrlEnding("http://example.com/#foo?bar=baz"));
    assertEquals("http://example.com/index.html", SimplePostTool.normalizeUrlEnding("http://example.com/index.html#hello"));
  }
  
  @Test
  public void testComputeFullUrl() throws MalformedURLException {
    assertEquals("http://example.com/index.html", t_web.computeFullUrl(new URL("http://example.com/"), "/index.html"));
    assertEquals("http://example.com/index.html", t_web.computeFullUrl(new URL("http://example.com/foo/bar/"), "/index.html"));
    assertEquals("http://example.com/fil.html", t_web.computeFullUrl(new URL("http://example.com/foo.htm?baz#hello"), "fil.html"));
//    TODO: How to know what is the base if URL path ends with "foo"?? 
//    assertEquals("http://example.com/fil.html", t_web.computeFullUrl(new URL("http://example.com/foo?baz#hello"), "fil.html"));
    assertEquals(null, t_web.computeFullUrl(new URL("http://example.com/"), "fil.jpg"));
    assertEquals(null, t_web.computeFullUrl(new URL("http://example.com/"), "mailto:hello@foo.bar"));
    assertEquals(null, t_web.computeFullUrl(new URL("http://example.com/"), "ftp://server/file"));
  }
  
  @Test
  public void testTypeSupported() {
    assertTrue(t_web.typeSupported("application/pdf"));
    assertTrue(t_web.typeSupported("text/xml"));
    assertFalse(t_web.typeSupported("text/foo"));

    t_web.fileTypes = "doc,xls,ppt";
    t_web.globFileFilter = t_web.getFileFilterFromFileTypes(t_web.fileTypes);
    assertFalse(t_web.typeSupported("application/pdf"));
    assertTrue(t_web.typeSupported("application/msword"));
  }
  
  @Test
  public void testIsOn() {
    assertTrue(SimplePostTool.isOn("true"));
    assertTrue(SimplePostTool.isOn("1"));
    assertFalse(SimplePostTool.isOn("off"));
  }
  
  @Test
  public void testAppendParam() {
    assertEquals("http://example.com?foo=bar", SimplePostTool.appendParam("http://example.com", "foo=bar"));
    assertEquals("http://example.com/?a=b&foo=bar", SimplePostTool.appendParam("http://example.com/?a=b", "foo=bar"));
  }
  
  @Test
  public void testAppendUrlPath() throws MalformedURLException {
    assertEquals(new URL("http://example.com/a?foo=bar"), SimplePostTool.appendUrlPath(new URL("http://example.com?foo=bar"), "/a"));
  }
  
  @Test
  public void testGuessType() {
    File f = new File("foo.doc");
    assertEquals("application/msword", SimplePostTool.guessType(f));
    f = new File("foobar");
    assertEquals(null, SimplePostTool.guessType(f));
  }

  @Test
  public void testDoFilesMode() {
    t_file_auto.recursive = 0;
    File dir = getFile("exampledocs");
    int num = t_file_auto.postFiles(new File[] {dir}, 0, null, null);
    assertEquals(2, num);
  }

  @Test
  public void testDoWebMode() {
    // Uses mock pageFetcher
    t_web.delay = 0;
    t_web.recursive = 5;
    int num = t_web.postWebPages(new String[] {"http://example.com/#removeme"}, 0, null);
    assertEquals(5, num);
    
    t_web.recursive = 1;
    num = t_web.postWebPages(new String[] {"http://example.com/"}, 0, null);
    assertEquals(3, num);
    
    // Without respecting robots.txt
    SimplePostTool.pageFetcher.robotsCache.clear();
    t_web.recursive = 5;
    num = t_web.postWebPages(new String[] {"http://example.com/#removeme"}, 0, null);
    assertEquals(6, num);
}
  
  @Test
  public void testRobotsExclusion() throws MalformedURLException {
    assertFalse(SimplePostTool.pageFetcher.isDisallowedByRobots(new URL("http://example.com/")));
    assertTrue(SimplePostTool.pageFetcher.isDisallowedByRobots(new URL("http://example.com/disallowed")));
    assertTrue("There should be two entries parsed from robots.txt", SimplePostTool.pageFetcher.robotsCache.get("example.com").size() == 2);
  }

  class MockPageFetcher extends PageFetcher {
    HashMap<String,String> htmlMap = new HashMap<String,String>();
    HashMap<String,Set<URL>> linkMap = new HashMap<String,Set<URL>>();
    
    public MockPageFetcher() throws IOException {
      (new SimplePostTool()).super();
      htmlMap.put("http://example.com", "<html><body><a href=\"http://example.com/page1\">page1</a><a href=\"http://example.com/page2\">page2</a></body></html>");
      htmlMap.put("http://example.com/index.html", "<html><body><a href=\"http://example.com/page1\">page1</a><a href=\"http://example.com/page2\">page2</a></body></html>");
      htmlMap.put("http://example.com/page1", "<html><body><a href=\"http://example.com/page1/foo\"></body></html>");
      htmlMap.put("http://example.com/page1/foo", "<html><body><a href=\"http://example.com/page1/foo/bar\"></body></html>");
      htmlMap.put("http://example.com/page1/foo/bar", "<html><body><a href=\"http://example.com/page1\"></body></html>");
      htmlMap.put("http://example.com/page2", "<html><body><a href=\"http://example.com/\"><a href=\"http://example.com/disallowed\"/></body></html>");
      htmlMap.put("http://example.com/disallowed", "<html><body><a href=\"http://example.com/\"></body></html>");

      Set<URL> s = new HashSet<URL>();
      s.add(new URL("http://example.com/page1"));
      s.add(new URL("http://example.com/page2"));
      linkMap.put("http://example.com", s);
      linkMap.put("http://example.com/index.html", s);
      s = new HashSet<URL>();
      s.add(new URL("http://example.com/page1/foo"));
      linkMap.put("http://example.com/page1", s);
      s = new HashSet<URL>();
      s.add(new URL("http://example.com/page1/foo/bar"));
      linkMap.put("http://example.com/page1/foo", s);
      s = new HashSet<URL>();
      s.add(new URL("http://example.com/disallowed"));
      linkMap.put("http://example.com/page2", s);
      
      // Simulate a robots.txt file with comments and a few disallows
      StringBuilder sb = new StringBuilder();
      sb.append("# Comments appear after the \"#\" symbol at the start of a line, or after a directive\n");
      sb.append("User-agent: * # match all bots\n");
      sb.append("Disallow:  # This is void\n");
      sb.append("Disallow: /disallow # Disallow this path\n");
      sb.append("Disallow: /nonexistingpath # Disallow this path\n");
      this.robotsCache.put("example.com", SimplePostTool.pageFetcher.
          parseRobotsTxt(new ByteArrayInputStream(sb.toString().getBytes("UTF-8"))));
    }
    
    @Override
    public PageFetcherResult readPageFromUrl(URL u) {
      PageFetcherResult res = (new SimplePostTool()).new PageFetcherResult();
      if (isDisallowedByRobots(u)) {
        res.httpStatus = 403;
        return res;
      }
      res.httpStatus = 200;
      res.contentType = "text/html";
      try {
        res.content = htmlMap.get(u.toString()).getBytes("UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException();
      }
      return res;
    }
    
    @Override
    public Set<URL> getLinksFromWebPage(URL u, InputStream is, String type, URL postUrl) {
      Set<URL> s = linkMap.get(SimplePostTool.normalizeUrlEnding(u.toString()));
      if(s == null)
        s = new HashSet<URL>();
      return s;
    }
  }
}
