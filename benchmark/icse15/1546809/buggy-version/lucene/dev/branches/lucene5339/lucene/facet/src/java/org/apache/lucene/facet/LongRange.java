  + native
package org.apache.lucene.facet;

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

import org.apache.lucene.document.NumericDocValuesField; // javadocs

/** Represents a range over long values indexed as {@link
 *  NumericDocValuesField}.  */
public final class LongRange extends Range {
  private final long minIncl;
  private final long maxIncl;

  public final long min;
  public final long max;
  public final boolean minInclusive;
  public final boolean maxInclusive;

  // TODO: can we require fewer args? (same for
  // Double/FloatRange too)

  /** Create a LongRange. */
  public LongRange(String label, long min, boolean minInclusive, long max, boolean maxInclusive) {
    super(label);
    this.min = min;
    this.max = max;
    this.minInclusive = minInclusive;
    this.maxInclusive = maxInclusive;

    if (!minInclusive && min != Long.MAX_VALUE) {
      min++;
    }

    if (!maxInclusive && max != Long.MIN_VALUE) {
      max--;
    }

    this.minIncl = min;
    this.maxIncl = max;
  }

  @Override
  public boolean accept(long value) {
    return value >= minIncl && value <= maxIncl;
  }
}
