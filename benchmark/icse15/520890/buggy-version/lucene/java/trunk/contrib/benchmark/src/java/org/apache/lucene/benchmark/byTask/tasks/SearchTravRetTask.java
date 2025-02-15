package org.apache.lucene.benchmark.byTask.tasks;

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

import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.feeds.QueryMaker;

/**
 * Search and Travrese and Retrieve docs task.
 * 
 * <p>Note: This task reuses the reader if it is already open. 
 * Otherwise a reader is opened at start and closed at the end.
 */
public class SearchTravRetTask extends ReadTask {

  public SearchTravRetTask(PerfRunData runData) {
    super(runData);
  }

  public boolean withRetrieve() {
    return true;
  }

  public boolean withSearch() {
    return true;
  }

  public boolean withTraverse() {
    return true;
  }

  public boolean withWarm() {
    return false;
  }

  public QueryMaker getQueryMaker() {
    return getRunData().getSearchTravQueryMaker();
  }


}
