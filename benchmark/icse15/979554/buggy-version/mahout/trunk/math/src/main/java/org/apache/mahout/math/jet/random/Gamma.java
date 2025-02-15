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

/*
Copyright � 1999 CERN - European Organization for Nuclear Research.
Permission to use, copy, modify, distribute and sell this software and its documentation for any purpose 
is hereby granted without fee, provided that the above copyright notice appear in all copies and 
that both that copyright notice and this permission notice appear in supporting documentation. 
CERN makes no representations about the suitability of this software for any purpose. 
It is provided "as is" without expressed or implied warranty.
*/
package org.apache.mahout.math.jet.random;

import org.apache.mahout.math.jet.random.engine.RandomEngine;
import org.apache.mahout.math.jet.stat.Probability;

public class Gamma extends AbstractContinousDistribution {
  // shape
  private double alpha;

  // rate
  private double beta;

  /**
   * Constructs a Gamma distribution with a given shape (alpha) and rate (beta).
   *
   * @param alpha The shape parameter.
   * @param beta The rate parameter.
   * @param randomGenerator The random number generator that generates bits for us.
   * @throws IllegalArgumentException if <tt>alpha &lt;= 0.0 || alpha &lt;= 0.0</tt>.
   */
  public Gamma(double alpha, double beta, RandomEngine randomGenerator) {
    this.alpha = alpha;
    this.beta = beta;
    setRandomGenerator(randomGenerator);
  }

  /**
   * Returns the cumulative distribution function.
   * @param x The end-point where the cumulation should end.
   */
  public double cdf(double x) {
    return Probability.gamma(alpha, beta, x);
  }

  /** Returns a random number from the distribution. */
  @Override
  public double nextDouble() {
    return nextDouble(alpha, beta);
  }

  /** Returns a random number from the distribution; bypasses the internal state.
   *                                                                *
   *    Gamma Distribution - Acceptance Rejection combined with     *
   *                         Acceptance Complement                  *
   *                                                                *
   ******************************************************************
   *                                                                *
   * FUNCTION:    - gds samples a random number from the standard   *
   *                gamma distribution with parameter  a > 0.       *
   *                Acceptance Rejection  gs  for  a < 1 ,          *
   *                Acceptance Complement gd  for  a >= 1 .         *
   * REFERENCES:  - J.H. Ahrens, U. Dieter (1974): Computer methods *
   *                for sampling from gamma, beta, Poisson and      *
   *                binomial distributions, Computing 12, 223-246.  *
   *              - J.H. Ahrens, U. Dieter (1982): Generating gamma *
   *                variates by a modified rejection technique,     *
   *                Communications of the ACM 25, 47-54.            *
   * SUBPROGRAMS: - drand(seed) ... (0,1)-Uniform generator with    *
   *                unsigned long integer *seed                     *
   *              - NORMAL(seed) ... Normal generator N(0,1).       *
   *                                                                *
   * @param beta  Scale parameter.
   * @param alpha   Shape parameter.
   * @return A gamma distributed sample.
   */
  public double nextDouble(double alpha, double beta) {
    if (alpha <= 0.0) {
      throw new IllegalArgumentException();
    }
    if (beta <= 0.0) {
      throw new IllegalArgumentException();
    }

    double gds;
    double b = 0.0;
    if (alpha < 1.0) { // CASE A: Acceptance rejection algorithm gs
      b = 1.0 + 0.36788794412 * alpha;              // Step 1
      while (true) {
        double p = b * randomGenerator.raw();
        if (p <= 1.0) {                       // Step 2. Case gds <= 1
          gds = Math.exp(Math.log(p) / alpha);
          if (Math.log(randomGenerator.raw()) <= -gds) {
            return (gds / beta);
          }
        } else {                                // Step 3. Case gds > 1
          gds = -Math.log((b - p) / alpha);
          if (Math.log(randomGenerator.raw()) <= ((alpha - 1.0) * Math.log(gds))) {
            return (gds / beta);
          }
        }
      }
    } else {        // CASE B: Acceptance complement algorithm gd (gaussian distribution, box muller transformation)
      double ss = 0.0;
      double s = 0.0;
      double d = 0.0;
      double aa = -1.0;
      if (alpha != aa) {                        // Step 1. Preparations
        aa = alpha;
        ss = alpha - 0.5;
        s = Math.sqrt(ss);
        d = 5.656854249 - 12.0 * s;
      }
      // Step 2. Normal deviate
      double v12;
      double v1;
      do {
        v1 = 2.0 * randomGenerator.raw() - 1.0;
        double v2 = 2.0 * randomGenerator.raw() - 1.0;
        v12 = v1 * v1 + v2 * v2;
      } while (v12 > 1.0);
      double t = v1 * Math.sqrt(-2.0 * Math.log(v12) / v12);
      double x = s + 0.5 * t;
      gds = x * x;
      if (t >= 0.0) {
        return (gds / beta);
      }         // Immediate acceptance

      double u = randomGenerator.raw();
      if (d * u <= t * t * t) {
        return (gds / beta);
      } // Squeeze acceptance

      double q0 = 0.0;
      double si = 0.0;
      double c = 0.0;
      double aaa = -1.0;
      if (alpha != aaa) {                           // Step 4. Set-up for hat case
        aaa = alpha;
        double r = 1.0 / alpha;
        double q9 = 0.0001710320;
        double q8 = -0.0004701849;
        double q7 = 0.0006053049;
        double q6 = 0.0003340332;
        double q5 = -0.0003349403;
        double q4 = 0.0015746717;
        double q3 = 0.0079849875;
        double q2 = 0.0208333723;
        double q1 = 0.0416666664;
        q0 = ((((((((q9 * r + q8) * r + q7) * r + q6) * r + q5) * r + q4) *
            r + q3) * r + q2) * r + q1) * r;
        if (alpha > 3.686) {
          if (alpha > 13.022) {
            b = 1.77;
            si = 0.75;
            c = 0.1515 / s;
          } else {
            b = 1.654 + 0.0076 * ss;
            si = 1.68 / s + 0.275;
            c = 0.062 / s + 0.024;
          }
        } else {
          b = 0.463 + s - 0.178 * ss;
          si = 1.235;
          c = 0.195 / s - 0.079 + 0.016 * s;
        }
      }
      double v;
      double q;
      double a9 = 0.104089866;
      double a8 = -0.112750886;
      double a7 = 0.110368310;
      double a6 = -0.124385581;
      double a5 = 0.142873973;
      double a4 = -0.166677482;
      double a3 = 0.199999867;
      double a2 = -0.249999949;
      double a1 = 0.333333333;
      if (x > 0.0) {                        // Step 5. Calculation of q
        v = t / (s + s);                  // Step 6.
        if (Math.abs(v) > 0.25) {
          q = q0 - s * t + 0.25 * t * t + (ss + ss) * Math.log(1.0 + v);
        } else {
          q = q0 + 0.5 * t * t * ((((((((a9 * v + a8) * v + a7) * v + a6) *
              v + a5) * v + a4) * v + a3) * v + a2) * v + a1) * v;
        }                  // Step 7. Quotient acceptance
        if (Math.log(1.0 - u) <= q) {
          return (gds / beta);
        }
      }

      double e7 = 0.000247453;
      double e6 = 0.001353826;
      double e5 = 0.008345522;
      double e4 = 0.041664508;
      double e3 = 0.166666848;
      double e2 = 0.499999994;
      double e1 = 1.000000000;
      while (true) {                          // Step 8. Double exponential deviate t
        double sign_u;
        double e;
        do {
          e = -Math.log(randomGenerator.raw());
          u = randomGenerator.raw();
          u = u + u - 1.0;
          sign_u = (u > 0) ? 1.0 : -1.0;
          t = b + (e * si) * sign_u;
        } while (t <= -0.71874483771719); // Step 9. Rejection of t
        v = t / (s + s);                  // Step 10. New q(t)
        if (Math.abs(v) > 0.25) {
          q = q0 - s * t + 0.25 * t * t + (ss + ss) * Math.log(1.0 + v);
        } else {
          q = q0 + 0.5 * t * t * ((((((((a9 * v + a8) * v + a7) * v + a6) *
              v + a5) * v + a4) * v + a3) * v + a2) * v + a1) * v;
        }
        if (q <= 0.0) {
          continue;
        }           // Step 11.
        double w;
        if (q > 0.5) {
          w = Math.exp(q) - 1.0;
        } else {
          w = ((((((e7 * q + e6) * q + e5) * q + e4) * q + e3) * q + e2) *
              q + e1) * q;
        }                            // Step 12. Hat acceptance
        if (c * u * sign_u <= w * Math.exp(e - 0.5 * t * t)) {
          x = s + 0.5 * t;
          return (x * x / beta);
        }
      }
    }
  }

  /** Returns the probability distribution function.
   * @param x Where to compute the density function.
   *
   * @return The value of the gamma density at x.
   */
  public double pdf(double x) {
    if (x < 0) {
      throw new IllegalArgumentException();
    }
    if (x == 0) {
      if (alpha == 1.0) {
        return beta;
      } else if (alpha < 1) {
        return Double.POSITIVE_INFINITY;
      } else {
        return 0;
      }
    }
    if (alpha == 1.0) {
      return beta * Math.exp(-x * beta);
    }
    return beta * Math.exp((alpha - 1.0) * Math.log(x * beta) - x * beta - Fun.logGamma(alpha));
  }

  public String toString() {
    return this.getClass().getName() + '(' + beta + ',' + alpha + ')';
  }
}
