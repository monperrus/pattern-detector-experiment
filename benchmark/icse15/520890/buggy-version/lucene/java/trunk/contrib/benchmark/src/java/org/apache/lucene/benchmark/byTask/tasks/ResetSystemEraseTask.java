package org.apache.lucene.benchmark.byTask.tasks;

import org.apache.lucene.benchmark.byTask.PerfRunData;

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



/**
 * Reset all index and input data and call gc, erase index and dir, does NOT clear statistics.
 * This contains ResetInputs.
 * Other side effects: writers/readers nulified, deleted, closed.
 * Index is erased.
 * Directory is erased.
 */
public class ResetSystemEraseTask extends PerfTask {

  public ResetSystemEraseTask(PerfRunData runData) {
    super(runData);
  }

  public int doLogic() throws Exception {
    getRunData().reinit(true);
    return 0;
  }
  
  /*
   * (non-Javadoc)
   * @see org.apache.lucene.benchmark.byTask.tasks.PerfTask#shouldNotRecordStats()
   */
  protected boolean shouldNotRecordStats() {
    return true;
  }

}
