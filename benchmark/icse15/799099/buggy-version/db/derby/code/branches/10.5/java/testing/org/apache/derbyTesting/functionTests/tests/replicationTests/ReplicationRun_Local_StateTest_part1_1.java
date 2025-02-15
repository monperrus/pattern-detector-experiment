/*
 
Derby - Class org.apache.derbyTesting.functionTests.tests.replicationTests.ReplicationRun_Local_StateTest_part1_1
 
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at
 
   http://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 
 */
package org.apache.derbyTesting.functionTests.tests.replicationTests;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.derbyTesting.junit.SecurityManagerSetup;
import org.apache.derbyTesting.junit.BaseJDBCTestCase;


/**
 * Run a replication test on localhost
 * by using default values for master and slave hosts,
 * and master and slave ports.
 * 
 */

public class ReplicationRun_Local_StateTest_part1_1 extends ReplicationRun
{
    final static String REPLICATION_DB_NOT_BOOTED              = "XRE11";
    final static String REPLICATION_NOT_IN_SLAVE_MODE          = "XRE40";
    final static String SLAVE_OPERATION_DENIED_WHILE_CONNECTED = "XRE41";
    final static String REPLICATION_SLAVE_SHUTDOWN_OK          = "XRE42";

    /**
     * Creates a new instance of ReplicationRun_Local_StateTest_part1
     * 
     * @param testcaseName Identifying the test.
     */
    public ReplicationRun_Local_StateTest_part1_1(String testcaseName)
    {
        super(testcaseName);
    }
        
    public static Test suite()
    {
        TestSuite suite = new TestSuite("ReplicationRun_Local_StateTest_part1_1 Suite");
        
        suite.addTestSuite( ReplicationRun_Local_StateTest_part1_1.class );
        
        return SecurityManagerSetup.noSecurityManager(suite);
    }
    
    //////////////////////////////////////////////////////////////
    ////
    //// The replication test framework (testReplication()):
    //// a) "clean" replication run starting master and slave servers,
    ////     preparing master and slave databases,
    ////     starting and stopping replication and doing
    ////     failover for a "normal"/"failure free" replication
    ////     test run.
    ////
    //////////////////////////////////////////////////////////////
    
    public void testReplication_Local_StateTest_part1_1()
    throws Exception
    {
        cleanAllTestHosts();
        
        initEnvironment();
        
       initMaster(masterServerHost,
                replicatedDb);
        
        masterServer = startServer(masterJvmVersion, derbyMasterVersion,
                masterServerHost,
                ALL_INTERFACES, // masterServerHost, // "0.0.0.0", // All. or use masterServerHost for interfacesToListenOn,
                masterServerPort,
                masterDbSubPath); // Distinguishing master/slave
                
        slaveServer = startServer(slaveJvmVersion, derbySlaveVersion,
                slaveServerHost,
                ALL_INTERFACES, // slaveServerHost, // "0.0.0.0", // All. or use slaveServerHost for interfacesToListenOn,
                slaveServerPort,
                slaveDbSubPath); // Distinguishing master/slave
        
        startServerMonitor(slaveServerHost);
        
        bootMasterDatabase(jvmVersion,
                masterDatabasePath +FS+ masterDbSubPath,
                replicatedDb,
                masterServerHost, // Where the startreplication command must be given
                masterServerPort, // master server interface accepting client requests
                null // bootLoad, // The "test" to start when booting db.
                );
                
        initSlave(slaveServerHost,
                jvmVersion,
                replicatedDb); // Trunk and Prototype V2: copy master db to db_slave.
                
        startSlave(jvmVersion, replicatedDb,
                slaveServerHost, // slaveClientInterface // where the slave db runs
                slaveServerPort,
                slaveServerHost, // for slaveReplInterface
                slaveReplPort,
                testClientHost);
                
        // With master started above, next will fail! 
        // Also seems failover will fail w/XRE21!
        // Further testing: skipping next startMaster seems to 
        // NOT remove failover failure!
        /* TEMP: should be operational already - try skipping this. */
        startMaster(jvmVersion, replicatedDb,
                masterServerHost, // Where the startMaster command must be given
                masterServerPort, // master server interface accepting client requests
                masterServerHost, // An interface on the master: masterClientInterface (==masterServerHost),
                slaveServerPort, // Not used since slave don't allow clients.
                slaveServerHost, // for slaveReplInterface
                slaveReplPort);
         /* */
        

        _testPostStartedMasterAndSlave_StopSlave(); // Not in a state to continue.
                
        stopServer(jvmVersion, derbyVersion,
                slaveServerHost, slaveServerPort);
        
        stopServer(jvmVersion, derbyVersion,
                masterServerHost, masterServerPort);
        
    }

    private void _testPostStartedMasterAndSlave_StopSlave()
            throws InterruptedException, SQLException
    {
        String db = null;
        String connectionURL = null;  
        Connection conn = null;
        
        // 1. stopSlave to slave with connection to master should fail.
        db = slaveDatabasePath +FS+ReplicationRun.slaveDbSubPath +FS+ replicatedDb;
        connectionURL = "jdbc:derby:"  
                + "//" + slaveServerHost + ":" + slaveServerPort + "/"
                + db
                + ";stopSlave=true";
        util.DEBUG("1. testPostStartedMasterAndSlave_StopSlave: " + connectionURL);
        try
        {
            conn = DriverManager.getConnection(connectionURL);
            util.DEBUG("Unexpectdly connected as: " + connectionURL);
            assertTrue("Unexpectedly connected: " + connectionURL,false);
        }
        catch (SQLException se)
        {
            int ec = se.getErrorCode();
            String ss = se.getSQLState();
            String msg = ec + " " + ss + " " + se.getMessage();
            BaseJDBCTestCase.assertSQLState(
                connectionURL + " failed: ",
                SLAVE_OPERATION_DENIED_WHILE_CONNECTED,
                se);
            util.DEBUG("1. Failed as expected: " + connectionURL +  " " + msg);
        }
        // Default replication test sequence still OK.
        
        // 2. stopSlave to a master server should fail:
        db = masterDatabasePath +FS+ReplicationRun.masterDbSubPath +FS+ replicatedDb;
        connectionURL = "jdbc:derby:"  
                + "//" + masterServerHost + ":" + masterServerPort + "/"
                + db
                + ";stopSlave=true";
        util.DEBUG("2. testPostStartedMasterAndSlave_StopSlave: " + connectionURL);
        try
        {
            conn = DriverManager.getConnection(connectionURL); // From anywhere against slaveServerHost?
            util.DEBUG("Unexpectdly connected as: " + connectionURL);
            // DERBY-???? - assertTrue("Unexpectedly connected: " + connectionURL,false);
       }
        catch (SQLException se)
        {
            int ec = se.getErrorCode();
            String ss = se.getSQLState();
            String msg = ec + " " + ss + " " + se.getMessage();
            // SSQLCODE: -1, SQLSTATE: XRE40
            BaseJDBCTestCase.assertSQLState(
                connectionURL + " failed: ",
                REPLICATION_NOT_IN_SLAVE_MODE,
                se);
            util.DEBUG("2. Failed as expected: " + connectionURL +  " " + msg);
        }
        // Default replication test sequence still OK.
        
        // Replication should still be up.
        
        // Take down master - slave connection:
        killMaster(masterServerHost, masterServerPort);
        
        // 3.  stopSlave on slave should now result in an exception stating that
        //     the slave database has been shutdown. A master shutdown results
        //     in a behaviour that is similar to what happens when a stopMaster
        //     is called.
        db = slaveDatabasePath +FS+ReplicationRun.slaveDbSubPath +FS+ replicatedDb;
        connectionURL = "jdbc:derby:"  
                + "//" + slaveServerHost + ":" + slaveServerPort + "/"
                + db
                + ";stopSlave=true";
        boolean stopSlaveCorrect = false;
        util.DEBUG("3. testPostStartedMasterAndSlave_StopSlave: " + connectionURL);

        // We use a loop below, to allow for intermediate states before the
        // expected final state.
        //
        // If we get here quick enough we see these error states (in order):
        //     a) SLAVE_OPERATION_DENIED_WHILE_CONNECTED
        //     b) REPLICATION_SLAVE_SHUTDOWN_OK
        //
        // The final end state is expected to be REPLICATION_DB_NOT_BOOTED.
        //
        SQLException gotEx = null;
        int tries = 20;

        while (tries-- > 0) {
            gotEx = null;

            try {
                // From anywhere against slaveServerHost?
                conn = DriverManager.getConnection(connectionURL); 
                util.DEBUG("Unexpectedly connected: " + connectionURL);
                assertTrue("Unexpectedly connected: " + connectionURL,false);

            } catch (SQLException se) {
                if (se.getSQLState().
                        equals(SLAVE_OPERATION_DENIED_WHILE_CONNECTED)) {
                    // Try again, shutdown did not complete yet..
                    gotEx = se;
                    util.DEBUG
                        ("got SLAVE_OPERATION_DENIED_WHILE_CONNECTED, sleep");
                    Thread.sleep(1000L);
                    continue;

                } else if (se.getSQLState().
                               equals(REPLICATION_SLAVE_SHUTDOWN_OK)) {
                    // Try again, shutdown started but did not complete yet.
                    gotEx = se;
                    util.DEBUG("got REPLICATION_SLAVE_SHUTDOWN_OK, sleep..");
                    Thread.sleep(1000L);
                    continue;

                } else if (se.getSQLState().equals(REPLICATION_DB_NOT_BOOTED)) {
                    // All is fine, so proceed
                    util.DEBUG("Got REPLICATION_DB_NOT_BOOTED as expected");
                    stopSlaveCorrect = true;
                    break;

                } else {
                    // Something else, so report.
                    gotEx = se;
                    break;
                }
            }
        }

        if (gotEx != null) {
            // We did not get what we expected as the final state
            // (REPLICATION_DB_NOT_BOOTED) in reasonable time, or we saw
            // something that is not a legal intermediate state, so we fail
            // now:
            throw gotEx;
        }
        
        // Default replication test sequence will NOT be OK after this point.
        
        if ( stopSlaveCorrect )
        {
            // 4. Try a normal connection:
            connectionURL = "jdbc:derby:"
                    + "//" + slaveServerHost + ":" + slaveServerPort + "/"
                    + db;
            util.DEBUG("4. testPostStartedMasterAndSlave_StopSlave: " + connectionURL);

            try
            {
                conn = DriverManager.getConnection(connectionURL);
                util.DEBUG("4. Connected as expected: " + connectionURL);
            }
            catch (SQLException se)
            {
                int ec = se.getErrorCode();
                String ss = se.getSQLState();
                String msg = ec + " " + ss + " " + se.getMessage();
                util.DEBUG("4. Unexpectedly failed to connect: " + connectionURL +  " " + msg);
                assertTrue("Unexpectedly failed to connect: " + connectionURL +  " " + msg, false);
            }
        }
    }

    
}
