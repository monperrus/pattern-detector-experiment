package org.apache.solr;

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

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.params.ModifiableSolrParams;

/**
 * TODO? perhaps use:
 *  http://docs.codehaus.org/display/JETTY/ServletTester
 * rather then open a real connection?
 *
 * @since solr 3.5
 */
public class TestDistributedGrouping extends BaseDistributedSearchTestCase {

  String t1="a_t";
  String s1="1_a_s1";
  String s2="2_a_s1";
  String tlong = "other_tl1";
  String tdate_a = "a_n_tdt";
  String tdate_b = "b_n_tdt";
  String oddField="oddField_s";

  public void doTest() throws Exception {
    del("*:*");
    commit();

    handle.clear();
    handle.put("QTime", SKIPVAL);
    handle.put("timestamp", SKIPVAL);
    // Test distributed grouping with empty indices
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "hl","true","hl.fl",t1);
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "facet", "true", "facet.field", t1);
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "stats", "true", "stats.field", tlong);
    query("q", "kings", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "spellcheck", "true", "spellcheck.build", "true", "qt", "spellCheckCompRH");
    query("q", "*:*", "fq", s2 + ":a", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "group.truncate", "true", "facet", "true", "facet.field", t1);

    indexr(id,1, s1, 100, tlong, 100,t1,"now is the time for all good men",
           tdate_a, "2010-04-20T11:00:00Z",
           tdate_b, "2009-08-20T11:00:00Z",
           "foo_f", 1.414f, "foo_b", "true", "foo_d", 1.414d);
    indexr(id,2, s1, 50 , tlong, 50,t1,"to come to the aid of their country.",
           tdate_a, "2010-05-02T11:00:00Z",
           tdate_b, "2009-11-02T11:00:00Z");
    indexr(id,3, s1, 2, tlong, 2,t1,"how now brown cow",
           tdate_a, "2010-05-03T11:00:00Z");
    indexr(id,4, s1, -100 ,tlong, 101,
           t1,"the quick fox jumped over the lazy dog",
           tdate_a, "2010-05-03T11:00:00Z",
           tdate_b, "2010-05-03T11:00:00Z");
    indexr(id,5, s1, 500, tlong, 500 ,
           t1,"the quick fox jumped way over the lazy dog",
           tdate_a, "2010-05-05T11:00:00Z");
    indexr(id,6, s1, -600, tlong, 600 ,t1,"humpty dumpy sat on a wall");
    indexr(id,7, s1, 123, tlong, 123 ,t1,"humpty dumpy had a great fall");
    indexr(id,8, s1, 876, tlong, 876,
           tdate_b, "2010-01-05T11:00:00Z",
           t1,"all the kings horses and all the kings men");
    indexr(id,9, s1, 7, tlong, 7,t1,"couldn't put humpty together again");
    indexr(id,10, s1, 4321, tlong, 4321,t1,"this too shall pass");
    indexr(id,11, s1, -987, tlong, 987,
           t1,"An eye for eye only ends up making the whole world blind.");
    indexr(id,12, s1, 379, tlong, 379,
           t1,"Great works are performed, not by strength, but by perseverance.");
    indexr(id,13, s1, 232, tlong, 232,
           t1,"no eggs on wall, lesson learned", 
           oddField, "odd man out");

    indexr(id, 14, "SubjectTerms_mfacet", new String[]  {"mathematical models", "mathematical analysis"});
    indexr(id, 15, "SubjectTerms_mfacet", new String[]  {"test 1", "test 2", "test3"});
    indexr(id, 16, "SubjectTerms_mfacet", new String[]  {"test 1", "test 2", "test3"});
    String[] vals = new String[100];
    for (int i=0; i<100; i++) {
      vals[i] = "test " + i;
    }
    indexr(id, 17, "SubjectTerms_mfacet", vals);

    indexr(
        id, 18, s1, "232", tlong, 332,
        t1,"no eggs on wall, lesson learned",
        oddField, "odd man out"
    );
    indexr(
        id, 19, s1, "232", tlong, 432,
        t1, "many eggs on wall",
        oddField, "odd man in"
    );
    indexr(
        id, 20, s1, "232", tlong, 532,
        t1, "some eggs on wall",
        oddField, "odd man between"
    );
    indexr(
        id, 21, s1, "232", tlong, 632,
        t1, "a few eggs on wall",
        oddField, "odd man under"
    );
    indexr(
        id, 22, s1, "232", tlong, 732,
        t1, "any eggs on wall",
        oddField, "odd man above"
    );
    indexr(
        id, 23, s1, "233", tlong, 734,
        t1, "dirty eggs",
        oddField, "odd eggs"
    );

    for (int i = 100; i < 150; i++) {
      indexr(id, i);
    }

    int[] values = new int[]{9999, 99999, 999999, 9999999};
    for (int shard = 0; shard < clients.size(); shard++) {
      int groupValue = values[shard];
      for (int i = 500; i < 600; i++) {
        index_specific(shard, s1, groupValue, s2, "a", id, i * (shard + 1), t1, shard);
      }
    }

    commit();

	  // test grouping
    // The second sort = id asc . The sorting behaviour is different in dist mode. See TopDocs#merge
    // The shard the result came from matters in the order if both document sortvalues are equal
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", "id asc, _docid_ asc");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", "{!func}add(" + tlong + ",5) asc, id asc");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "facet", "true", "facet.field", t1);
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "stats", "true", "stats.field", tlong);
    query("q", "kings", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "spellcheck", "true", "spellcheck.build", "true", "qt", "spellCheckCompRH");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "facet", "true", "hl","true","hl.fl",t1);
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "group.sort", "id desc");

    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.offset", 5, "group.limit", 5, "sort", s1 + " asc, id asc");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "offset", 5, "rows", 5, "group.offset", 5, "group.limit", 5, "sort", s1 + " asc, id asc");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "offset", 5, "rows", 5, "sort", s1 + " asc, id asc", "group.format", "simple");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "offset", 5, "rows", 5, "sort", s1 + " asc, id asc", "group.main", "true");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.offset", 5, "group.limit", 5, "sort", s1 + " asc, id asc", "group.format", "simple", "offset", 5, "rows", 5);
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.offset", 5, "group.limit", 5, "sort", s1 + " asc, id asc", "group.main", "true", "offset", 5, "rows", 5);

    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.query", t1 + ":kings OR " + t1 + ":eggs", "group.limit", 10, "sort", s1 + " asc, id asc");
    query("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.query", t1 + ":kings OR " + t1 + ":eggs", "group.limit", 10, "sort", s1 + " asc, id asc");

    // SOLR-3109
    query("q", t1 + ":eggs", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", tlong + " asc, id asc");
    query("q", s1 + ":232", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", tlong + " asc, id asc");

    // In order to validate this we need to make sure that during indexing that all documents of one group only occur on the same shard
    query("q", "*:*", "fq", s2 + ":a", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "group.ngroups", "true");
    query("q", "*:*", "fq", s2 + ":a", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "group.truncate", "true");
    query("q", "*:*", "fq", s2 + ":a", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " asc, id asc", "group.truncate", "true", "facet", "true", "facet.field", t1);

    // We cannot validate distributed grouping with scoring as first sort. since there is no global idf. We can check if no errors occur
    simpleQuery("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", s1 + " desc", "group.sort", "score desc"); // SOLR-2955
    simpleQuery("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10, "sort", "score desc, _docid_ asc, id asc");
    simpleQuery("q", "*:*", "rows", 100, "fl", "id," + s1, "group", "true", "group.field", s1, "group.limit", 10);
  }

  private void simpleQuery(Object... queryParams) throws SolrServerException {
    ModifiableSolrParams params = new ModifiableSolrParams();
    for (int i = 0; i < queryParams.length; i += 2) {
      params.add(queryParams[i].toString(), queryParams[i + 1].toString());
    }
    params.set("shards", shards);
    queryServer(params);
  }

}
