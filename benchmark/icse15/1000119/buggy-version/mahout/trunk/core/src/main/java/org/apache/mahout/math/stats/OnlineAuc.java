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

package org.apache.mahout.math.stats;

import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.Vector;

import java.util.Random;

/**
 * Computes a running estimate of AUC (see http://en.wikipedia.org/wiki/Receiver_operating_characteristic).
 * <p/>
 * Since AUC is normally a global property of labeled scores, it is almost always computed in a
 * batch fashion.  The probabilistic definition (the probability that a random element of one set
 * has a higher score than a random element of another set) gives us a way to estimate this
 * on-line.
 */
public class OnlineAuc {

  enum ReplacementPolicy {
    FIFO, FAIR, RANDOM
  }

  // increasing this to 100 causes very small improvements in accuracy.  Decreasing it to 2
  // causes substantial degradation for the FAIR and RANDOM policies, but almost no change
  // for the FIFO policy
  public static final int HISTORY = 10;

  // FIFO has distinctly the best properties as a policy.  See OnlineAucTest for details
  private ReplacementPolicy policy = ReplacementPolicy.FIFO;
  private transient Random random = org.apache.mahout.common.RandomUtils.getRandom();
  private final Matrix scores;
  private final Vector averages;
  private final Vector samples;

  public OnlineAuc() {
    int numCategories = 2;
    scores = new DenseMatrix(numCategories, HISTORY);
    scores.assign(Double.NaN);
    averages = new DenseVector(numCategories);
    averages.assign(0.5);
    samples = new DenseVector(numCategories);
  }

  public double addSample(int category, double score) {
    int n = (int) samples.get(category);
    if (n < HISTORY) {
      scores.set(category, n, score);
    } else {
      switch (policy) {
        case FIFO:
          scores.set(category, n % HISTORY, score);
          break;
        case FAIR:
          int j1 = random.nextInt(n + 1);
          if (j1 < HISTORY) {
            scores.set(category, j1, score);
          }
          break;
        case RANDOM:
          int j2 = random.nextInt(HISTORY);
          scores.set(category, j2, score);
          break;
      }
    }

    samples.set(category, n + 1);

    if (samples.minValue() >= 1) {
      // compare to previous scores for other category
      Vector row = scores.viewRow(1 - category);
      double m = 0.0;
      double count = 0.0;
      for (Vector.Element element : row) {
        double v = element.get();
        if (Double.isNaN(v)) {
          continue;
        }
        count++;
        if (score > v) {
          m++;
        } else if (score < v) {
          // m += 0
        } else if (score == v) {
          m += 0.5;
        }
      }
      averages.set(category, averages.get(category) + (m / count - averages.get(category)) / samples.get(category));
    }
    return auc();
  }

  public double auc() {
    // return an unweighted average of all averages.
    return (1 - averages.get(0) + averages.get(1)) / 2;
  }

  public void setPolicy(ReplacementPolicy policy) {
    this.policy = policy;
  }
}
