  + native
/*

   Derby - Class org.apache.derbyTesting.functionTests.tests.store._Suite

       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License
*/
package org.apache.derbyTesting.functionTests.tests.store;

import org.apache.derbyTesting.junit.BaseTestCase;
import org.apache.derbyTesting.junit.JDBC;

import junit.framework.Test; 
import junit.framework.TestSuite;

/**
 * Suite to run all JUnit tests in this package:
 * org.apache.derbyTesting.functionTests.tests.lang
 * <P>
 * All tests are run "as-is", just as if they were run
 * individually. Thus this test is just a collection
 * of all the JUNit tests in this package (excluding itself).
 * While the old test harness is in use, some use of decorators
 * may be required.
 *
 */
public class _Suite extends BaseTestCase  {

    /**
     * Use suite method instead.
     */
    private _Suite(String name) {
        super(name);
    }

    public static Test suite() {

        TestSuite suite = new TestSuite("store");
        
        suite.addTest(BootAllTest.suite());

        suite.addTest(Derby3625Test.suite());
        suite.addTest(PositionedStoreStreamTest.suite());
        // Encryption only supported for Derby in J2SE/J2EE environments.
        // J2ME (JSR169) does not support encryption.
        if (JDBC.vmSupportsJDBC3()) {
            // Add tests of basic functionality on encrypted databases.
	    // DERBY-2644 disable encryption tests until permissions failures 
	    // are resolved.
            //suite.addTest(EncryptionKeyAESTest.suite());
            //suite.addTest(EncryptionKeyBlowfishTest.suite());
            //suite.addTest(EncryptionKeyDESTest.suite());
        }
        
        return suite;
    }
}
