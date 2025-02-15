  Merged /lucene/dev/trunk/solr/core:r1565572
package org.apache.solr.common.cloud;

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

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.Hash;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

//
// user!uniqueid
// app!user!uniqueid
// user/4!uniqueid
// app/2!user/4!uniqueid
//
public class CompositeIdRouter extends HashBasedRouter {
  public static final String NAME = "compositeId";

  public static final String SEPARATOR = "!";

  // separator used to optionally specify number of bits to allocate toward first part.
  public static final int bitsSeparator = '/';
  private int bits = 16;

  @Override
  public int sliceHash(String id, SolrInputDocument doc, SolrParams params, DocCollection collection) {
    String shardFieldName = getRouteField(collection);
    if (shardFieldName != null && doc != null) {
      Object o = doc.getFieldValue(shardFieldName);
      if (o == null)
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "No value for :" + shardFieldName + ". Unable to identify shard");
      id = o.toString();
    }
    if (id.indexOf(SEPARATOR) < 0) {
      return Hash.murmurhash3_x86_32(id, 0, id.length(), 0);
    }

    return new KeyParser(id).getHash();
  }


  /**
   * Get Range for a given CompositeId based route key
   *
   * @param routeKey to return Range for
   * @return Range for given routeKey
   */
  public Range keyHashRange(String routeKey) {
    if (routeKey.indexOf(SEPARATOR) < 0) {
      int hash = sliceHash(routeKey, null, null, null);
      return new Range(hash, hash);
    }

    return new KeyParser(routeKey).getRange();
  }

  @Override
  public Collection<Slice> getSearchSlicesSingle(String shardKey, SolrParams params, DocCollection collection) {
    if (shardKey == null) {
      // search across whole collection
      // TODO: this may need modification in the future when shard splitting could cause an overlap
      return collection.getActiveSlices();
    }
    String id = shardKey;

    if (shardKey.indexOf(SEPARATOR) < 0) {
      // shardKey is a simple id, so don't do a range
      return Collections.singletonList(hashToSlice(Hash.murmurhash3_x86_32(id, 0, id.length(), 0), collection));
    }

    Range completeRange = new KeyParser(id).getRange();

    List<Slice> targetSlices = new ArrayList<Slice>(1);
    for (Slice slice : collection.getActiveSlices()) {
      Range range = slice.getRange();
      if (range != null && range.overlaps(completeRange)) {
        targetSlices.add(slice);
      }
    }

    return targetSlices;
  }

  public List<Range> partitionRangeByKey(String key, Range range) {
    List<Range> result = new ArrayList<Range>(3);
    Range keyRange = keyHashRange(key);
    if (!keyRange.overlaps(range)) {
      throw new IllegalArgumentException("Key range does not overlap given range");
    }
    if (keyRange.equals(range)) {
      return Collections.singletonList(keyRange);
    } else if (keyRange.isSubsetOf(range)) {
      result.add(new Range(range.min, keyRange.min - 1));
      result.add(keyRange);
      result.add((new Range(keyRange.max + 1, range.max)));
    } else if (range.includes(keyRange.max)) {
      result.add(new Range(range.min, keyRange.max));
      result.add(new Range(keyRange.max + 1, range.max));
    } else {
      result.add(new Range(range.min, keyRange.min - 1));
      result.add(new Range(keyRange.min, range.max));
    }
    return result;
  }

  @Override
  public List<Range> partitionRange(int partitions, Range range) {
    int min = range.min;
    int max = range.max;

    assert max >= min;
    if (partitions == 0) return Collections.EMPTY_LIST;
    long rangeSize = (long) max - (long) min;
    long rangeStep = Math.max(1, rangeSize / partitions);

    List<Range> ranges = new ArrayList<Range>(partitions);

    long start = min;
    long end = start;

    // keep track of the idealized target to avoid accumulating rounding errors
    long targetStart = min;
    long targetEnd = targetStart;

    // Round to avoid splitting hash domains across ranges if such rounding is not significant.
    // With default bits==16, one would need to create more than 4000 shards before this
    // becomes false by default.
    int mask = 0x0000ffff;
    boolean round = rangeStep >= (1 << bits) * 16;

    while (end < max) {
      targetEnd = targetStart + rangeStep;
      end = targetEnd;

      if (round && ((end & mask) != mask)) {
        // round up or down?
        int increment = 1 << bits;  // 0x00010000
        long roundDown = (end | mask) - increment;
        long roundUp = (end | mask) + increment;
        if (end - roundDown < roundUp - end && roundDown > start) {
          end = roundDown;
        } else {
          end = roundUp;
        }
      }

      // make last range always end exactly on MAX_VALUE
      if (ranges.size() == partitions - 1) {
        end = max;
      }
      ranges.add(new Range((int) start, (int) end));
      start = end + 1L;
      targetStart = targetEnd + 1L;
    }

    return ranges;
  }

  /**
   * Helper class to calculate parts, masks etc for an id.
   */
  static class KeyParser {
    String key;
    int[] numBits;
    int[] hashes;
    int[] masks;
    boolean triLevel;
    int pieces;

    public KeyParser(String key) {
      String[] parts = key.split(SEPARATOR);
      this.key = key;
      pieces = parts.length;
      hashes = new int[pieces];
      numBits = new int[2];
      if (key.endsWith("!"))
        pieces++;
      if (pieces == 3) {
        numBits[0] = 8;
        numBits[1] = 8;
        triLevel = true;
      } else {
        numBits[0] = 16;
        triLevel = false;
      }

      for (int i = 0; i < parts.length; i++) {
        if (i < pieces - 1) {
          int commaIdx = parts[i].indexOf(bitsSeparator);

          if (commaIdx > 0) {
            numBits[i] = getNumBits(parts[i], commaIdx);
            parts[i] = parts[i].substring(0, commaIdx);
          }
        }
        hashes[i] = Hash.murmurhash3_x86_32(parts[i], 0, parts[i].length(), 0);
      }
      masks = getMasks();
    }

    Range getRange() {
      int lowerBound;
      int upperBound;

      if (triLevel) {
        lowerBound = hashes[0] & masks[0] | hashes[1] & masks[1];
        upperBound = lowerBound | masks[2];
      } else {
        lowerBound = hashes[0] & masks[0];
        upperBound = lowerBound | masks[1];
      }
      //  If the upper bits are 0xF0000000, the range we want to cover is
      //  0xF0000000 0xFfffffff

      if ((masks[0] == 0 && !triLevel) || (masks[0] == 0 && masks[1] == 0 && triLevel)) {
        // no bits used from first part of key.. the code above will produce 0x000000000->0xffffffff
        // which only works on unsigned space, but we're using signed space.
        lowerBound = Integer.MIN_VALUE;
        upperBound = Integer.MAX_VALUE;
      }
      Range r = new Range(lowerBound, upperBound);
      return r;
    }

    /**
     * Get bit masks for routing based on routing level
     */
    private int[] getMasks() {
      int[] masks;
      if (triLevel)
        masks = getBitMasks(numBits[0], numBits[1]);
      else
        masks = getBitMasks(numBits[0]);

      return masks;
    }

    private int[] getBitMasks(int firstBits, int secondBits) {
      // java can't shift 32 bits
      int[] masks = new int[3];
      masks[0] = firstBits == 0 ? 0 : (-1 << (32 - firstBits));
      masks[1] = (firstBits + secondBits) == 0 ? 0 : (-1 << (32 - firstBits - secondBits));
      masks[1] = masks[0] ^ masks[1];
      masks[2] = (firstBits + secondBits) == 32 ? 0 : ~(masks[0] | masks[1]);
      return masks;
    }

    private int getNumBits(String firstPart, int commaIdx) {
      int v = 0;
      for (int idx = commaIdx + 1; idx < firstPart.length(); idx++) {
        char ch = firstPart.charAt(idx);
        if (ch < '0' || ch > '9') return -1;
        v = v * 10 + (ch - '0');
      }
      return v > 32 ? -1 : v;
    }

    private int[] getBitMasks(int firstBits) {
      // java can't shift 32 bits
      int[] masks;
      masks = new int[2];
      masks[0] = firstBits == 0 ? 0 : (-1 << (32 - firstBits));
      masks[1] = firstBits == 32 ? 0 : (-1 >>> firstBits);
      return masks;
    }

    int getHash() {
      int result = hashes[0] & masks[0];

      for (int i = 1; i < pieces; i++)
        result = result | (hashes[i] & masks[i]);
      return result;
    }

  }
}
