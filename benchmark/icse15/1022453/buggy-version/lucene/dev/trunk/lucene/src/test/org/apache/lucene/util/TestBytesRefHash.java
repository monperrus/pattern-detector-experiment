package org.apache.lucene.util;

/**
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

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

import org.apache.lucene.util.BytesRefHash.MaxBytesLengthExceededException;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class TestBytesRefHash extends LuceneTestCase {

  BytesRefHash hash;
  ByteBlockPool pool;
  
  /**
   */
  @Before
  public void setUp() throws Exception {
    super.setUp();
    pool = newPool();
    hash = newHash(pool);
  }
  
  private ByteBlockPool newPool(){
    return  random.nextBoolean() && pool != null ? pool
        : new ByteBlockPool(new RecyclingByteBlockAllocator(ByteBlockPool.BYTE_BLOCK_SIZE, random.nextInt(25)));
  }
  
  private BytesRefHash newHash(ByteBlockPool blockPool) {
    final int initSize = 2 << 1 + random.nextInt(5);
    return random.nextBoolean() ? new BytesRefHash(blockPool) : new BytesRefHash(
        blockPool, initSize, new BytesRefHash.DirectBytesStartArray(initSize));
  }

  /**
   * Test method for {@link org.apache.lucene.util.BytesRefHash#size()}.
   */
  @Test
  public void testSize() {
    BytesRef ref = new BytesRef();
    for (int j = 0; j < 2 * RANDOM_MULTIPLIER; j++) {
      final int mod = 1+random.nextInt(39);
      for (int i = 0; i < 797; i++) {
        String str;
        do {
          str = _TestUtil.randomRealisticUnicodeString(random, 1000);
        } while (str.length() == 0);
        ref.copy(str);
        int count = hash.size();
        int key = hash.add(ref);
        if (key < 0)
          assertEquals(hash.size(), count);
        else
          assertEquals(hash.size(), count + 1);
        if(i % mod == 0) {
          hash.clear();
          assertEquals(0, hash.size());
          hash.reinit();
        }
      }
    }
  }

  /**
   * Test method for
   * {@link org.apache.lucene.util.BytesRefHash#get(org.apache.lucene.util.BytesRefHash.Entry)}
   * .
   */
  @Test
  public void testGet() {
    BytesRef ref = new BytesRef();
    for (int j = 0; j < 2 * RANDOM_MULTIPLIER; j++) {
      Map<String, Integer> strings = new HashMap<String, Integer>();
      for (int i = 0; i < 797; i++) {
        String str;
        do {
          str = _TestUtil.randomRealisticUnicodeString(random, 1000);
        } while (str.length() == 0);
        ref.copy(str);
        int count = hash.size();
        int key = hash.add(ref);
        if (key >= 0) {
          assertNull(strings.put(str, Integer.valueOf(key)));
          assertEquals(i, key);
          assertEquals(hash.size(), count + 1);
        } else {
          assertTrue((-key)-1 < count);
          assertEquals(hash.size(), count);
        }
      }
      for (Entry<String, Integer> entry : strings.entrySet()) {
        ref.copy(entry.getKey());
        assertEquals(ref, hash.get(entry.getValue().intValue()));
      }
      hash.clear();
      assertEquals(0, hash.size());
      hash.reinit();
    }
  }

  /**
   * Test method for {@link org.apache.lucene.util.BytesRefHash#compact()}.
   */
  @Test
  public void testCompact() {
    BytesRef ref = new BytesRef();
    for (int j = 0; j < 2 * RANDOM_MULTIPLIER; j++) {
      final int size = 797;
      BitSet bits = new BitSet(size);
      for (int i = 0; i < size; i++) {
        String str;
        do {
          str = _TestUtil.randomRealisticUnicodeString(random, 1000);
        } while (str.length() == 0);
        ref.copy(str);
        bits.set(hash.add(ref));

      }
      assertEquals(hash.size(), bits.cardinality());
      int[] compact = hash.compact();
      assertTrue(size < compact.length);
      for (int i = 0; i < size; i++) {
        bits.set(compact[i], false);
      }
      assertEquals(0, bits.cardinality());
      hash.clear();
      assertEquals(0, hash.size());
      hash.reinit();
    }
  }

  /**
   * Test method for
   * {@link org.apache.lucene.util.BytesRefHash#sort(java.util.Comparator)}.
   */
  @Test
  public void testSort() {
    BytesRef ref = new BytesRef();
    for (int j = 0; j < 2 * RANDOM_MULTIPLIER; j++) {
      SortedSet<String> strings = new TreeSet<String>();
      for (int i = 0; i < 797; i++) {
        String str;
        do {
          str = _TestUtil.randomRealisticUnicodeString(random, 1000);
        } while (str.length() == 0);
        ref.copy(str);
        hash.add(ref);
        strings.add(str);
      }
      int[] sort = hash.sort(BytesRef.getUTF8SortedAsUTF16Comparator());
      assertTrue(strings.size() < sort.length);
      int i = 0;
      for (String string : strings) {
        ref.copy(string);
        assertEquals(ref, hash.get(sort[i++]));
      }
      hash.clear();
      assertEquals(0, hash.size());
      hash.reinit();

    }
  }

  /**
   * Test method for
   * {@link org.apache.lucene.util.BytesRefHash#add(org.apache.lucene.util.BytesRef)}
   * .
   */
  @Test
  public void testAdd() {
    BytesRef ref = new BytesRef();
    for (int j = 0; j < 2 * RANDOM_MULTIPLIER; j++) {
      Set<String> strings = new HashSet<String>();
      for (int i = 0; i < 797; i++) {
        String str;
        do {
          str = _TestUtil.randomRealisticUnicodeString(random, 1000);
        } while (str.length() == 0);
        ref.copy(str);
        int count = hash.size();
        int key = hash.add(ref);

        if (key >=0) {
          assertTrue(strings.add(str));
          assertEquals(i, key);
          assertEquals(hash.size(), count + 1);
        } else {
          assertFalse(strings.add(str));
          assertTrue((-key)-1 < count);
          assertEquals(str, hash.get((-key)-1).utf8ToString());
          assertEquals(count, hash.size());
        }
      }
      
      assertAllIn(strings, hash);
      hash.clear();
      assertEquals(0, hash.size());
      hash.reinit();
    }
  }

  @Test(expected = MaxBytesLengthExceededException.class)
  public void testLargeValue() {
    int[] sizes = new int[] { random.nextInt(5),
        ByteBlockPool.BYTE_BLOCK_SIZE - 33 + random.nextInt(31),
        ByteBlockPool.BYTE_BLOCK_SIZE - 1 + random.nextInt(37) };
    BytesRef ref = new BytesRef();
    for (int i = 0; i < sizes.length; i++) {
      ref.bytes = new byte[sizes[i]];
      ref.offset = 0;
      ref.length = sizes[i];
      try {
        assertEquals(i, hash.add(ref));
      } catch (MaxBytesLengthExceededException e) {
        if (i < sizes.length - 1)
          fail("unexpected exception at size: " + sizes[i]);
        throw e;
      }
    }
  }
  
  /**
   * Test method for
   * {@link org.apache.lucene.util.BytesRefHash#addByPoolOffset(int)}
   * .
   */
  @Test
  public void testAddByPoolOffset() {
    BytesRef ref = new BytesRef();
    BytesRefHash offsetHash = newHash(pool);
    for (int j = 0; j < 2 * RANDOM_MULTIPLIER; j++) {
      Set<String> strings = new HashSet<String>();
      for (int i = 0; i < 797; i++) {
        String str;
        do {
          str = _TestUtil.randomRealisticUnicodeString(random, 1000);
        } while (str.length() == 0);
        ref.copy(str);
        int count = hash.size();
        int key = hash.add(ref);

        if (key >= 0) {
          assertTrue(strings.add(str));
          assertEquals(i, key);
          assertEquals(hash.size(), count + 1);
          int offsetKey = offsetHash.addByPoolOffset(hash.byteStart(key));
          assertEquals(i, offsetKey);
          assertEquals(offsetHash.size(), count + 1);
        } else {
          assertFalse(strings.add(str));
          assertTrue((-key)-1 < count);
          assertEquals(str, hash.get((-key)-1).utf8ToString());
          assertEquals(count, hash.size());
          int offsetKey = offsetHash.addByPoolOffset(hash.byteStart((-key)-1));
          assertTrue((-offsetKey)-1 < count);
          assertEquals(str, hash.get((-offsetKey)-1).utf8ToString());
          assertEquals(count, hash.size());
        }
      }
      
      assertAllIn(strings, hash);
      for (String string : strings) {
        ref.copy(string);
        int key = hash.add(ref);
        BytesRef bytesRef = offsetHash.get((-key)-1);
        assertEquals(ref, bytesRef);
      }

      hash.clear();
      assertEquals(0, hash.size());
      offsetHash.clear();
      assertEquals(0, offsetHash.size());
      hash.reinit(); // init for the next round
      offsetHash.reinit();
    }
  }
  
  private void assertAllIn(Set<String> strings, BytesRefHash hash) {
    BytesRef ref = new BytesRef();
    int count = hash.size();
    for (String string : strings) {
      ref.copy(string);
      int key  =  hash.add(ref); // add again to check duplicates
      assertEquals(string, hash.get((-key)-1).utf8ToString());
      assertEquals(count, hash.size());
      assertTrue("key: " + key + " count: " + count + " string: " + string,
          key < count);
    }
  }


}
