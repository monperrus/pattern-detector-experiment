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

package org.apache.mahout.classifier.naivebayes.training;

import org.apache.mahout.math.Vector;

import java.util.Iterator;

public class ComplementaryThetaTrainer extends AbstractThetaTrainer {

  public ComplementaryThetaTrainer(Vector weightsPerFeature, Vector weightsPerLabel, double alphaI) {
    super(weightsPerFeature, weightsPerLabel, alphaI);
  }

  @Override
  public void train(int label, Vector perLabelWeight) {
    double sigmaK = labelWeight(label);
    Iterator<Vector.Element> it = perLabelWeight.iterateNonZero();
    while (it.hasNext()) {
      Vector.Element e = it.next();
      double numerator = featureWeight(e.index()) - e.get() + alphaI();
      double denominator = totalWeightSum() - sigmaK + numFeatures() ;
      double weight = Math.log(numerator / denominator);
      updatePerLabelThetaNormalizer(label, weight);
    }
  }
}
