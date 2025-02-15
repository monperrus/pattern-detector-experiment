  Merged /lucene/dev/branches/branch_4x/lucene/CHANGES.txt:r1480024
package org.apache.lucene.facet.range;

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

import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.facet.FacetTestCase;
import org.apache.lucene.facet.FacetTestUtils;
import org.apache.lucene.facet.params.FacetIndexingParams;
import org.apache.lucene.facet.params.FacetSearchParams;
import org.apache.lucene.facet.search.DrillDownQuery;
import org.apache.lucene.facet.search.FacetResult;
import org.apache.lucene.facet.search.FacetResultNode;
import org.apache.lucene.facet.search.FacetsCollector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util._TestUtil;

public class TestRangeAccumulator extends FacetTestCase {

  public void testBasicLong() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    Document doc = new Document();
    NumericDocValuesField field = new NumericDocValuesField("field", 0L);
    doc.add(field);
    for(long l=0;l<100;l++) {
      field.setLongValue(l);
      w.addDocument(doc);
    }

    IndexReader r = w.getReader();
    w.close();

    FacetSearchParams fsp = new FacetSearchParams(
                                new RangeFacetRequest<LongRange>("field",
                                                      new LongRange("less than 10", 0L, true, 10L, false),
                                                      new LongRange("less than or equal to 10", 0L, true, 10L, true),
                                                      new LongRange("over 90", 90L, false, 100L, false),
                                                      new LongRange("90 or above", 90L, true, 100L, false),
                                                      new LongRange("over 1000", 1000L, false, Long.MAX_VALUE, false)));

    RangeAccumulator a = new RangeAccumulator(fsp, r);
    
    FacetsCollector fc = FacetsCollector.create(a);

    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);
    List<FacetResult> result = fc.getFacetResults();
    assertEquals(1, result.size());
    assertEquals("field (0)\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n", FacetTestUtils.toSimpleString(result.get(0)));
    
    r.close();
    d.close();
  }

  public void testBasicDouble() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    Document doc = new Document();
    DoubleDocValuesField field = new DoubleDocValuesField("field", 0.0);
    doc.add(field);
    for(long l=0;l<100;l++) {
      field.setDoubleValue((double) l);
      w.addDocument(doc);
    }

    IndexReader r = w.getReader();
    w.close();

    FacetSearchParams fsp = new FacetSearchParams(
                                new RangeFacetRequest<DoubleRange>("field",
                                                      new DoubleRange("less than 10", 0.0, true, 10.0, false),
                                                      new DoubleRange("less than or equal to 10", 0.0, true, 10.0, true),
                                                      new DoubleRange("over 90", 90.0, false, 100.0, false),
                                                      new DoubleRange("90 or above", 90.0, true, 100.0, false),
                                                      new DoubleRange("over 1000", 1000.0, false, Double.POSITIVE_INFINITY, false)));

    RangeAccumulator a = new RangeAccumulator(fsp, r);
    
    FacetsCollector fc = FacetsCollector.create(a);

    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);
    List<FacetResult> result = fc.getFacetResults();
    assertEquals(1, result.size());
    assertEquals("field (0)\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n", FacetTestUtils.toSimpleString(result.get(0)));
    
    r.close();
    d.close();
  }

  public void testBasicFloat() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    Document doc = new Document();
    FloatDocValuesField field = new FloatDocValuesField("field", 0.0f);
    doc.add(field);
    for(long l=0;l<100;l++) {
      field.setFloatValue((float) l);
      w.addDocument(doc);
    }

    IndexReader r = w.getReader();
    w.close();

    FacetSearchParams fsp = new FacetSearchParams(
                                new RangeFacetRequest<FloatRange>("field",
                                                      new FloatRange("less than 10", 0.0f, true, 10.0f, false),
                                                      new FloatRange("less than or equal to 10", 0.0f, true, 10.0f, true),
                                                      new FloatRange("over 90", 90.0f, false, 100.0f, false),
                                                      new FloatRange("90 or above", 90.0f, true, 100.0f, false),
                                                      new FloatRange("over 1000", 1000.0f, false, Float.POSITIVE_INFINITY, false)));

    RangeAccumulator a = new RangeAccumulator(fsp, r);
    
    FacetsCollector fc = FacetsCollector.create(a);

    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);
    List<FacetResult> result = fc.getFacetResults();
    assertEquals(1, result.size());
    assertEquals("field (0)\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n", FacetTestUtils.toSimpleString(result.get(0)));
    
    r.close();
    d.close();
  }

  public void testRandomLongs() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    int numDocs = atLeast(1000);
    long[] values = new long[numDocs];
    for(int i=0;i<numDocs;i++) {
      Document doc = new Document();
      long v = random().nextLong();
      values[i] = v;
      doc.add(new NumericDocValuesField("field", v));
      doc.add(new LongField("field", v, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);
    
    int numIters = atLeast(10);
    for(int iter=0;iter<numIters;iter++) {
      if (VERBOSE) {
        System.out.println("TEST: iter=" + iter);
      }
      int numRange = _TestUtil.nextInt(random(), 1, 5);
      LongRange[] ranges = new LongRange[numRange];
      int[] expectedCounts = new int[numRange];
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        long min = random().nextLong();
        long max = random().nextLong();
        if (min > max) {
          long x = min;
          min = max;
          max = x;
        }
        boolean minIncl = random().nextBoolean();
        boolean maxIncl = random().nextBoolean();
        ranges[rangeID] = new LongRange("r" + rangeID, min, minIncl, max, maxIncl);

        // Do "slow but hopefully correct" computation of
        // expected count:
        for(int i=0;i<numDocs;i++) {
          boolean accept = true;
          if (minIncl) {
            accept &= values[i] >= min;
          } else {
            accept &= values[i] > min;
          }
          if (maxIncl) {
            accept &= values[i] <= max;
          } else {
            accept &= values[i] < max;
          }
          if (accept) {
            expectedCounts[rangeID]++;
          }
        }
      }

      FacetSearchParams fsp = new FacetSearchParams(new RangeFacetRequest<LongRange>("field", ranges));
      FacetsCollector fc = FacetsCollector.create(new RangeAccumulator(fsp, r));
      s.search(new MatchAllDocsQuery(), fc);
      List<FacetResult> results = fc.getFacetResults();
      assertEquals(1, results.size());
      List<FacetResultNode> nodes = results.get(0).getFacetResultNode().subResults;
      assertEquals(numRange, nodes.size());
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        if (VERBOSE) {
          System.out.println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID]);
        }
        FacetResultNode subNode = nodes.get(rangeID);
        assertEquals("field/r" + rangeID, subNode.label.toString('/'));
        assertEquals(expectedCounts[rangeID], (int) subNode.value);

        LongRange range = (LongRange) ((RangeFacetRequest) results.get(0).getFacetRequest()).ranges[rangeID];

        // Test drill-down:
        DrillDownQuery ddq = new DrillDownQuery(FacetIndexingParams.DEFAULT);
        ddq.add("field", NumericRangeQuery.newLongRange("field", range.min, range.max, range.minInclusive, range.maxInclusive));
        assertEquals(expectedCounts[rangeID], s.search(ddq, 10).totalHits);
      }
    }

    r.close();
    dir.close();
  }

  public void testRandomFloats() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    int numDocs = atLeast(1000);
    float[] values = new float[numDocs];
    for(int i=0;i<numDocs;i++) {
      Document doc = new Document();
      float v = random().nextFloat();
      values[i] = v;
      doc.add(new FloatDocValuesField("field", v));
      doc.add(new FloatField("field", v, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);
    
    int numIters = atLeast(10);
    for(int iter=0;iter<numIters;iter++) {
      if (VERBOSE) {
        System.out.println("TEST: iter=" + iter);
      }
      int numRange = _TestUtil.nextInt(random(), 1, 5);
      FloatRange[] ranges = new FloatRange[numRange];
      int[] expectedCounts = new int[numRange];
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        float min = random().nextFloat();
        float max = random().nextFloat();
        if (min > max) {
          float x = min;
          min = max;
          max = x;
        }
        boolean minIncl = random().nextBoolean();
        boolean maxIncl = random().nextBoolean();
        ranges[rangeID] = new FloatRange("r" + rangeID, min, minIncl, max, maxIncl);

        // Do "slow but hopefully correct" computation of
        // expected count:
        for(int i=0;i<numDocs;i++) {
          boolean accept = true;
          if (minIncl) {
            accept &= values[i] >= min;
          } else {
            accept &= values[i] > min;
          }
          if (maxIncl) {
            accept &= values[i] <= max;
          } else {
            accept &= values[i] < max;
          }
          if (accept) {
            expectedCounts[rangeID]++;
          }
        }
      }

      FacetSearchParams fsp = new FacetSearchParams(new RangeFacetRequest<FloatRange>("field", ranges));
      FacetsCollector fc = FacetsCollector.create(new RangeAccumulator(fsp, r));
      s.search(new MatchAllDocsQuery(), fc);
      List<FacetResult> results = fc.getFacetResults();
      assertEquals(1, results.size());
      List<FacetResultNode> nodes = results.get(0).getFacetResultNode().subResults;
      assertEquals(numRange, nodes.size());
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        if (VERBOSE) {
          System.out.println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID]);
        }
        FacetResultNode subNode = nodes.get(rangeID);
        assertEquals("field/r" + rangeID, subNode.label.toString('/'));
        assertEquals(expectedCounts[rangeID], (int) subNode.value);

        FloatRange range = (FloatRange) ((RangeFacetRequest) results.get(0).getFacetRequest()).ranges[rangeID];

        // Test drill-down:
        DrillDownQuery ddq = new DrillDownQuery(FacetIndexingParams.DEFAULT);
        ddq.add("field", NumericRangeQuery.newFloatRange("field", range.min, range.max, range.minInclusive, range.maxInclusive));
        assertEquals(expectedCounts[rangeID], s.search(ddq, 10).totalHits);
      }
    }

    r.close();
    dir.close();
  }

  public void testRandomDoubles() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    int numDocs = atLeast(1000);
    double[] values = new double[numDocs];
    for(int i=0;i<numDocs;i++) {
      Document doc = new Document();
      double v = random().nextDouble();
      values[i] = v;
      doc.add(new DoubleDocValuesField("field", v));
      doc.add(new DoubleField("field", v, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);
    
    int numIters = atLeast(10);
    for(int iter=0;iter<numIters;iter++) {
      if (VERBOSE) {
        System.out.println("TEST: iter=" + iter);
      }
      int numRange = _TestUtil.nextInt(random(), 1, 5);
      DoubleRange[] ranges = new DoubleRange[numRange];
      int[] expectedCounts = new int[numRange];
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        double min = random().nextDouble();
        double max = random().nextDouble();
        if (min > max) {
          double x = min;
          min = max;
          max = x;
        }
        boolean minIncl = random().nextBoolean();
        boolean maxIncl = random().nextBoolean();
        ranges[rangeID] = new DoubleRange("r" + rangeID, min, minIncl, max, maxIncl);

        // Do "slow but hopefully correct" computation of
        // expected count:
        for(int i=0;i<numDocs;i++) {
          boolean accept = true;
          if (minIncl) {
            accept &= values[i] >= min;
          } else {
            accept &= values[i] > min;
          }
          if (maxIncl) {
            accept &= values[i] <= max;
          } else {
            accept &= values[i] < max;
          }
          if (accept) {
            expectedCounts[rangeID]++;
          }
        }
      }

      FacetSearchParams fsp = new FacetSearchParams(new RangeFacetRequest<DoubleRange>("field", ranges));
      FacetsCollector fc = FacetsCollector.create(new RangeAccumulator(fsp, r));
      s.search(new MatchAllDocsQuery(), fc);
      List<FacetResult> results = fc.getFacetResults();
      assertEquals(1, results.size());
      List<FacetResultNode> nodes = results.get(0).getFacetResultNode().subResults;
      assertEquals(numRange, nodes.size());
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        if (VERBOSE) {
          System.out.println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID]);
        }
        FacetResultNode subNode = nodes.get(rangeID);
        assertEquals("field/r" + rangeID, subNode.label.toString('/'));
        assertEquals(expectedCounts[rangeID], (int) subNode.value);

        DoubleRange range = (DoubleRange) ((RangeFacetRequest) results.get(0).getFacetRequest()).ranges[rangeID];

        // Test drill-down:
        DrillDownQuery ddq = new DrillDownQuery(FacetIndexingParams.DEFAULT);
        ddq.add("field", NumericRangeQuery.newDoubleRange("field", range.min, range.max, range.minInclusive, range.maxInclusive));
        assertEquals(expectedCounts[rangeID], s.search(ddq, 10).totalHits);
      }
    }

    r.close();
    dir.close();
  }
}

