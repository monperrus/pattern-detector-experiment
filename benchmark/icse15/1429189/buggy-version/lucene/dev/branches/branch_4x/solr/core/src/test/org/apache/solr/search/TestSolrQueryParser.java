  Merged /lucene/dev/trunk/lucene/memory:r1429188
  Merged /lucene/dev/trunk/lucene/JRE_VERSION_MIGRATION.txt:r1429188
  Merged /lucene/dev/trunk/lucene/BUILD.txt:r1429188
  Merged /lucene/dev/trunk/lucene/suggest:r1429188
  Merged /lucene/dev/trunk/lucene/analysis/icu/src/java/org/apache/lucene/collation/ICUCollationKeyFilterFactory.java:r1429188
  Merged /lucene/dev/trunk/lucene/analysis:r1429188
  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1429188
  Merged /lucene/dev/trunk/lucene/grouping:r1429188
  Merged /lucene/dev/trunk/lucene/misc:r1429188
  Merged /lucene/dev/trunk/lucene/sandbox:r1429188
  Merged /lucene/dev/trunk/lucene/highlighter:r1429188
  Merged /lucene/dev/trunk/lucene/NOTICE.txt:r1429188
  Merged /lucene/dev/trunk/lucene/LICENSE.txt:r1429188
  Merged /lucene/dev/trunk/lucene/codecs:r1429188
  Merged /lucene/dev/trunk/lucene/ivy-settings.xml:r1429188
  Merged /lucene/dev/trunk/lucene/SYSTEM_REQUIREMENTS.txt:r1429188
  Merged /lucene/dev/trunk/lucene/MIGRATE.txt:r1429188
  Merged /lucene/dev/trunk/lucene/test-framework:r1429188
  Merged /lucene/dev/trunk/lucene/README.txt:r1429188
  Merged /lucene/dev/trunk/lucene/queries:r1429188
  Merged /lucene/dev/trunk/lucene/module-build.xml:r1429188
  Merged /lucene/dev/trunk/lucene/facet:r1429188
  Merged /lucene/dev/trunk/lucene/queryparser:r1429188
  Merged /lucene/dev/trunk/lucene/common-build.xml:r1429188
  Merged /lucene/dev/trunk/lucene/demo:r1429188
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/TestBackwardsCompatibility.java:r1429188
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.nocfs.zip:r1429188
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.cfs.zip:r1429188
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.nocfs.zip:r1429188
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.cfs.zip:r1429188
  Merged /lucene/dev/trunk/lucene/core:r1429188
  Merged /lucene/dev/trunk/lucene/benchmark:r1429188
  Merged /lucene/dev/trunk/lucene/spatial:r1429188
  Merged /lucene/dev/trunk/lucene/build.xml:r1429188
  Merged /lucene/dev/trunk/lucene/join:r1429188
  Merged /lucene/dev/trunk/lucene/tools:r1429188
  Merged /lucene/dev/trunk/lucene/backwards:r1429188
  Merged /lucene/dev/trunk/lucene/site:r1429188
  Merged /lucene/dev/trunk/lucene/licenses:r1429188
  Merged /lucene/dev/trunk/lucene:r1429188
  Merged /lucene/dev/trunk/dev-tools:r1429188
  Merged /lucene/dev/trunk/solr/test-framework:r1429188
  Merged /lucene/dev/trunk/solr/README.txt:r1429188
  Merged /lucene/dev/trunk/solr/webapp:r1429188
  Merged /lucene/dev/trunk/solr/testlogging.properties:r1429188
  Merged /lucene/dev/trunk/solr/cloud-dev:r1429188
  Merged /lucene/dev/trunk/solr/common-build.xml:r1429188
  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1429188
  Merged /lucene/dev/trunk/solr/scripts:r1429188
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
package org.apache.solr.search;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.response.transform.ScoreAugmenter;
import org.junit.BeforeClass;
import org.junit.Test;


public class TestSolrQueryParser extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml", "schema12.xml");
    createIndex();
  }

  public static void createIndex() {
    String v;
    v="how now brown cow";
    assertU(adoc("id","1", "text",v,  "text_np",v));
    v="now cow";
    assertU(adoc("id","2", "text",v,  "text_np",v));
    assertU(adoc("id","3", "foo_s","a ' \" \\ {! ) } ( { z"));  // A value filled with special chars

    assertU(adoc("id","10", "qqq_s","X"));
    assertU(adoc("id","11", "www_s","X"));
    assertU(adoc("id","12", "eee_s","X"));
    assertU(adoc("id","13", "eee_s","'balance'"));

    assertU(commit());
  }

  @Test
  public void testPhrase() {
    // should generate a phrase of "now cow" and match only one doc
    assertQ(req("q","text:now-cow", "indent","true")
        ,"//*[@numFound='1']"
    );
    // should generate a query of (now OR cow) and match both docs
    assertQ(req("q","text_np:now-cow", "indent","true")
        ,"//*[@numFound='2']"
    );
  }

  @Test
  public void testLocalParamsInQP() throws Exception {
    assertJQ(req("q","qaz {!term f=text v=$qq} wsx", "qq","now")
        ,"/response/numFound==2"
    );

    assertJQ(req("q","qaz {!term f=text v=$qq} wsx", "qq","nomatch")
        ,"/response/numFound==0"
    );

    assertJQ(req("q","qaz {!term f=text}now wsx", "qq","now")
        ,"/response/numFound==2"
    );

    assertJQ(req("q","qaz {!term f=foo_s v='a \\' \" \\\\ {! ) } ( { z'} wsx")           // single quote escaping
        ,"/response/numFound==1"
    );

    assertJQ(req("q","qaz {!term f=foo_s v=\"a ' \\\" \\\\ {! ) } ( { z\"} wsx")         // double quote escaping
        ,"/response/numFound==1"
    );

    // double-join to test back-to-back local params
    assertJQ(req("q","qaz {!join from=www_s to=eee_s}{!join from=qqq_s to=www_s}id:10" )
        ,"/response/docs/[0]/id=='12'"
    );
  }

  @Test
  public void testSolr4121() throws Exception {
    // This query doesn't match anything, testing
    // to make sure that SOLR-4121 is not a problem.
    assertJQ(req("q","eee_s:'balance'", "indent","true")
        ,"/response/numFound==1"
    );
  }
}
