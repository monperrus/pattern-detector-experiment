  Merged /lucene/dev/trunk/lucene/classification/build.xml:r1464802
  Merged /lucene/dev/trunk/lucene/classification/ivy.xml:r1464802
  Merged /lucene/dev/trunk/lucene/classification/src:r1464802
  Merged /lucene/dev/trunk/lucene/classification:r1464802
  Merged /lucene/dev/trunk/lucene/misc:r1464802
  Merged /lucene/dev/trunk/lucene/sandbox:r1464802
  Merged /lucene/dev/trunk/lucene/highlighter:r1464802
  Merged /lucene/dev/trunk/lucene/NOTICE.txt:r1464802
  Merged /lucene/dev/trunk/lucene/codecs:r1464802
  Merged /lucene/dev/trunk/lucene/LICENSE.txt:r1464802
  Merged /lucene/dev/trunk/lucene/ivy-settings.xml:r1464802
  Merged /lucene/dev/trunk/lucene/SYSTEM_REQUIREMENTS.txt:r1464802
  Merged /lucene/dev/trunk/lucene/MIGRATE.txt:r1464802
  Merged /lucene/dev/trunk/lucene/test-framework:r1464802
  Merged /lucene/dev/trunk/lucene/README.txt:r1464802
  Merged /lucene/dev/trunk/lucene/queries:r1464802
  Merged /lucene/dev/trunk/lucene/module-build.xml:r1464802
  Merged /lucene/dev/trunk/lucene/facet:r1464802
  Merged /lucene/dev/trunk/lucene/queryparser:r1464802
  Merged /lucene/dev/trunk/lucene/demo:r1464802
  Merged /lucene/dev/trunk/lucene/common-build.xml:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.cfs.zip:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.nocfs.zip:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.cfs.zip:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.nocfs.zip:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/TestBackwardsCompatibility.java:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestSort.java:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestSortRandom.java:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestTopFieldCollector.java:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestSortDocValues.java:r1464802
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestTotalHitCountCollector.java:r1464802
  Merged /lucene/dev/trunk/lucene/core:r1464802
  Merged /lucene/dev/trunk/lucene/benchmark:r1464802
  Merged /lucene/dev/trunk/lucene/spatial:r1464802
  Merged /lucene/dev/trunk/lucene/build.xml:r1464802
  Merged /lucene/dev/trunk/lucene/join:r1464802
  Merged /lucene/dev/trunk/lucene/tools:r1464802
  Merged /lucene/dev/trunk/lucene/backwards:r1464802
  Merged /lucene/dev/trunk/lucene/site:r1464802
  Merged /lucene/dev/trunk/lucene/licenses:r1464802
  Merged /lucene/dev/trunk/lucene/memory:r1464802
  Merged /lucene/dev/trunk/lucene/JRE_VERSION_MIGRATION.txt:r1464802
  Merged /lucene/dev/trunk/lucene/BUILD.txt:r1464802
  Merged /lucene/dev/trunk/lucene/suggest:r1464802
  Merged /lucene/dev/trunk/lucene/analysis/icu/src/java/org/apache/lucene/collation/ICUCollationKeyFilterFactory.java:r1464802
  Merged /lucene/dev/trunk/lucene/analysis:r1464802
  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1464802
  Merged /lucene/dev/trunk/lucene/grouping:r1464802
  Merged /lucene/dev/trunk/lucene:r1464802
  Merged /lucene/dev/trunk/dev-tools:r1464802
  Merged /lucene/dev/trunk/solr/test-framework:r1464802
  Merged /lucene/dev/trunk/solr/README.txt:r1464802
  Merged /lucene/dev/trunk/solr/webapp:r1464802
  Merged /lucene/dev/trunk/solr/cloud-dev:r1464802
  Merged /lucene/dev/trunk/solr/common-build.xml:r1464802
  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1464802
  Merged /lucene/dev/trunk/solr/scripts:r1464802
  Merged /lucene/dev/trunk/solr/core/src/test/org/apache/solr/core/TestConfig.java:r1464802
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

import org.apache.commons.lang.StringUtils;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class TestPseudoReturnFields extends SolrTestCaseJ4 {

  // :TODO: datatypes produced by the functions used may change

  /**
   * values of the fl param that mean all real fields
   */
  private static String[] ALL_REAL_FIELDS = new String[] { "", "*" };

  /**
   * values of the fl param that mean all real fields and score
   */
  private static String[] SCORE_AND_REAL_FIELDS = new String[] { 
    "score,*", "*,score"
  };

  @BeforeClass
  public static void beforeTests() throws Exception {
    initCore("solrconfig.xml","schema12.xml");


    assertU(adoc("id", "42", "val_i", "1", "ssto", "X", "subject", "aaa"));
    assertU(adoc("id", "43", "val_i", "9", "ssto", "X", "subject", "bbb"));
    assertU(adoc("id", "44", "val_i", "4", "ssto", "X", "subject", "aaa"));
    assertU(adoc("id", "45", "val_i", "6", "ssto", "X", "subject", "aaa"));
    assertU(adoc("id", "46", "val_i", "3", "ssto", "X", "subject", "ggg"));
    assertU(commit());
  }

  @Test
  public void testMultiValued() throws Exception {
    // the response writers used to consult isMultiValued on the field
    // but this doesn't work when you alias a single valued field to
    // a multi valued field (the field value is copied first, then
    // if the type lookup is done again later, we get the wrong thing). SOLR-4036

    assertJQ(req("q","id:42", "fl","val_ss:val_i, val2_ss:10")
        ,"/response/docs==[{'val2_ss':10,'val_ss':1}]"
    );

    assertJQ(req("qt","/get", "id","42", "fl","val_ss:val_i, val2_ss:10")
        ,"/doc=={'val2_ss':10,'val_ss':1}"
    );

    // also check real-time-get from transaction log
    assertU(adoc("id", "42", "val_i", "1", "ssto", "X", "subject", "aaa"));

    assertJQ(req("qt","/get", "id","42", "fl","val_ss:val_i, val2_ss:10")
        ,"/doc=={'val2_ss':10,'val_ss':1}"
    );

  }
  
  @Test
  public void testAllRealFields() throws Exception {

    for (String fl : ALL_REAL_FIELDS) {
      assertQ("fl="+fl+" ... all real fields",
              req("q","*:*", "rows", "1", "fl",fl)
              ,"//result[@numFound='5']"
              ,"//result/doc/str[@name='id']"
              ,"//result/doc/int[@name='val_i']"
              ,"//result/doc/str[@name='ssto']"
              ,"//result/doc/str[@name='subject']"
              
              ,"//result/doc[count(*)=4]"
              );
    }
  }

  @Test
  public void testScoreAndAllRealFields() throws Exception {

    for (String fl : SCORE_AND_REAL_FIELDS) {
      assertQ("fl="+fl+" ... score and real fields",
              req("q","*:*", "rows", "1", "fl",fl)
              ,"//result[@numFound='5']"
              ,"//result/doc/str[@name='id']"
              ,"//result/doc/int[@name='val_i']"
              ,"//result/doc/str[@name='ssto']"
              ,"//result/doc/str[@name='subject']"
              ,"//result/doc/float[@name='score']"
              
              ,"//result/doc[count(*)=5]"
              );
    }
  }

  @Test
  public void testScoreAndExplicitRealFields() throws Exception {

    assertQ("fl=score,val_i",
            req("q","*:*", "rows", "1", "fl","score,val_i")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/float[@name='score']"
            
            ,"//result/doc[count(*)=2]"
            );
    assertQ("fl=score&fl=val_i",
            req("q","*:*", "rows", "1", "fl","score","fl","val_i")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/float[@name='score']"
            
            ,"//result/doc[count(*)=2]"
            );

    assertQ("fl=val_i",
            req("q","*:*", "rows", "1", "fl","val_i")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            
            ,"//result/doc[count(*)=1]"
            );
  }

  @Test
  public void testFunctions() throws Exception {
    assertQ("fl=log(val_i)",
            req("q","*:*", "rows", "1", "fl","log(val_i)")
            ,"//result[@numFound='5']"
            ,"//result/doc/double[@name='log(val_i)']"
            
            ,"//result/doc[count(*)=1]"
            );

    assertQ("fl=log(val_i),abs(val_i)",
            req("q","*:*", "rows", "1", "fl","log(val_i),abs(val_i)")
            ,"//result[@numFound='5']"
            ,"//result/doc/double[@name='log(val_i)']"
            ,"//result/doc/float[@name='abs(val_i)']"
            
            ,"//result/doc[count(*)=2]"
            );
    assertQ("fl=log(val_i)&fl=abs(val_i)",
            req("q","*:*", "rows", "1", "fl","log(val_i)","fl","abs(val_i)")
            ,"//result[@numFound='5']"
            ,"//result/doc/double[@name='log(val_i)']"
            ,"//result/doc/float[@name='abs(val_i)']"
            
            ,"//result/doc[count(*)=2]"
            );
  }

  @Test
  public void testFunctionsAndExplicit() throws Exception {
    assertQ("fl=log(val_i),val_i",
            req("q","*:*", "rows", "1", "fl","log(val_i),val_i")
            ,"//result[@numFound='5']"
            ,"//result/doc/double[@name='log(val_i)']"
            ,"//result/doc/int[@name='val_i']"

            ,"//result/doc[count(*)=2]"
            );

    assertQ("fl=log(val_i)&fl=val_i",
            req("q","*:*", "rows", "1", "fl","log(val_i)","fl","val_i")
            ,"//result[@numFound='5']"
            ,"//result/doc/double[@name='log(val_i)']"
            ,"//result/doc/int[@name='val_i']"
            
            ,"//result/doc[count(*)=2]"
            );
  }

  @Test
  public void testFunctionsAndScore() throws Exception {

    assertQ("fl=log(val_i),score",
            req("q","*:*", "rows", "1", "fl","log(val_i),score")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/double[@name='log(val_i)']"
            
            ,"//result/doc[count(*)=2]"
            );
    assertQ("fl=log(val_i)&fl=score",
            req("q","*:*", "rows", "1", "fl","log(val_i)","fl","score")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/double[@name='log(val_i)']"
            
            ,"//result/doc[count(*)=2]"
            );

    assertQ("fl=score,log(val_i),abs(val_i)",
            req("q","*:*", "rows", "1", 
                "fl","score,log(val_i),abs(val_i)")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/double[@name='log(val_i)']"
            ,"//result/doc/float[@name='abs(val_i)']"
            
            ,"//result/doc[count(*)=3]"
            );
    assertQ("fl=score&fl=log(val_i)&fl=abs(val_i)",
            req("q","*:*", "rows", "1", 
                "fl","score","fl","log(val_i)","fl","abs(val_i)")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/double[@name='log(val_i)']"
            ,"//result/doc/float[@name='abs(val_i)']"
            
            ,"//result/doc[count(*)=3]"
            );
    
  }

  @Test
  public void testGlobs() throws Exception {
    assertQ("fl=val_*",
            req("q","*:*", "rows", "1", "fl","val_*")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            
            ,"//result/doc[count(*)=1]"
            );

    assertQ("fl=val_*,subj*",
            req("q","*:*", "rows", "1", "fl","val_*,subj*")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/str[@name='subject']"
            
            ,"//result/doc[count(*)=2]"
            );
    assertQ("fl=val_*&fl=subj*",
            req("q","*:*", "rows", "1", "fl","val_*","fl","subj*")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/str[@name='subject']"
            
            ,"//result/doc[count(*)=2]"
            );
  }

  @Test
  public void testGlobsAndExplicit() throws Exception {
    assertQ("fl=val_*,id",
            req("q","*:*", "rows", "1", "fl","val_*,id")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/str[@name='id']"
            
            ,"//result/doc[count(*)=2]"
            );

    assertQ("fl=val_*,subj*,id",
            req("q","*:*", "rows", "1", "fl","val_*,subj*,id")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/str[@name='subject']"
            ,"//result/doc/str[@name='id']"
            
            ,"//result/doc[count(*)=3]"
            );
    assertQ("fl=val_*&fl=subj*&fl=id",
            req("q","*:*", "rows", "1", "fl","val_*","fl","subj*","fl","id")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/str[@name='subject']"
            ,"//result/doc/str[@name='id']"
            
            ,"//result/doc[count(*)=3]"
            );
  }

  @Test
  public void testGlobsAndScore() throws Exception {
    assertQ("fl=val_*,score",
            req("q","*:*", "rows", "1", "fl","val_*,score", "indent", "true")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/int[@name='val_i']"
            
            ,"//result/doc[count(*)=2]"
            );

    assertQ("fl=val_*,subj*,score",
            req("q","*:*", "rows", "1", "fl","val_*,subj*,score")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/str[@name='subject']"
            
            ,"//result/doc[count(*)=3]"
            );
    assertQ("fl=val_*&fl=subj*&fl=score",
            req("q","*:*", "rows", "1", 
                "fl","val_*","fl","subj*","fl","score")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/int[@name='val_i']"
            ,"//result/doc/str[@name='subject']"
            
            ,"//result/doc[count(*)=3]"
            );

    
  }

  @Test
  public void testAugmenters() throws Exception {
    assertQ("fl=[docid]",
            req("q","*:*", "rows", "1", "fl","[docid]")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='[docid]']"
            
            ,"//result/doc[count(*)=1]"
            );

    assertQ("fl=[docid],[explain]",
            req("q","*:*", "rows", "1", "fl","[docid],[explain]")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='[docid]']"
            ,"//result/doc/str[@name='[explain]']"
            
            ,"//result/doc[count(*)=2]"
            );
    assertQ("fl=[docid]&fl=[explain]",
            req("q","*:*", "rows", "1", "fl","[docid]","fl","[explain]")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='[docid]']"
            ,"//result/doc/str[@name='[explain]']"
            
            ,"//result/doc[count(*)=2]"
            );
  }

  @Test
  public void testAugmentersAndExplicit() throws Exception {
    assertQ("fl=[docid],id",
            req("q","*:*", "rows", "1", 
                "fl","[docid],id")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='[docid]']"
            ,"//result/doc/str[@name='id']"
            
            ,"//result/doc[count(*)=2]"
            );

    assertQ("fl=[docid],[explain],id",
            req("q","*:*", "rows", "1", 
                "fl","[docid],[explain],id")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='[docid]']"
            ,"//result/doc/str[@name='[explain]']"
            ,"//result/doc/str[@name='id']"
            
            ,"//result/doc[count(*)=3]"
            );
    assertQ("fl=[docid]&fl=[explain]&fl=id",
            req("q","*:*", "rows", "1", 
                "fl","[docid]","fl","[explain]","fl","id")
            ,"//result[@numFound='5']"
            ,"//result/doc/int[@name='[docid]']"
            ,"//result/doc/str[@name='[explain]']"
            ,"//result/doc/str[@name='id']"
            
            ,"//result/doc[count(*)=3]"
            );
  }

  @Test
  public void testAugmentersAndScore() throws Exception {
    assertQ("fl=[docid],score",
            req("q","*:*", "rows", "1",
                "fl","[docid],score")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/int[@name='[docid]']"
            
            ,"//result/doc[count(*)=2]"
            );

    assertQ("fl=[docid],[explain],score",
            req("q","*:*", "rows", "1",
                "fl","[docid],[explain],score")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/int[@name='[docid]']"
            ,"//result/doc/str[@name='[explain]']"
            
            ,"//result/doc[count(*)=3]"
            );
    assertQ("fl=[docid]&fl=[explain]&fl=score",
            req("q","*:*", "rows", "1", 
                "fl","[docid]","fl","[explain]","fl","score")
            ,"//result[@numFound='5']"
            ,"//result/doc/float[@name='score']"
            ,"//result/doc/int[@name='[docid]']"
            ,"//result/doc/str[@name='[explain]']"
            
            ,"//result/doc[count(*)=3]"
            );
  }

  @Test
  public void testAugmentersGlobsExplicitAndScoreOhMy() throws Exception {
    Random random = random();

    // NOTE: 'ssto' is the missing one
    final List<String> fl = Arrays.asList
      ("id","[docid]","[explain]","score","val_*","subj*");
    
    final int iters = atLeast(random, 10);
    for (int i = 0; i< iters; i++) {
      
      Collections.shuffle(fl, random);

      final String singleFl = StringUtils.join(fl.toArray(),',');
      assertQ("fl=" + singleFl,
              req("q","*:*", "rows", "1","fl",singleFl)
              ,"//result[@numFound='5']"
              ,"//result/doc/str[@name='id']"
              ,"//result/doc/float[@name='score']"
              ,"//result/doc/str[@name='subject']"
              ,"//result/doc/int[@name='val_i']"
              ,"//result/doc/int[@name='[docid]']"
              ,"//result/doc/str[@name='[explain]']"
              
              ,"//result/doc[count(*)=6]"
              );

      final List<String> params = new ArrayList<String>((fl.size()*2) + 4);
      final StringBuilder info = new StringBuilder();
      params.addAll(Arrays.asList("q","*:*", "rows", "1"));
      for (String item : fl) {
        params.add("fl");
        params.add(item);
        info.append("&fl=").append(item);
      }
      
      assertQ(info.toString(),
              req((String[])params.toArray(new String[0]))
              ,"//result[@numFound='5']"
              ,"//result/doc/str[@name='id']"
              ,"//result/doc/float[@name='score']"
              ,"//result/doc/str[@name='subject']"
              ,"//result/doc/int[@name='val_i']"
              ,"//result/doc/int[@name='[docid]']"
              ,"//result/doc/str[@name='[explain]']"
              
              ,"//result/doc[count(*)=6]"
              );

    }
  }
}
