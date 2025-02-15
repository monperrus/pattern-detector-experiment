  Merged /lucene/dev/trunk/lucene/core:r1356435-1356437
package org.apache.lucene.spatial.query;

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

import com.spatial4j.core.exception.InvalidSpatialArgument;

import java.io.Serializable;
import java.util.*;

/**
 * A clause that compares a stored geometry to a supplied geometry.
 *
 * @see <a href="http://edndoc.esri.com/arcsde/9.1/general_topics/understand_spatial_relations.htm">
 *   ESRI's docs on spatial relations</a>
 * @see <a href="http://docs.geoserver.org/latest/en/user/filter/ecql_reference.html#spatial-predicate">
 *   GeoServer ECQL Spatial Predicates</a>
 *
 * @lucene.experimental
 */
public class SpatialOperation implements Serializable {
  // Private registry
  private static final Map<String, SpatialOperation> registry = new HashMap<String, SpatialOperation>();
  private static final List<SpatialOperation> list = new ArrayList<SpatialOperation>();

  // Geometry Operations
  public static final SpatialOperation BBoxIntersects = new SpatialOperation("BBoxIntersects", true, false, false);
  public static final SpatialOperation BBoxWithin     = new SpatialOperation("BBoxWithin", true, false, false);
  public static final SpatialOperation Contains       = new SpatialOperation("Contains", true, true, false);
  public static final SpatialOperation Intersects     = new SpatialOperation("Intersects", true, false, false);
  public static final SpatialOperation IsEqualTo      = new SpatialOperation("IsEqualTo", false, false, false);
  public static final SpatialOperation IsDisjointTo   = new SpatialOperation("IsDisjointTo", false, false, false);
  public static final SpatialOperation IsWithin       = new SpatialOperation("IsWithin", true, false, true);
  public static final SpatialOperation Overlaps       = new SpatialOperation("Overlaps", true, false, true);

  // Member variables
  private final boolean scoreIsMeaningful;
  private final boolean sourceNeedsArea;
  private final boolean targetNeedsArea;
  private final String name;

  protected SpatialOperation(String name, boolean scoreIsMeaningful, boolean sourceNeedsArea, boolean targetNeedsArea) {
    this.name = name;
    this.scoreIsMeaningful = scoreIsMeaningful;
    this.sourceNeedsArea = sourceNeedsArea;
    this.targetNeedsArea = targetNeedsArea;
    registry.put(name, this);
    registry.put(name.toUpperCase(Locale.US), this);
    list.add( this );
  }

  public static SpatialOperation get( String v ) {
    SpatialOperation op = registry.get( v );
    if( op == null ) {
      op = registry.get(v.toUpperCase(Locale.US));
    }
    if( op == null ) {
      throw new InvalidSpatialArgument("Unknown Operation: " + v );
    }
    return op;
  }

  public static List<SpatialOperation> values() {
    return list;
  }

  public static boolean is( SpatialOperation op, SpatialOperation ... tst ) {
    for( SpatialOperation t : tst ) {
      if( op == t ) {
        return true;
      }
    }
    return false;
  }


  // ================================================= Getters / Setters =============================================

  public boolean isScoreIsMeaningful() {
    return scoreIsMeaningful;
  }

  public boolean isSourceNeedsArea() {
    return sourceNeedsArea;
  }

  public boolean isTargetNeedsArea() {
    return targetNeedsArea;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
