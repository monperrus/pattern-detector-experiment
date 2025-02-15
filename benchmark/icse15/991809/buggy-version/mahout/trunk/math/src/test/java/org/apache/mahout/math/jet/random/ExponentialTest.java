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

package org.apache.mahout.math.jet.random;

import org.apache.commons.math.ConvergenceException;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.jet.random.engine.MersenneTwister;
import org.junit.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * Created by IntelliJ IDEA. User: tdunning Date: Aug 31, 2010 Time: 7:14:19 PM To change this
 * template use File | Settings | File Templates.
 */
public class ExponentialTest extends DistributionTest {
  @Test
  public void testCdf() {
    Exponential dist = new Exponential(5.0, RandomUtils.getRandom());
    for (int i = 0; i < 1000; i++) {
      double x = i / 50.0;
      assertEquals(1 - Math.exp(-x * 5.0), dist.cdf(x), 1e-9);
    }
  }

  @Test
  public void testPdf() {
    checkPdf(new Exponential(13.0, null), 13.0);
  }

  private void checkPdf(Exponential dist, double lambda) {
    assertEquals(0, dist.pdf(-1), 0);
    double sum = 0;
    double dx = 0.001 / lambda;
    for (double x = 0; x < 20/lambda;x+=dx) {
      sum += x * dist.pdf(x) * dx;
      assertEquals(Math.exp(-x * lambda) * lambda, dist.pdf(x), 1e-9);
    }
    assertEquals(1 / lambda, sum, 1e-6 / lambda);
  }

  @Test
  public void testSetState() {
    Exponential dist = new Exponential(13.0, null);
    for (double lambda = 0.1; lambda < 1000; lambda *= 1.3) {
      dist.setState(lambda);
      checkPdf(dist, lambda);
    }
  }

  @Test
  public void testNextDouble() throws ConvergenceException, FunctionEvaluationException {
    double[] x = {-0.01, 0.1053605, 0.2231436, 0.3566749, 0.5108256, 0.6931472, 0.9162907, 1.2039728, 1.6094379, 2.3025851};
    Exponential dist = new Exponential(1, RandomUtils.getRandom());
    for (double lambda : new double[]{13.0, 0.02, 1.6}) {
      dist.setState(lambda);
      checkEmpiricalDistribution(dist, 10000, lambda);
      checkDistribution(dist, x, 0, 1 / lambda, 10000);
    }
  }

  private void checkEmpiricalDistribution(Exponential dist, int n, double lambda) {
    double[] x = new double[n];
    for (int i = 0; i < n; i++) {
      x[i] = dist.nextDouble();
    }
    Arrays.sort(x);
    for (int i = 0; i < n; i++) {
      double cumulative = (double) i / (n - 1);
      assertEquals(String.format("lambda = %.3f", lambda), cumulative, dist.cdf(x[i]), 0.02);
    }
  }

  @Test
  public void testToString() {
    assertEquals("org.apache.mahout.math.jet.random.Exponential(3.1000)", new Exponential(3.1, null).toString());
    Locale.setDefault(Locale.GERMAN);
    assertEquals("org.apache.mahout.math.jet.random.Exponential(3.1000)", new Exponential(3.1, null).toString());
  }
}
