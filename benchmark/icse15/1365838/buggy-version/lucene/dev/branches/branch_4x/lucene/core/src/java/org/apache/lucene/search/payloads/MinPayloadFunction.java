  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1365837
package org.apache.lucene.search.payloads;

import org.apache.lucene.search.Explanation;
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

/**
 * Calculates the minimum payload seen
 *
 **/
public class MinPayloadFunction extends PayloadFunction {

  @Override
	public float currentScore(int docId, String field, int start, int end, int numPayloadsSeen, float currentScore, float currentPayloadScore) {
    if (numPayloadsSeen == 0) {
      return currentPayloadScore;
    } else {
		return Math.min(currentPayloadScore, currentScore);
	}
  }

  @Override
  public float docScore(int docId, String field, int numPayloadsSeen, float payloadScore) {
    return numPayloadsSeen > 0 ? payloadScore : 1;
  }
  
  @Override
  public Explanation explain(int doc, int numPayloadsSeen, float payloadScore) {
	  Explanation expl = new Explanation();
	  float minPayloadScore = (numPayloadsSeen > 0 ? payloadScore : 1);
	  expl.setValue(minPayloadScore);
	  expl.setDescription("MinPayloadFunction(...)");
	  return expl;
  }  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + this.getClass().hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    return true;
  }

}
