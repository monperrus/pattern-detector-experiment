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

package org.apache.mahout.df.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.apache.mahout.common.MahoutTestCase;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.df.data.conditions.Condition;

public class DataTest extends MahoutTestCase {

  private static final int ATTRIBUTE_COUNT = 10;

  private static final int DATA_SIZE = 100;

  private Random rng;

  private Data data;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    rng = RandomUtils.getRandom();
    data = Utils.randomData(rng, ATTRIBUTE_COUNT, DATA_SIZE);
  }

  /**
   * Test method for
   * {@link org.apache.mahout.df.data.Data#subset(org.apache.mahout.df.data.conditions.Condition)}.
   */
  public void testSubset() {
    int n = 10;

    for (int nloop = 0; nloop < n; nloop++) {
      int attr = rng.nextInt(data.getDataset().nbAttributes());

      double[] values = data.values(attr);
      double value = values[rng.nextInt(values.length)];

      Data eSubset = data.subset(Condition.equals(attr, value));
      Data lSubset = data.subset(Condition.lesser(attr, value));
      Data gSubset = data.subset(Condition.greaterOrEquals(attr, value));

      for (int index = 0; index < DATA_SIZE; index++) {
        Instance instance = data.get(index);

        if (instance.get(attr) < value) {
          assertTrue(lSubset.contains(instance));
          assertFalse(eSubset.contains(instance));
          assertFalse(gSubset.contains(instance));
        } else if (instance.get(attr) == value) {
          assertFalse(lSubset.contains(instance));
          assertTrue(eSubset.contains(instance));
          assertTrue(gSubset.contains(instance));
        } else {
          assertFalse(lSubset.contains(instance));
          assertFalse(eSubset.contains(instance));
          assertTrue(gSubset.contains(instance));
        }
      }
    }
  }


  public void testValues() throws Exception {
    Data data = Utils.randomData(rng, ATTRIBUTE_COUNT, DATA_SIZE);

    for (int attr = 0; attr < data.getDataset().nbAttributes(); attr++) {
      double[] values = data.values(attr);

      // each value of the attribute should appear exactly one time in values
      for (int index = 0; index < DATA_SIZE; index++) {
        assertEquals(1, count(values, data.get(index).get(attr)));
      }
    }

  }

  private static int count(double[] values, double value) {
    int count = 0;
    for (double v : values) {
      if (v == value) {
        count++;
      }
    }
    return count;
  }

  public void testIdenticalTrue() throws Exception {
    // generate a small data, only to get the dataset
    Dataset dataset = Utils.randomData(rng, ATTRIBUTE_COUNT, 1).getDataset();
    
    // test empty data
    Data empty = new Data(dataset, new ArrayList<Instance>());
    assertTrue(empty.isIdentical());

    // test identical data, except for the labels
    Data identical = Utils.randomData(rng, ATTRIBUTE_COUNT, DATA_SIZE);
    Instance model = identical.get(0);
    for (int index = 1; index < DATA_SIZE; index++) {
      for (int attr = 0; attr < identical.getDataset().nbAttributes(); attr++) {
        identical.get(index).set(attr, model.get(attr));
      }
    }

    assertTrue(identical.isIdentical());
  }

  public void testIdenticalFalse() throws Exception {
    int n = 10;

    for (int nloop = 0; nloop < n; nloop++) {
      Data data = Utils.randomData(rng, ATTRIBUTE_COUNT, DATA_SIZE);

      // choose a random instance
      int index = rng.nextInt(DATA_SIZE);
      Instance instance = data.get(index);

      // change a random attribute
      int attr = rng.nextInt(data.getDataset().nbAttributes());
      instance.set(attr, instance.get(attr) + 1);

      assertFalse(data.isIdentical());
    }
  }

  public void testIdenticalLabelTrue() throws Exception {
    // generate a small data, only to get a dataset
    Dataset dataset = Utils.randomData(rng, ATTRIBUTE_COUNT, 1).getDataset();
    
    // test empty data
    Data empty = new Data(dataset, new ArrayList<Instance>());
    assertTrue(empty.identicalLabel());

    // test identical labels
    String descriptor = Utils.randomDescriptor(rng, ATTRIBUTE_COUNT);
    double[][] source = Utils.randomDoublesWithSameLabel(rng, descriptor,
            DATA_SIZE, rng.nextInt());
    String[] sData = Utils.double2String(source);
    
    dataset = DataLoader.generateDataset(descriptor, sData);
    Data data = DataLoader.loadData(dataset, sData);
    
    assertTrue(data.identicalLabel());
  }

  public void testIdenticalLabelFalse() throws Exception {
    int n = 10;

    for (int nloop = 0; nloop < n; nloop++) {
      String descriptor = Utils.randomDescriptor(rng, ATTRIBUTE_COUNT);
      int label = Utils.findLabel(descriptor);
      double[][] source = Utils.randomDoublesWithSameLabel(rng, descriptor,
              DATA_SIZE, rng.nextInt());
      // choose a random vector and change its label
      int index = rng.nextInt(DATA_SIZE);
      source[index][label]++;

      String[] sData = Utils.double2String(source);
      
      Dataset dataset = DataLoader.generateDataset(descriptor, sData);
      Data data = DataLoader.loadData(dataset, sData);

      assertFalse(data.identicalLabel());
    }
  }

  /**
   * Test method for
   * {@link org.apache.mahout.df.data.Data#bagging(java.util.Random)}.
   */
  public void testBagging() {
    Data bag = data.bagging(rng);

    // the bag should have the same size as the data
    assertEquals(data.size(), bag.size());

    // at least one element from the data should not be in the bag
    boolean found = false;
    for (int index = 0; index < data.size() && !found; index++) {
      found = !bag.contains(data.get(index));
    }
    
    assertTrue("some instances from data should not be in the bag", found);
  }

  /**
   * Test method for
   * {@link org.apache.mahout.df.data.Data#rsplit(java.util.Random, int)}.
   */
  public void testRsplit() {

    // rsplit should handle empty subsets
    Data source = data.clone();
    Data subset = source.rsplit(rng, 0);
    assertTrue("subset should be empty", subset.isEmpty());
    assertEquals("source.size is incorrect", DATA_SIZE, source.size());

    // rsplit should handle full size subsets
    source = data.clone();
    subset = source.rsplit(rng, DATA_SIZE);
    assertEquals("subset.size is incorrect", DATA_SIZE, subset.size());
    assertTrue("source should be empty", source.isEmpty());

    // random case
    int subsize = rng.nextInt(DATA_SIZE);
    source = data.clone();
    subset = source.rsplit(rng, subsize);
    assertEquals("subset.size is incorrect", subsize, subset.size());
    assertEquals("source.size is incorrect", DATA_SIZE - subsize, source.size());
  }

  public void testCountLabel() throws Exception {
    Data data = Utils.randomData(rng, ATTRIBUTE_COUNT, DATA_SIZE);
    int[] counts = new int[data.getDataset().nblabels()];

    int n = 10;

    for (int nloop = 0; nloop < n; nloop++) {
      Arrays.fill(counts, 0);
      data.countLabels(counts);
      
      for (int index=0;index<data.size();index++) {
        counts[data.get(index).getLabel()]--;
      }
      
      for (int label = 0; label < data.getDataset().nblabels(); label++) {
        assertEquals("Wrong label 'equals' count", 0, counts[0]);
      }
    }
  }

  public void testMajorityLabel() throws Exception {

    // all instances have the same label
    String descriptor = Utils.randomDescriptor(rng, ATTRIBUTE_COUNT);
    int label = Utils.findLabel(descriptor);

    int label1 = rng.nextInt();
    double[][] source = Utils.randomDoublesWithSameLabel(rng, descriptor, 100,
        label1);
    String[] sData = Utils.double2String(source);
    
    Dataset dataset = DataLoader.generateDataset(descriptor, sData);
    Data data = DataLoader.loadData(dataset, sData);

    int code1 = dataset.labelCode(Double.toString(label1));

    assertEquals(code1, data.majorityLabel(rng));

    // 51/100 vectors have label2
    int label2 = label1 + 1;
    int nblabel2 = 51;
    while (nblabel2 > 0) {
      double[] vector = source[rng.nextInt(100)];
      if (vector[label] != label2) {
        vector[label] = label2;
        nblabel2--;
      }
    }
    sData = Utils.double2String(source);
    dataset = DataLoader.generateDataset(descriptor, sData);
    data = DataLoader.loadData(dataset, sData);
    int code2 = dataset.labelCode(Double.toString(label2));

    // label2 should be the majority label
    assertEquals(code2, data.majorityLabel(rng));

    // 50 vectors with label1 and 50 vectors with label2
    do {
      double[] vector = source[rng.nextInt(100)];
      if (vector[label] == label2) {
        vector[label] = label1;
        break;
      }
    } while (true);
    sData = Utils.double2String(source);
    
    data = DataLoader.loadData(dataset, sData);
    code1 = dataset.labelCode(Double.toString(label1));
    code2 = dataset.labelCode(Double.toString(label2));

    // majorityLabel should return label1 and label2 at random
    boolean found1 = false;
    boolean found2 = false;
    for (int index = 0; index < 10 && (!found1 || !found2); index++) {
      int major = data.majorityLabel(rng);
      if (major == code1) {
        found1 = true;
      }
      if (major == code2) {
        found2 = true;
      }
    }
    assertTrue(found1 && found2);
  }

}
