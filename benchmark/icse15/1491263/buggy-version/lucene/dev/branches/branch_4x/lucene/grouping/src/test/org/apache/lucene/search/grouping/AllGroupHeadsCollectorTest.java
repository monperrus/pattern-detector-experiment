package org.apache.lucene.search.grouping;

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

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo.DocValuesType;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.BytesRefFieldSource;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryUtils;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.grouping.function.FunctionAllGroupHeadsCollector;
import org.apache.lucene.search.grouping.term.TermAllGroupHeadsCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AllGroupHeadsCollectorTest extends LuceneTestCase {

  private static final DocValuesType[] vts = new DocValuesType[]{
      DocValuesType.BINARY, DocValuesType.SORTED
  };

  public void testBasic() throws Exception {
    final String groupField = "author";
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(
        random(),
        dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT,
            new MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()));
    boolean canUseIDV = !"Lucene3x".equals(w.w.getConfig().getCodec().getName());
    DocValuesType valueType = vts[random().nextInt(vts.length)];

    // 0
    Document doc = new Document();
    addGroupField(doc, groupField, "author1", canUseIDV, valueType);
    doc.add(newTextField("content", "random text", Field.Store.NO));
    doc.add(newStringField("id_1", "1", Field.Store.NO));
    doc.add(newStringField("id_2", "1", Field.Store.NO));
    w.addDocument(doc);

    // 1
    doc = new Document();
    addGroupField(doc, groupField, "author1", canUseIDV, valueType);
    doc.add(newTextField("content", "some more random text blob", Field.Store.NO));
    doc.add(newStringField("id_1", "2", Field.Store.NO));
    doc.add(newStringField("id_2", "2", Field.Store.NO));
    w.addDocument(doc);

    // 2
    doc = new Document();
    addGroupField(doc, groupField, "author1", canUseIDV, valueType);
    doc.add(newTextField("content", "some more random textual data", Field.Store.NO));
    doc.add(newStringField("id_1", "3", Field.Store.NO));
    doc.add(newStringField("id_2", "3", Field.Store.NO));
    w.addDocument(doc);
    w.commit(); // To ensure a second segment

    // 3
    doc = new Document();
    addGroupField(doc, groupField, "author2", canUseIDV, valueType);
    doc.add(newTextField("content", "some random text", Field.Store.NO));
    doc.add(newStringField("id_1", "4", Field.Store.NO));
    doc.add(newStringField("id_2", "4", Field.Store.NO));
    w.addDocument(doc);

    // 4
    doc = new Document();
    addGroupField(doc, groupField, "author3", canUseIDV, valueType);
    doc.add(newTextField("content", "some more random text", Field.Store.NO));
    doc.add(newStringField("id_1", "5", Field.Store.NO));
    doc.add(newStringField("id_2", "5", Field.Store.NO));
    w.addDocument(doc);

    // 5
    doc = new Document();
    addGroupField(doc, groupField, "author3", canUseIDV, valueType);
    doc.add(newTextField("content", "random blob", Field.Store.NO));
    doc.add(newStringField("id_1", "6", Field.Store.NO));
    doc.add(newStringField("id_2", "6", Field.Store.NO));
    w.addDocument(doc);

    // 6 -- no author field
    doc = new Document();
    doc.add(newTextField("content", "random word stuck in alot of other text", Field.Store.NO));
    doc.add(newStringField("id_1", "6", Field.Store.NO));
    doc.add(newStringField("id_2", "6", Field.Store.NO));
    w.addDocument(doc);

    // 7 -- no author field
    doc = new Document();
    doc.add(newTextField("content", "random word stuck in alot of other text", Field.Store.NO));
    doc.add(newStringField("id_1", "7", Field.Store.NO));
    doc.add(newStringField("id_2", "7", Field.Store.NO));
    w.addDocument(doc);

    IndexReader reader = w.getReader();
    IndexSearcher indexSearcher = newSearcher(reader);

    w.close();
    int maxDoc = reader.maxDoc();

    Sort sortWithinGroup = new Sort(new SortField("id_1", SortField.Type.INT, true));
    AbstractAllGroupHeadsCollector<?> allGroupHeadsCollector = createRandomCollector(groupField, sortWithinGroup, canUseIDV, valueType);
    indexSearcher.search(new TermQuery(new Term("content", "random")), allGroupHeadsCollector);
    assertTrue(arrayContains(new int[]{2, 3, 5, 7}, allGroupHeadsCollector.retrieveGroupHeads()));
    assertTrue(openBitSetContains(new int[]{2, 3, 5, 7}, allGroupHeadsCollector.retrieveGroupHeads(maxDoc), maxDoc));

    allGroupHeadsCollector = createRandomCollector(groupField, sortWithinGroup, canUseIDV, valueType);
    indexSearcher.search(new TermQuery(new Term("content", "some")), allGroupHeadsCollector);
    assertTrue(arrayContains(new int[]{2, 3, 4}, allGroupHeadsCollector.retrieveGroupHeads()));
    assertTrue(openBitSetContains(new int[]{2, 3, 4}, allGroupHeadsCollector.retrieveGroupHeads(maxDoc), maxDoc));

    allGroupHeadsCollector = createRandomCollector(groupField, sortWithinGroup, canUseIDV, valueType);
    indexSearcher.search(new TermQuery(new Term("content", "blob")), allGroupHeadsCollector);
    assertTrue(arrayContains(new int[]{1, 5}, allGroupHeadsCollector.retrieveGroupHeads()));
    assertTrue(openBitSetContains(new int[]{1, 5}, allGroupHeadsCollector.retrieveGroupHeads(maxDoc), maxDoc));

    // STRING sort type triggers different implementation
    Sort sortWithinGroup2 = new Sort(new SortField("id_2", SortField.Type.STRING, true));
    allGroupHeadsCollector = createRandomCollector(groupField, sortWithinGroup2, canUseIDV, valueType);
    indexSearcher.search(new TermQuery(new Term("content", "random")), allGroupHeadsCollector);
    assertTrue(arrayContains(new int[]{2, 3, 5, 7}, allGroupHeadsCollector.retrieveGroupHeads()));
    assertTrue(openBitSetContains(new int[]{2, 3, 5, 7}, allGroupHeadsCollector.retrieveGroupHeads(maxDoc), maxDoc));

    Sort sortWithinGroup3 = new Sort(new SortField("id_2", SortField.Type.STRING, false));
    allGroupHeadsCollector = createRandomCollector(groupField, sortWithinGroup3, canUseIDV, valueType);
    indexSearcher.search(new TermQuery(new Term("content", "random")), allGroupHeadsCollector);
    // 7 b/c higher doc id wins, even if order of field is in not in reverse.
    assertTrue(arrayContains(new int[]{0, 3, 4, 6}, allGroupHeadsCollector.retrieveGroupHeads()));
    assertTrue(openBitSetContains(new int[]{0, 3, 4, 6}, allGroupHeadsCollector.retrieveGroupHeads(maxDoc), maxDoc));

    indexSearcher.getIndexReader().close();
    dir.close();
  }

  public void testRandom() throws Exception {
    int numberOfRuns = _TestUtil.nextInt(random(), 3, 6);
    for (int iter = 0; iter < numberOfRuns; iter++) {
      if (VERBOSE) {
        System.out.println(String.format(Locale.ROOT, "TEST: iter=%d total=%d", iter, numberOfRuns));
      }

      final int numDocs = _TestUtil.nextInt(random(), 100, 1000) * RANDOM_MULTIPLIER;
      final int numGroups = _TestUtil.nextInt(random(), 1, numDocs);

      if (VERBOSE) {
        System.out.println("TEST: numDocs=" + numDocs + " numGroups=" + numGroups);
      }

      final List<BytesRef> groups = new ArrayList<BytesRef>();
      for (int i = 0; i < numGroups; i++) {
        String randomValue;
        do {
          // B/c of DV based impl we can't see the difference between an empty string and a null value.
          // For that reason we don't generate empty string groups.
          randomValue = _TestUtil.randomRealisticUnicodeString(random());
        } while ("".equals(randomValue));
        groups.add(new BytesRef(randomValue));
      }
      final String[] contentStrings = new String[_TestUtil.nextInt(random(), 2, 20)];
      if (VERBOSE) {
        System.out.println("TEST: create fake content");
      }
      for (int contentIDX = 0; contentIDX < contentStrings.length; contentIDX++) {
        final StringBuilder sb = new StringBuilder();
        sb.append("real").append(random().nextInt(3)).append(' ');
        final int fakeCount = random().nextInt(10);
        for (int fakeIDX = 0; fakeIDX < fakeCount; fakeIDX++) {
          sb.append("fake ");
        }
        contentStrings[contentIDX] = sb.toString();
        if (VERBOSE) {
          System.out.println("  content=" + sb.toString());
        }
      }

      Directory dir = newDirectory();
      RandomIndexWriter w = new RandomIndexWriter(
          random(),
          dir,
          newIndexWriterConfig(TEST_VERSION_CURRENT,
              new MockAnalyzer(random())));
      boolean preFlex = "Lucene3x".equals(w.w.getConfig().getCodec().getName());
      boolean canUseIDV = !preFlex;
      DocValuesType valueType = vts[random().nextInt(vts.length)];

      Document doc = new Document();
      Document docNoGroup = new Document();
      Field group = newStringField("group", "", Field.Store.NO);
      doc.add(group);
      Field valuesField = null;
      if (canUseIDV) {
        switch(valueType) {
        case BINARY:
          valuesField = new BinaryDocValuesField("group_dv", new BytesRef());
          break;
        case SORTED:
          valuesField = new SortedDocValuesField("group_dv", new BytesRef());
          break;
        default:
          fail("unhandled type");
        }
        doc.add(valuesField);
      }
      Field sort1 = newStringField("sort1", "", Field.Store.NO);
      doc.add(sort1);
      docNoGroup.add(sort1);
      Field sort2 = newStringField("sort2", "", Field.Store.NO);
      doc.add(sort2);
      docNoGroup.add(sort2);
      Field sort3 = newStringField("sort3", "", Field.Store.NO);
      doc.add(sort3);
      docNoGroup.add(sort3);
      Field content = newTextField("content", "", Field.Store.NO);
      doc.add(content);
      docNoGroup.add(content);
      IntField id = new IntField("id", 0, Field.Store.NO);
      doc.add(id);
      docNoGroup.add(id);
      final GroupDoc[] groupDocs = new GroupDoc[numDocs];
      for (int i = 0; i < numDocs; i++) {
        final BytesRef groupValue;
        if (random().nextInt(24) == 17) {
          // So we test the "doc doesn't have the group'd
          // field" case:
          groupValue = null;
        } else {
          groupValue = groups.get(random().nextInt(groups.size()));
        }

        final GroupDoc groupDoc = new GroupDoc(
            i,
            groupValue,
            groups.get(random().nextInt(groups.size())),
            groups.get(random().nextInt(groups.size())),
            new BytesRef(String.format(Locale.ROOT, "%05d", i)),
            contentStrings[random().nextInt(contentStrings.length)]
        );

        if (VERBOSE) {
          System.out.println("  doc content=" + groupDoc.content + " id=" + i + " group=" + (groupDoc.group == null ? "null" : groupDoc.group.utf8ToString()) + " sort1=" + groupDoc.sort1.utf8ToString() + " sort2=" + groupDoc.sort2.utf8ToString() + " sort3=" + groupDoc.sort3.utf8ToString());
        }

        groupDocs[i] = groupDoc;
        if (groupDoc.group != null) {
          group.setStringValue(groupDoc.group.utf8ToString());
          if (canUseIDV) {
            valuesField.setBytesValue(new BytesRef(groupDoc.group.utf8ToString()));
          }
        }
        sort1.setStringValue(groupDoc.sort1.utf8ToString());
        sort2.setStringValue(groupDoc.sort2.utf8ToString());
        sort3.setStringValue(groupDoc.sort3.utf8ToString());
        content.setStringValue(groupDoc.content);
        id.setIntValue(groupDoc.id);
        if (groupDoc.group == null) {
          w.addDocument(docNoGroup);
        } else {
          w.addDocument(doc);
        }
      }

      final DirectoryReader r = w.getReader();
      w.close();

      // NOTE: intentional but temporary field cache insanity!
      final FieldCache.Ints docIdToFieldId = FieldCache.DEFAULT.getInts(new SlowCompositeReaderWrapper(r), "id", false);
      final int[] fieldIdToDocID = new int[numDocs];
      for (int i = 0; i < numDocs; i++) {
        int fieldId = docIdToFieldId.get(i);
        fieldIdToDocID[fieldId] = i;
      }

      try {
        final IndexSearcher s = newSearcher(r);
        if (SlowCompositeReaderWrapper.class.isAssignableFrom(s.getIndexReader().getClass())) {
          canUseIDV = false;
        } else {
          canUseIDV = !preFlex;
        }

        for (int contentID = 0; contentID < 3; contentID++) {
          final ScoreDoc[] hits = s.search(new TermQuery(new Term("content", "real" + contentID)), numDocs).scoreDocs;
          for (ScoreDoc hit : hits) {
            final GroupDoc gd = groupDocs[docIdToFieldId.get(hit.doc)];
            assertTrue(gd.score == 0.0);
            gd.score = hit.score;
            int docId = gd.id;
            assertEquals(docId, docIdToFieldId.get(hit.doc));
          }
        }

        for (GroupDoc gd : groupDocs) {
          assertTrue(gd.score != 0.0);
        }

        for (int searchIter = 0; searchIter < 100; searchIter++) {

          if (VERBOSE) {
            System.out.println("TEST: searchIter=" + searchIter);
          }

          final String searchTerm = "real" + random().nextInt(3);
          boolean sortByScoreOnly = random().nextBoolean();
          Sort sortWithinGroup = getRandomSort(sortByScoreOnly);
          AbstractAllGroupHeadsCollector<?> allGroupHeadsCollector = createRandomCollector("group", sortWithinGroup, canUseIDV, valueType);
          s.search(new TermQuery(new Term("content", searchTerm)), allGroupHeadsCollector);
          int[] expectedGroupHeads = createExpectedGroupHeads(searchTerm, groupDocs, sortWithinGroup, sortByScoreOnly, fieldIdToDocID);
          int[] actualGroupHeads = allGroupHeadsCollector.retrieveGroupHeads();
          // The actual group heads contains Lucene ids. Need to change them into our id value.
          for (int i = 0; i < actualGroupHeads.length; i++) {
            actualGroupHeads[i] = docIdToFieldId.get(actualGroupHeads[i]);
          }
          // Allows us the easily iterate and assert the actual and expected results.
          Arrays.sort(expectedGroupHeads);
          Arrays.sort(actualGroupHeads);

          if (VERBOSE) {
            System.out.println("Collector: " + allGroupHeadsCollector.getClass().getSimpleName());
            System.out.println("Sort within group: " + sortWithinGroup);
            System.out.println("Num group: " + numGroups);
            System.out.println("Num doc: " + numDocs);
            System.out.println("\n=== Expected: \n");
            for (int expectedDocId : expectedGroupHeads) {
              GroupDoc expectedGroupDoc = groupDocs[expectedDocId];
              String expectedGroup = expectedGroupDoc.group == null ? null : expectedGroupDoc.group.utf8ToString();
              System.out.println(
                  String.format(Locale.ROOT,
                      "Group:%10s score%5f Sort1:%10s Sort2:%10s Sort3:%10s doc:%5d",
                      expectedGroup, expectedGroupDoc.score, expectedGroupDoc.sort1.utf8ToString(),
                      expectedGroupDoc.sort2.utf8ToString(), expectedGroupDoc.sort3.utf8ToString(), expectedDocId
                  )
              );
            }
            System.out.println("\n=== Actual: \n");
            for (int actualDocId : actualGroupHeads) {
              GroupDoc actualGroupDoc = groupDocs[actualDocId];
              String actualGroup = actualGroupDoc.group == null ? null : actualGroupDoc.group.utf8ToString();
              System.out.println(
                  String.format(Locale.ROOT,
                      "Group:%10s score%5f Sort1:%10s Sort2:%10s Sort3:%10s doc:%5d",
                      actualGroup, actualGroupDoc.score, actualGroupDoc.sort1.utf8ToString(),
                      actualGroupDoc.sort2.utf8ToString(), actualGroupDoc.sort3.utf8ToString(), actualDocId
                  )
              );
            }
            System.out.println("\n===================================================================================");
          }

          assertEquals(expectedGroupHeads.length, actualGroupHeads.length);
          for (int i = 0; i < expectedGroupHeads.length; i++) {
            assertEquals(expectedGroupHeads[i], actualGroupHeads[i]);
          }
        }
      } finally {
        QueryUtils.purgeFieldCache(r);
      }

      r.close();
      dir.close();
    }
  }


  private boolean arrayContains(int[] expected, int[] actual) {
    Arrays.sort(actual); // in some cases the actual docs aren't sorted by docid. This method expects that.
    if (expected.length != actual.length) {
      return false;
    }

    for (int e : expected) {
      boolean found = false;
      for (int a : actual) {
        if (e == a) {
          found = true;
        }
      }

      if (!found) {
        return false;
      }
    }

    return true;
  }

  private boolean openBitSetContains(int[] expectedDocs, FixedBitSet actual, int maxDoc) throws IOException {
    if (expectedDocs.length != actual.cardinality()) {
      return false;
    }

    FixedBitSet expected = new FixedBitSet(maxDoc);
    for (int expectedDoc : expectedDocs) {
      expected.set(expectedDoc);
    }

    int docId;
    DocIdSetIterator iterator = expected.iterator();
    while ((docId = iterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
      if (!actual.get(docId)) {
        return false;
      }
    }

    return true;
  }

  private int[] createExpectedGroupHeads(String searchTerm, GroupDoc[] groupDocs, Sort docSort, boolean sortByScoreOnly, int[] fieldIdToDocID) {
    Map<BytesRef, List<GroupDoc>> groupHeads = new HashMap<BytesRef, List<GroupDoc>>();
    for (GroupDoc groupDoc : groupDocs) {
      if (!groupDoc.content.startsWith(searchTerm)) {
        continue;
      }

      if (!groupHeads.containsKey(groupDoc.group)) {
        List<GroupDoc> list = new ArrayList<GroupDoc>();
        list.add(groupDoc);
        groupHeads.put(groupDoc.group, list);
        continue;
      }
      groupHeads.get(groupDoc.group).add(groupDoc);
    }

    int[] allGroupHeads = new int[groupHeads.size()];
    int i = 0;
    for (BytesRef groupValue : groupHeads.keySet()) {
      List<GroupDoc> docs = groupHeads.get(groupValue);
      Collections.sort(docs, getComparator(docSort, sortByScoreOnly, fieldIdToDocID));
      allGroupHeads[i++] = docs.get(0).id;
    }

    return allGroupHeads;
  }

  private Sort getRandomSort(boolean scoreOnly) {
    final List<SortField> sortFields = new ArrayList<SortField>();
    if (random().nextInt(7) == 2 || scoreOnly) {
      sortFields.add(SortField.FIELD_SCORE);
    } else {
      if (random().nextBoolean()) {
        if (random().nextBoolean()) {
          sortFields.add(new SortField("sort1", SortField.Type.STRING, random().nextBoolean()));
        } else {
          sortFields.add(new SortField("sort2", SortField.Type.STRING, random().nextBoolean()));
        }
      } else if (random().nextBoolean()) {
        sortFields.add(new SortField("sort1", SortField.Type.STRING, random().nextBoolean()));
        sortFields.add(new SortField("sort2", SortField.Type.STRING, random().nextBoolean()));
      }
    }
    // Break ties:
    if (random().nextBoolean() && !scoreOnly) {
      sortFields.add(new SortField("sort3", SortField.Type.STRING));
    } else if (!scoreOnly) {
      sortFields.add(new SortField("id", SortField.Type.INT));
    }
    return new Sort(sortFields.toArray(new SortField[sortFields.size()]));
  }

  private Comparator<GroupDoc> getComparator(Sort sort, final boolean sortByScoreOnly, final int[] fieldIdToDocID) {
    final SortField[] sortFields = sort.getSort();
    return new Comparator<GroupDoc>() {
      @Override
      public int compare(GroupDoc d1, GroupDoc d2) {
        for (SortField sf : sortFields) {
          final int cmp;
          if (sf.getType() == SortField.Type.SCORE) {
            if (d1.score > d2.score) {
              cmp = -1;
            } else if (d1.score < d2.score) {
              cmp = 1;
            } else {
              cmp = sortByScoreOnly ? fieldIdToDocID[d1.id] - fieldIdToDocID[d2.id] : 0;
            }
          } else if (sf.getField().equals("sort1")) {
            cmp = d1.sort1.compareTo(d2.sort1);
          } else if (sf.getField().equals("sort2")) {
            cmp = d1.sort2.compareTo(d2.sort2);
          } else if (sf.getField().equals("sort3")) {
            cmp = d1.sort3.compareTo(d2.sort3);
          } else {
            assertEquals(sf.getField(), "id");
            cmp = d1.id - d2.id;
          }
          if (cmp != 0) {
            return sf.getReverse() ? -cmp : cmp;
          }
        }
        // Our sort always fully tie breaks:
        fail();
        return 0;
      }
    };
  }

  @SuppressWarnings({"unchecked","rawtypes"})
  private AbstractAllGroupHeadsCollector<?> createRandomCollector(String groupField, Sort sortWithinGroup, boolean canUseIDV, DocValuesType valueType) {
    AbstractAllGroupHeadsCollector<? extends AbstractAllGroupHeadsCollector.GroupHead> collector;
    if (random().nextBoolean()) {
      ValueSource vs = new BytesRefFieldSource(groupField);
      collector =  new FunctionAllGroupHeadsCollector(vs, new HashMap<Object, Object>(), sortWithinGroup);
    } else {
      collector =  TermAllGroupHeadsCollector.create(groupField, sortWithinGroup);
    }

    if (VERBOSE) {
      System.out.println("Selected implementation: " + collector.getClass().getSimpleName());
    }

    return collector;
  }

  private void addGroupField(Document doc, String groupField, String value, boolean canUseIDV, DocValuesType valueType) {
    doc.add(new TextField(groupField, value, Field.Store.YES));
    if (canUseIDV) {
      Field valuesField = null;
      switch(valueType) {
      case BINARY:
        valuesField = new BinaryDocValuesField(groupField + "_dv", new BytesRef(value));
        break;
      case SORTED:
        valuesField = new SortedDocValuesField(groupField + "_dv", new BytesRef(value));
        break;
      default:
        fail("unhandled type");
      }
      doc.add(valuesField);
    }
  }

  private static class GroupDoc {
    final int id;
    final BytesRef group;
    final BytesRef sort1;
    final BytesRef sort2;
    final BytesRef sort3;
    // content must be "realN ..."
    final String content;
    float score;

    public GroupDoc(int id, BytesRef group, BytesRef sort1, BytesRef sort2, BytesRef sort3, String content) {
      this.id = id;
      this.group = group;
      this.sort1 = sort1;
      this.sort2 = sort2;
      this.sort3 = sort3;
      this.content = content;
    }

  }

}
