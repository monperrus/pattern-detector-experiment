package org.apache.solr;

import org.apache.lucene.util._TestUtil;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;

/**
 * Tests that highlighting doesn't break on grouped documents
 * with duplicate unique key fields stored on multiple shards.
 */
public class TestHighlightDedupGrouping extends BaseDistributedSearchTestCase {

  private static final String id_s1 = "id_s1"; // string copy of the id for highlighting
  private static final String group_ti1 = "group_ti1";
  private static final String shard_i1 = "shard_i1";

  public TestHighlightDedupGrouping() {
    super();
    fixShardCount = true;
    shardCount = 2;
  }

  @Override
  public void doTest() throws Exception {
    basicTest();
    randomizedTest();
  }

  private void basicTest() throws Exception {
    del("*:*");
    commit();

    handle.clear();
    handle.put("QTime", SKIPVAL);
    handle.put("timestamp", SKIPVAL);
    handle.put("grouped", UNORDERED);   // distrib grouping doesn't guarantee order of top level group commands

    int docid = 1;
    int group = 5;
    for (int shard = 0 ; shard < shardCount ; ++shard) {
      addDoc(docid, group, shard); // add the same doc to both shards
      clients.get(shard).commit();
    }

    QueryResponse rsp = queryServer(params
        ("q",           id_s1 + ":" + docid,
         "shards",      shards,
         "group",       "true",
         "group.field", id_s1,
         "group.limit", Integer.toString(shardCount),
         "hl",          "true",
         "hl.fl",       id_s1
        ));

    // The number of highlit documents should be the same as the de-duplicated docs
    assertEquals(1, rsp.getHighlighting().values().size());
  }

  private void randomizedTest() throws Exception {
    del("*:*");
    commit();

    handle.clear();
    handle.put("QTime", SKIPVAL);
    handle.put("timestamp", SKIPVAL);
    handle.put("grouped", UNORDERED);   // distrib grouping doesn't guarantee order of top level group commands

    int numDocs = _TestUtil.nextInt(random(), 100, 1000);
    int numGroups = _TestUtil.nextInt(random(), 1, numDocs / 50);
    int[] docsInGroup = new int[numGroups + 1];
    int percentDuplicates = _TestUtil.nextInt(random(), 1, 25);
    for (int docid = 0 ; docid < numDocs ; ++docid) {
      int group = _TestUtil.nextInt(random(), 1, numGroups);
      ++docsInGroup[group];
      boolean makeDuplicate = 0 == _TestUtil.nextInt(random(), 0, numDocs / percentDuplicates);
      if (makeDuplicate) {
        for (int shard = 0 ; shard < shardCount ; ++shard) {
          addDoc(docid, group, shard);
        }
      } else {
        int shard = _TestUtil.nextInt(random(), 0, shardCount - 1);
        addDoc(docid, group, shard);
      }
    }
    for (int shard = 0 ; shard < shardCount ; ++shard) {
      clients.get(shard).commit();
    }

    for (int group = 1 ; group <= numGroups ; ++group) {
      QueryResponse rsp = queryServer(params
          ("q", group_ti1 + ":" + group + " AND " + id_s1 + ":[* TO *]", "start", "0", "rows", "" + numDocs,
           "fl", id_s1 + "," + shard_i1, "sort", id_s1 + " asc", "shards", shards,
           "group", "true", "group.field", id_s1
          ,"group.limit", "" + numDocs
          ,"hl", "true", "hl.fl", "*", "hl.requireFieldMatch", "true"
          ));
      // The number of highlit documents should be the same as the de-duplicated docs for this group
      assertEquals(docsInGroup[group], rsp.getHighlighting().values().size());
    }
  }

  private void addDoc(int docid, int group, int shard) throws IOException, SolrServerException {
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField(id, docid);
    doc.addField(id_s1, docid);  // string copy of the id for highlighting
    doc.addField(group_ti1, group);
    doc.addField(shard_i1, shard);
    clients.get(shard).add(doc);
  }
}
