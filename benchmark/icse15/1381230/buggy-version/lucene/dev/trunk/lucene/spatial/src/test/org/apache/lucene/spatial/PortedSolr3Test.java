package org.apache.lucene.spatial;

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

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;
import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.context.simple.SimpleSpatialContext;
import com.spatial4j.core.shape.Point;
import com.spatial4j.core.shape.Shape;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.TermQueryPrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Based off of Solr 3's SpatialFilterTest.
 */
public class PortedSolr3Test extends StrategyTestCase {

  @ParametersFactory
  public static Iterable<Object[]> parameters() {
    List<Object[]> ctorArgs = new ArrayList<Object[]>();

    SpatialContext ctx = SimpleSpatialContext.GEO_KM;
    SpatialPrefixTree grid;
    SpatialStrategy strategy;

    grid = new GeohashPrefixTree(ctx,12);
    strategy = new RecursivePrefixTreeStrategy(grid, "recursive_geohash");
    ctorArgs.add(new Object[]{new Param(strategy, "recursive_geohash")});

    grid = new QuadPrefixTree(ctx,25);
    strategy = new RecursivePrefixTreeStrategy(grid, "recursive_quad");
    ctorArgs.add(new Object[]{new Param(strategy, "recursive_quad")});

    grid = new GeohashPrefixTree(ctx,12);
    strategy = new TermQueryPrefixTreeStrategy(grid, "termquery_geohash");
    ctorArgs.add(new Object[]{new Param(strategy, "termquery_geohash")});

    return ctorArgs;
  }
  
  // this is a hack for clover!
  static class Param {
    SpatialStrategy strategy;
    String description;

    Param(SpatialStrategy strategy, String description) {
      this.strategy = strategy;
      this.description = description;
    }
    
    @Override
    public String toString() {
      return description;
    }
  }

//  private String fieldName;

  public PortedSolr3Test(@Name("strategy") Param param) {
    SpatialStrategy strategy = param.strategy;
    this.ctx = strategy.getSpatialContext();
    this.strategy = strategy;
  }

  private void setupDocs() throws IOException {
    super.deleteAll();
    adoc("1", "32.7693246, -79.9289094");
    adoc("2", "33.7693246, -80.9289094");
    adoc("3", "-32.7693246, 50.9289094");
    adoc("4", "-50.7693246, 60.9289094");
    adoc("5", "0,0");
    adoc("6", "0.1,0.1");
    adoc("7", "-0.1,-0.1");
    adoc("8", "0,179.9");
    adoc("9", "0,-179.9");
    adoc("10", "89.9,50");
    adoc("11", "89.9,-130");
    adoc("12", "-89.9,50");
    adoc("13", "-89.9,-130");
    commit();
  }


  @Test
  public void testIntersections() throws Exception {
    setupDocs();
    //Try some edge cases
    checkHitsCircle("1,1", 175, 3, 5, 6, 7);
    checkHitsCircle("0,179.8", 200, 2, 8, 9);
    checkHitsCircle("89.8, 50", 200, 2, 10, 11);//this goes over the north pole
    checkHitsCircle("-89.8, 50", 200, 2, 12, 13);//this goes over the south pole
    //try some normal cases
    checkHitsCircle("33.0,-80.0", 300, 2);
    //large distance
    checkHitsCircle("1,1", 5000, 3, 5, 6, 7);
    //Because we are generating a box based on the west/east longitudes and the south/north latitudes, which then
    //translates to a range query, which is slightly more inclusive.  Thus, even though 0.0 is 15.725 kms away,
    //it will be included, b/c of the box calculation.
    checkHitsBBox("0.1,0.1", 15, 2, 5, 6);
    //try some more
    deleteAll();
    adoc("14", "0,5");
    adoc("15", "0,15");
    //3000KM from 0,0, see http://www.movable-type.co.uk/scripts/latlong.html
    adoc("16", "18.71111,19.79750");
    adoc("17", "44.043900,-95.436643");
    commit();

    checkHitsCircle("0,0", 1000, 1, 14);
    checkHitsCircle("0,0", 2000, 2, 14, 15);
    checkHitsBBox("0,0", 3000, 3, 14, 15, 16);
    checkHitsCircle("0,0", 3001, 3, 14, 15, 16);
    checkHitsCircle("0,0", 3000.1, 3, 14, 15, 16);

    //really fine grained distance and reflects some of the vagaries of how we are calculating the box
    checkHitsCircle("43.517030,-96.789603", 109, 0);

    // falls outside of the real distance, but inside the bounding box
    checkHitsCircle("43.517030,-96.789603", 110, 0);
    checkHitsBBox("43.517030,-96.789603", 110, 1, 17);
  }

  /**
   * This test is similar to a Solr 3 spatial test.
   */
  @Test
  public void testDistanceOrder() throws IOException {
    adoc("100","1,2");
    adoc("101","4,-1");
    commit();

    //query closer to #100
    checkHitsOrdered("Intersects(Circle(3,4 d=1000))", "101", "100");
    //query closer to #101
    checkHitsOrdered("Intersects(Circle(4,0 d=1000))", "100", "101");
  }

  private void checkHitsOrdered(String spatialQ, String... ids) {
    SpatialArgs args = this.argsParser.parse(spatialQ,ctx);
    Query query = strategy.makeQuery(args);
    SearchResults results = executeQuery(query, 100);
    String[] resultIds = new String[results.numFound];
    int i = 0;
    for (SearchResult result : results.results) {
      resultIds[i++] = result.document.get("id");
    }
    assertArrayEquals("order matters",ids, resultIds);
  }

  //---- these are similar to Solr test methods
  
  private void adoc(String idStr, String shapeStr) throws IOException {
    Shape shape = ctx.readShape(shapeStr);
    addDocument(newDoc(idStr,shape));
  }

  @SuppressWarnings("unchecked")
  private Document newDoc(String id, Shape shape) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    for (Field f : strategy.createIndexableFields(shape)) {
      doc.add(f);
    }
    if (storeShape)
      doc.add(new StoredField(strategy.getFieldName(), ctx.toString(shape)));
    return doc;
  }

  private void checkHitsCircle(String ptStr, double dist, int assertNumFound, int... assertIds) {
    _checkHits(SpatialOperation.Intersects, ptStr, dist, assertNumFound, assertIds);
  }
  private void checkHitsBBox(String ptStr, double dist, int assertNumFound, int... assertIds) {
    _checkHits(SpatialOperation.BBoxIntersects, ptStr, dist, assertNumFound, assertIds);
  }

  @SuppressWarnings("unchecked")
  private void _checkHits(SpatialOperation op, String ptStr, double dist, int assertNumFound, int... assertIds) {
    Point pt = (Point) ctx.readShape(ptStr);
    Shape shape = ctx.makeCircle(pt,dist);

    SpatialArgs args = new SpatialArgs(op,shape);
    //args.setDistPrecision(0.025);
    Query query;
    if (random().nextBoolean()) {
      query = strategy.makeQuery(args);
    } else {
      query = new FilteredQuery(new MatchAllDocsQuery(),strategy.makeFilter(args));
    }
    SearchResults results = executeQuery(query, 100);
    assertEquals(""+shape,assertNumFound,results.numFound);
    if (assertIds != null) {
      Set<Integer> resultIds = new HashSet<Integer>();
      for (SearchResult result : results.results) {
        resultIds.add(Integer.valueOf(result.document.get("id")));
      }
      for (int assertId : assertIds) {
        assertTrue("has " + assertId, resultIds.contains(assertId));
      }
    }
  }

}
