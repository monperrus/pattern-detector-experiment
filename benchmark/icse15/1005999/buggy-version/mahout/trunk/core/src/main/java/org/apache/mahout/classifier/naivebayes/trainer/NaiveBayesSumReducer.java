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

package org.apache.mahout.classifier.naivebayes.trainer;

import java.io.IOException;

import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorWritable;

/**
 * Can also be used as a local Combiner. This accumulates all the features and the weights and sums them up.
 */
public class NaiveBayesSumReducer extends Reducer<WritableComparable<?>, VectorWritable, WritableComparable<?>, VectorWritable> {

  @Override
  protected void reduce(WritableComparable<?> key, Iterable<VectorWritable> values, Context context)
      throws IOException, InterruptedException {
    Vector vector = null;
    for (VectorWritable v : values) {
      if (vector == null) {
        vector = v.get();
      } else {
        v.get().addTo(vector);
      }
    }
    context.write(key, new VectorWritable(vector));
  }

}
