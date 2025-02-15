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

package org.apache.mahout.cf.taste.impl.correlation;

import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.User;
import org.apache.mahout.cf.taste.common.TasteException;

/**
 * <p>Tests {@link SpearmanCorrelation}.</p>
 */
public final class SpearmanCorrelationTest extends CorrelationTestCase {

  public void testFullCorrelation1() throws Exception {
    User user1 = getUser("test1", 1.0, 2.0, 3.0);
    User user2 = getUser("test2", 1.0, 2.0, 3.0);
    DataModel dataModel = getDataModel(user1, user2);
    double correlation = new SpearmanCorrelation(dataModel).userCorrelation(user1, user2);
    assertCorrelationEquals(1.0, correlation);
  }

  public void testFullCorrelation2() throws Exception {
    User user1 = getUser("test1", 1.0, 2.0, 3.0);
    User user2 = getUser("test2", 4.0, 5.0, 6.0);
    DataModel dataModel = getDataModel(user1, user2);
    double correlation = new SpearmanCorrelation(dataModel).userCorrelation(user1, user2);
    assertCorrelationEquals(1.0, correlation);
  }

  public void testAnticorrelation() throws Exception {
    User user1 = getUser("test1", 1.0, 2.0, 3.0);
    User user2 = getUser("test2", 3.0, 2.0, 1.0);
    DataModel dataModel = getDataModel(user1, user2);
    double correlation = new SpearmanCorrelation(dataModel).userCorrelation(user1, user2);
    assertCorrelationEquals(-1.0, correlation);
  }

  public void testSimple() throws Exception {
    User user1 = getUser("test1", 1.0, 2.0, 3.0);
    User user2 = getUser("test2", 2.0, 3.0, 1.0);
    DataModel dataModel = getDataModel(user1, user2);
    double correlation = new SpearmanCorrelation(dataModel).userCorrelation(user1, user2);
    assertCorrelationEquals(-0.5, correlation);
  }

  public void testRefresh() throws TasteException {
    // Make sure this doesn't throw an exception
    new SpearmanCorrelation(getDataModel()).refresh(null);
  }

}
