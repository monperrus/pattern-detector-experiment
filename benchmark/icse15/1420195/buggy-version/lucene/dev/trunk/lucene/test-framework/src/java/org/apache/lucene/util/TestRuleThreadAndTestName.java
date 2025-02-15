package org.apache.lucene.util;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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
 * Saves the executing thread and method name of the test case.
 */
final class TestRuleThreadAndTestName implements TestRule {
  /** 
   * The thread executing the current test case.
   * @see LuceneTestCase#isTestThread()
   */
  public volatile Thread testCaseThread;

  /**
   * Test method name.
   */
  public volatile String testMethodName = "<unknown>";

  @Override
  public Statement apply(final Statement base, final Description description) {
    return new Statement() {
      public void evaluate() throws Throwable {
        try {
          Thread current = Thread.currentThread();
          testCaseThread = current;
          testMethodName = description.getMethodName();

          base.evaluate();
        } finally {
          testCaseThread = null;
          testMethodName = null;
        }
      }
    };
  }
}
