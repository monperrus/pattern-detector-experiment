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

package org.apache.mahout.ga.watchmaker.cd.tool;

import org.apache.hadoop.io.Text;
import org.apache.mahout.common.MahoutTestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class ToolCombinerTest extends MahoutTestCase {

  public void testCreateDescriptionNumerical() throws Exception {
    ToolCombiner combiner = new ToolCombiner();

    char[] descriptors = { 'I', 'N', 'C' };
    combiner.configure(descriptors);

    List<Text> values = asList("0", "10", "-32", "0.5", "-30");
    String descriptor = combiner.createDescription(1, values.iterator());

    assertEquals("-32.0,10.0", descriptor);
  }

  public void testCreateDescriptionIgnored() throws Exception {
    ToolCombiner combiner = new ToolCombiner();

    char[] descriptors = { 'I', 'N', 'C' };
    combiner.configure(descriptors);

    try {
      combiner.createDescription(0, null);
      fail("Should throw a RuntimeException");
    } catch (RuntimeException e) {

    }
  }

  public void testCreateDescriptionNominal() throws Exception {
    ToolCombiner combiner = new ToolCombiner();

    char[] descriptors = { 'I', 'N', 'C' };
    combiner.configure(descriptors);

    List<Text> values = asList("val1", "val2", "val1", "val3", "val2");
    String descriptor = combiner.createDescription(2, values.iterator());

    StringTokenizer tokenizer = new StringTokenizer(descriptor, ",");
    int nbvalues = 0;
    while (tokenizer.hasMoreTokens()) {
      String value = tokenizer.nextToken().trim();
      if (!"val1".equals(value) && !"val2".equals(value)
          && !"val3".equals(value)) {
        fail("Incorrect value : " + value);
      }
      nbvalues++;
    }
    assertEquals(3, nbvalues);
  }

  static List<Text> asList(String... strings) {
    List<Text> values = new ArrayList<Text>();

    for (String value : strings) {
      values.add(new Text(value));
    }
    return values;
  }
}
