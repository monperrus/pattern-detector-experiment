/*

   Derby - Class 
       org.apache.derbyTesting.functionTests.tests.jdbcapi.BatchUpdateTest

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

package org.apache.derbyTesting.functionTests.tests.jdbcapi;

import java.sql.BatchUpdateException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.derbyTesting.junit.BaseJDBCTestCase;
import org.apache.derbyTesting.junit.CleanDatabaseTestSetup;
import org.apache.derbyTesting.junit.DatabasePropertyTestSetup;
import org.apache.derbyTesting.junit.JDBC;
import org.apache.derbyTesting.junit.TestConfiguration;

/**
 * Test BatchUpdate functionality.
 * <P>
 * This test examines the behavior fo BatchUpdate test.
 * One fixture tests creating tables in batch, the other fixtures can be grouped
 * into 5 rough categories:
 *  - tests that verify that correct usage with Statements work as expected
 *    - testEmptyStatementBatch()
 *      try executing a batch which nothing in it.
 *    - testSingleStatementBatch()
 *      try executing a batch which one statement in it.
 *    - testMultipleStatementsBatch()
 *      try executing a batch with 3 different statements in it.
 *    - test1000StatementsBatch()
 *      try executing a batch with 1000 statements in it.
 *    - testAutoCommitTrueBatch()
 *      try batch with autocommit true
 *    - testCombinationsOfClearBatch()
 *      try clear batch
 *    - testAssociatedParams()
 *      confirm associated parameters run ok with batches
 *   
 *  - tests that verify that incorrect usage with Statments give appropriate
 *    errors
 *    - testStatementWithResultSetBatch()
 *      statements which will return a resultset are not allowed in batch
 *      update. The following case should throw an exception for select.
 *      Below trying various placements of select statement in the batch,
 *      i.e. as 1st stmt, nth stmt and last stmt
 *    - testStatementNonBatchStuffInBatch()
 *      try executing a batch with regular statement intermingled.
 *    - testStatementWithErrorsBatch()
 *      Below trying various placements of overflow update statement
 *      in the batch, i.e. as 1st stmt, nth stat and last stmt
 *    - testTransactionErrorBatch()
 *      try transaction error, i.e. time out while getting the lock
 *    
 *  - tests that verify that usage with callableStatements work as expected
 *    - testCallableStatementBatch()
 *      try callable statements
 *    - testCallableStatementWithOutputParamBatch()
 *      try callable statement with output parameters
 *      
 *  - tests that verify that correct usage with preparedStatements work as
 *    expected
 *    - testEmptyValueSetPreparedBatch()
 *      try executing a batch which nothing in it.
 *    - testNoParametersPreparedBatch()
 *      try executing a batch with no parameters. 
 *      (fails with NullPointerException with NetworkServer. See DERBY-2112
 *    - testSingleValueSetPreparedBatch()
 *      try executing a batch which one parameter set in it.
 *    - testMultipleValueSetPreparedBatch()
 *      try executing a batch with 3 parameter sets in it.
 *    - testMultipleValueSetNullPreparedBatch()
 *      try executing a batch with 2 parameter sets in it and they are set 
 *      to null.
 *    - test1000ValueSetPreparedBatch()
 *      try executing a batch with 1000 statements in it.
 *    - testPreparedStatRollbackAndCommitCombinations()
 *      try executing batches with various rollback and commit combinations.
 *    - testAutoCommitTruePreparedStatBatch()
 *      try prepared statement batch with autocommit true
 *    - testCombinationsOfClearPreparedStatBatch()
 *      try clear batch
 *      
 *  - tests that verify that incorrect use with preparedStatements give 
 *    appropriate errors
 *    - testPreparedStmtWithResultSetBatch()
 *      statements which will return a resultset are not allowed in batch
 *      update. The following case should throw an exception for select.
 *    - testPreparedStmtNonBatchStuffInBatch();
 *      try executing a batch with regular statement intermingled.
 *    - testPreparedStmtWithErrorsBatch();
 *      trying various placements of overflow update statement
 *      in the batch
 *    - testTransactionErrorPreparedStmtBatch()
 *      try transaction error, in this particular case time out while
 *      getting the lock
 * 
 * Almost all fixtures but 1 execute with embedded and 
 * NetworkServer/DerbyNetClient - however, there is a difference in 
 * functionality between the two when an error condition is reaches. Thus,
 * the negative tests have if / else if blocks for embedded and client.
 * 
 * The 1 fixture that ise not running with network server is 
 * identified with //TODO: tags and has an if (usingEmbedded()) block and
 * a JIRA issue attached to it.
 * 
 */

public class BatchUpdateTest extends BaseJDBCTestCase {
	
    /** Creates a new instance of BatchUpdateTest */
    public BatchUpdateTest(String name) {
        super(name);
    }

    /**
     * Set up the conection to the database.
     *  This is itself a test of statements creating tables in batch. 
     */
    public void setUp() throws  Exception {
        getConnection().setAutoCommit(false);
        Statement s = createStatement();
        try {
            s.execute("delete from t1");
        } catch (SQLException e) {} // ignore if this fails, 
        // if it's the first time, it *will* fail, thereafter, other things
        // will fail anyway.
        s.close();
        commit();
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite("BatchUpdateTest");
        suite.addTest(baseSuite("BatchUpdateTest:embedded"));
        suite.addTest(TestConfiguration.clientServerDecorator(
            baseSuite("BatchUpdateTest:client")));
        return suite;
    }
    
    protected static Test baseSuite(String name) {
        TestSuite suite = new TestSuite(name);
        suite.addTestSuite(BatchUpdateTest.class);
        return new CleanDatabaseTestSetup(
                DatabasePropertyTestSetup.setLockTimeouts(suite, 2, 4)) 
        {
            /**
             * Creates the tables used in the test cases.
             * @exception SQLException if a database error occurs
             */
            protected void decorateSQL(Statement stmt) throws SQLException
            {
                stmt.execute("create table t1(c1 int)");
                // for fixture testCallableStatementBatch
                stmt.execute("create table datetab(c1 date)");
                stmt.execute("create table timetab(c1 time)");
                stmt.execute("create table timestamptab(c1 timestamp)");
                stmt.execute("create table usertypetab(c1 DATE)");
                // for fixture testAssociatedParams
                stmt.execute("create table assoc" +
                    "(x char(10) not null primary key, y char(100))");
                stmt.execute("create table assocout(x char(10))");
            }
        };
    } 
    
    /* 
     * helper method to check each count in the return array of batchExecute
     */
    private void assertBatchUpdateCounts( 
        int[] expectedBatchResult, int[] executeBatchResult )
    {
        assertEquals("length of array should be identical", 
            expectedBatchResult.length, executeBatchResult.length);
        
        for (int i=0; i<expectedBatchResult.length; i++)
        {
            String msg = "mismatch for array index [" + i + "] ; ";
            assertEquals(msg,expectedBatchResult[i],executeBatchResult[i]);
            println("expectedUpdate result #" + i + " : " +
                expectedBatchResult[i]);
            println("actual result #" + i + " : " + executeBatchResult[i]);
        }
    }
    
    /** 
     * helper method to evaluate negative tests where we expect a 
     * batchExecuteException to be returned.
     * @exception SQLException     Thrown if the expected error occurs
     *                             We expect a BatchUpdateException, and
     *                             verify it is so.
     *
     * @param String               The sqlstate to look for.
     * @param Statement            The Statement that contains the Batch to
     *                             be executed.
     * @param int[]                The expectedUpdateCount array.
     */
    protected void assertBatchExecuteError( 
        String expectedError,
        Statement stmt,
        int[] expectedUpdateCount) 
    throws SQLException 
    {
        int[] updateCount;    
        try {
            updateCount = stmt.executeBatch();
            fail("Expected batchExecute to fail");
        } catch (BatchUpdateException bue) {
            assertSQLState(expectedError, bue);
            updateCount = ((BatchUpdateException)bue).getUpdateCounts();
            assertBatchUpdateCounts(expectedUpdateCount, updateCount);
        } 
    }
    
    /* Fixture that verifies tables can be created in batch */
    public void testMinimalDDLInBatch() throws SQLException {
        
        Statement stmt = createStatement();
        stmt.addBatch("create table ddltsttable1(c1 int)");
        stmt.addBatch("create procedure ddlinteg() language java " +
            "parameter style java external name 'java.lang.Integer'");
        stmt.addBatch("create table ddltable2(c1 date)");
        int expectedCount[] = {0,0,0};
        assertBatchUpdateCounts(expectedCount, stmt.executeBatch());
        ResultSet rs = stmt.executeQuery(
            "select count(*) from SYS.SYSTABLES where tablename like 'DDL%'");
        JDBC.assertFullResultSet(rs, new String[][] {{"2"}}, true);
        rs = stmt.executeQuery(
            "select count(*) from SYS.SYSALIASES where alias like 'DDL%'");
        JDBC.assertFullResultSet(rs, new String[][] {{"1"}}, true);

        commit();
    }

    
    /* Fixtures that test correct usage of batch handling with Statements */
    
    // try executing a batch which nothing in it. Should work.
    public void testEmptyStatementBatch() throws SQLException {
        Statement stmt = createStatement();
        int updateCount[];

        // try executing a batch which nothing in it. Should work.
        println("Positive Statement: clear the batch and run the empty batch");
        stmt.clearBatch();
        updateCount = stmt.executeBatch();
        assertEquals("expected updateCount of 0", 0, updateCount.length);

        stmt.executeUpdate("delete from t1");
        commit();
    }

    // try executing a batch which single statement in it. Should work.
    public void testSingleStatementBatch() throws SQLException {

        Statement stmt = createStatement();
        println("Positive Statement: testing 1 statement batch");
        stmt.addBatch("insert into t1 values(2)");

        assertBatchUpdateCounts(new int[] {1}, stmt.executeBatch());
            
        commit();
    }
    
    // try executing a batch with 3 different statements in it.
    public void testMultipleStatementsBatch() throws SQLException {

        Statement stmt = createStatement();
        ResultSet rs;

        println("Positive Statement: testing 2 inserts and 1 update batch");
        stmt.addBatch("insert into t1 values(2)");
        stmt.addBatch("update t1 set c1=4");
        stmt.addBatch("insert into t1 values(3)");

        assertBatchUpdateCounts(new int[] {1,1,1}, stmt.executeBatch());
        
        rs = stmt.executeQuery("select count(*) from t1 where c1=2");
        rs.next();
        assertEquals("expect 0 rows with c1 = 2", 0, rs.getInt(1));
        rs.close();

        rs = stmt.executeQuery("select count(*) from t1 where c1=4");
        rs.next();
        assertEquals("expect 1 row with c1 = 4", 1, rs.getInt(1));
        rs.close();

        rs = stmt.executeQuery("select count(*) from t1 where c1=3");
        rs.next();
        assertEquals("expect 1 row with c1 = 3", 1, rs.getInt(1));
        rs.close();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("expect 2 rows total", 2, rs.getInt(1));
        rs.close();

        commit();
    }

    // try executing a batch with 1000 statements in it.
    public void test1000StatementsBatch() throws SQLException {
        int updateCount[];

        Statement stmt = createStatement();
        ResultSet rs;

        println("Positive Statement: 1000 statements batch");
        for (int i=0; i<1000; i++){
            stmt.addBatch("insert into t1 values(1)");
        }
        updateCount = stmt.executeBatch();
        assertEquals("1000 statement in the batch, expect update count 1000", 
            1000, updateCount.length);

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("1000 statement in the batch, expect 1000 rows",
            1000, rs.getInt(1));
        rs.close();

        commit();
    }

    // try batch with autocommit true
    public void testAutoCommitTrueBatch() throws SQLException {

        getConnection().setAutoCommit(true);    
        Statement stmt = createStatement();
        ResultSet rs;

        // try batch with autocommit true
        println("Positive Statement: stmt testing with autocommit true");
        stmt.addBatch("insert into t1 values(1)");
        stmt.addBatch("insert into t1 values(1)");
        stmt.addBatch("delete from t1");
        assertBatchUpdateCounts(new int[] {1,1,2}, stmt.executeBatch());

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("expect 0 rows", 0,rs.getInt(1));
        rs.close();

        // turn it false again after the above negative test. 
        // should happen automatically, but just in case
        getConnection().setAutoCommit(false);    
        commit();
    }

    //  try combinations of clear batch.
    public void testCombinationsOfClearBatch() throws SQLException {

        Statement stmt = createStatement();
        ResultSet rs;

        println("Positive Statement: add 3 statements, clear and execute batch");
        stmt.addBatch("insert into t1 values(2)");
        stmt.addBatch("insert into t1 values(2)");
        stmt.addBatch("insert into t1 values(2)");
        stmt.clearBatch();

        assertEquals("Batch should be cleared, there should be no update count",
            0, stmt.executeBatch().length);
        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        JDBC.assertEmpty(rs);
        rs.close();

        println("Positive Statement: add 3 statements, clear batch, " +
            "add 3 more statements and execute batch");
        stmt.addBatch("insert into t1 values(2)");
        stmt.addBatch("insert into t1 values(2)");
        stmt.addBatch("insert into t1 values(2)");
        stmt.clearBatch();
        stmt.addBatch("insert into t1 values(2)");
        stmt.addBatch("insert into t1 values(2)");
        stmt.addBatch("insert into t1 values(2)");

        assertBatchUpdateCounts(new int[] {1,1,1}, stmt.executeBatch());
        rs = stmt.executeQuery("select count(*) from t1");
        JDBC.assertFullResultSet(rs, new String[][] {{"3"}}, true);

        rs.close();
        commit();
    }

    /*
     ** Associated parameters are extra parameters that are created
     ** and associated with the root parameter (the user one) to
     ** improve the performance of like.       For something like
     ** where c1 like ?, we generate extra 'associated' parameters 
     ** that we use for predicates that we give to the access
     ** manager. 
     */
    public void testAssociatedParams() throws SQLException 
    {

        Statement stmt = createStatement();
        int i;
        println("Positive Statement: testing associated parameters");
        PreparedStatement checkps = prepareStatement(
            "select x from assocout order by x");
        PreparedStatement ps = prepareStatement(
            "insert into assoc values (?, 'hello')");
        for ( i = 10; i < 60; i++)
        {
            ps.setString(1, new Integer(i).toString());
            ps.executeUpdate();     
        }

        ps = prepareStatement(
            "insert into assocout select x from assoc where x like ?");
        ps.setString(1, "33%");
        ps.addBatch();
        ps.setString(1, "21%");
        ps.addBatch();
        ps.setString(1, "49%");
        ps.addBatch();
        
        assertBatchUpdateCounts(new int[] {1,1,1}, ps.executeBatch());
        checkps.execute();
        ResultSet rs = checkps.getResultSet();
        JDBC.assertFullResultSet(
            rs, new String[][] {{"21"},{"33"},{"49"}}, true);
                
        stmt.executeUpdate("delete from assocout");

        ps = prepareStatement(
                "insert into assocout select x from assoc where x like ?");
        ps.setString(1, "3%");
        ps.addBatch(); // expectedCount 10: values 10-19
        ps.setString(1, "2%");
        ps.addBatch(); // values 20-29
        ps.setString(1, "1%");
        ps.addBatch(); // values 30-39

        // set up expected values for check
        String expectedStrArray[][] = new String[30][1];
        for (i=10 ; i < 40 ; i++)
        {
            expectedStrArray[i-10][0] = String.valueOf(i);
        }
   
        assertBatchUpdateCounts( new int[] {10,10,10}, ps.executeBatch());
        checkps.execute();
        rs = checkps.getResultSet();
        JDBC.assertFullResultSet(rs, expectedStrArray, true);
                
        stmt.executeUpdate("delete from assocout");
        ps = prepareStatement(
            "insert into assocout select x from assoc where x like ?");
        ps.setString(1, "%");// values 10-59
        ps.addBatch();
        ps.setString(1, "666666");
        ps.addBatch();
        ps.setString(1, "%");// values 10-59
        ps.addBatch();
        
        // set up expected values for check
        String expectedStrArray2[][] = new String[100][1];
        int j = 0;
        for (i=10 ; i < 60 ; i++)
        {  
            for (int twice = 0; twice < 2; twice++)
            {
                expectedStrArray2[j][0] = String.valueOf(i);
                j++;
            }
        }
        
        assertBatchUpdateCounts (new int[] {50,0,50}, ps.executeBatch());
        checkps.execute();
        rs = checkps.getResultSet();
        JDBC.assertFullResultSet(rs, expectedStrArray2, true);
    }

    /* Fixtures that test incorrect batch usage with Statements */

    // statements which will return a resultset are not allowed in batch
    // update. The following case should throw an exception for select. 
    // Below trying various placements of select statement in the batch,
    // i.e. as 1st stmt, nth stat and last stmt
    public void testStatementWithResultSetBatch() throws SQLException {
        
        Statement stmt = createStatement();
        ResultSet rs;

        // trying select as the first statement
        println("Negative Statement: statement testing select as first " +
            "statement in the batch");
        stmt.addBatch("SELECT * FROM SYS.SYSCOLUMNS");
        stmt.addBatch("insert into t1 values(1)");
        if (usingEmbedded())
            /* Ensure the exception is the ResultSetReturnNotAllowed */
            assertBatchExecuteError("X0Y79", stmt, new int[] {});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", stmt, new int[] {-3, 1});
        
        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        if (usingEmbedded())
            assertEquals(
                "There should be no rows in the table", 0, rs.getInt(1));
        else if (usingDerbyNetClient())
            assertEquals("There will be 1 row in the table", 1, rs.getInt(1));
        rs.close();
        
        // trying select as the nth statement
        println("Negative Statement: " +
            "statement testing select as nth stat in the batch");
        stmt.addBatch("insert into t1 values(1)");
        stmt.addBatch("SELECT * FROM SYS.SYSCOLUMNS");
        stmt.addBatch("insert into t1 values(1)");
        if (usingEmbedded())
            /* Ensure the exception is the ResultSetReturnNotAllowed */
            assertBatchExecuteError("X0Y79", stmt, new int[] {1});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", stmt, new int[] {1,-3,1});
            
        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        if (usingEmbedded())
            assertEquals(
                "There should be 1 row in the table", 1, rs.getInt(1));
        else if (usingDerbyNetClient())
            assertEquals("There are 3 rows in the table", 3, rs.getInt(1));
        rs.close();

        // trying select as the last statement
        println("Negative Statement: statement testing select" +
            " as last stat in the batch");
        stmt.addBatch("insert into t1 values(1)");
        stmt.addBatch("insert into t1 values(1)");
        stmt.addBatch("SELECT * FROM SYS.SYSCOLUMNS");
        if (usingEmbedded())
            /* Ensure the exception is the ResultSetReturnNotAllowed */
            assertBatchExecuteError("X0Y79", stmt, new int[] {1,1});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", stmt, new int[] {1,1,-3});

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        if (usingEmbedded())
            assertEquals(
                "There should now be 3 rows in the table", 3, rs.getInt(1));
        else if (usingDerbyNetClient())
            assertEquals(
                "There should now be 5 rows in the table", 5, rs.getInt(1));
        rs.close();

        rollback();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be no rows in the table after rollback", 
            0, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // try executing a batch with regular statement intermingled.
    public void testStatementNonBatchStuffInBatch() throws SQLException {
        
        Statement stmt = createStatement();
        int[] updateCount=null;
        ResultSet rs;

        // trying execute after addBatch
        println("Negative Statement:" +
            " statement testing execute in the middle of batch");
        stmt.addBatch("SELECT * FROM SYS.SYSCOLUMNS");
        /* Check to be sure the exception is the MIDDLE_OF_BATCH */
        /* assertStatementError will do the execute() */
        if (usingEmbedded())
            assertStatementError("XJ068",stmt,"insert into t1 values(1)");
        else if (usingDerbyNetClient())
        {
            stmt.addBatch("insert into t1 values(1)"); 
            assertBatchExecuteError("XJ208",stmt, new int[] {-3,1});           
            // pull level with embedded situation
            rollback();
        }
        // do clearBatch so we can proceed
        stmt.clearBatch();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be no rows in the table", 0, rs.getInt(1));
        rs.close();

        // trying executeQuery after addBatch
        println("Negative Statement: " +
            "statement testing executeQuery in the middle of batch");
        stmt.addBatch("insert into t1 values(1)");
        if (usingEmbedded())
        {
            try
            {
                stmt.executeQuery("SELECT * FROM SYS.SYSTABLES");
                fail("Expected executeQuerywith embedded");
            } catch (SQLException sqle) {
                /* Check to be sure the exception is the MIDDLE_OF_BATCH */
                assertSQLState("XJ068", sqle);
                // do clearBatch so we can proceed
                stmt.clearBatch();
            }
        }
        else if (usingDerbyNetClient())
        {
            stmt.executeQuery("SELECT * FROM SYS.SYSTABLES");
            updateCount = stmt.executeBatch();
            assertBatchUpdateCounts(new int[] {1}, updateCount);
            // set to same spot as embedded
            rollback();
        }

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be no rows in the table", 0, rs.getInt(1));
        rs.close();

        println("Negative Statement: " +
            "statement testing executeUpdate in the middle of batch");
        // trying executeUpdate after addBatch
        println("Negative Statement: " +
        "statement testing executeUpdate in the middle of batch");
        stmt.addBatch("insert into t1 values(1)");
        try
        {
            stmt.executeUpdate("insert into t1 values(1)");
            stmt.addBatch("insert into t1 values(1)");
            stmt.addBatch("SELECT * FROM SYS.SYSCOLUMNS");
            if (usingDerbyNetClient())
            {
                assertBatchExecuteError("XJ208", stmt, new int[] {1,1,-3});
            }
            else if (usingEmbedded())
            {
                updateCount = stmt.executeBatch();
                fail("Expected executeBatch to fail");
            }
        } catch (SQLException sqle) {
            /* Check to be sure the exception is the MIDDLE_OF_BATCH */
            if (usingEmbedded())
                assertSQLState("XJ068", sqle);
            else if (usingDerbyNetClient())
                assertSQLState("XJ208", sqle);
            
            stmt.clearBatch();
        }
        
        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        if (usingEmbedded())
            assertEquals("There should be no rows in the table", 
                0, rs.getInt(1));
        else if (usingDerbyNetClient())
            assertEquals("There should be 3 rows in the table", 
                3, rs.getInt(1));
        rs.close();

        rollback();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be no rows in the table", 0, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // Below trying various placements of overflow update statement in the 
    // batch, i.e. as 1st stmt, nth stmt and last stmt
    public void testStatementWithErrorsBatch() throws SQLException {
        
        Statement stmt = createStatement();
        ResultSet rs;

        stmt.executeUpdate("insert into t1 values(1)");

        // trying update as the first statement
        println("Negative Statement: statement testing overflow error" +
            " as first statement in the batch");
        stmt.addBatch("update t1 set c1=2147483647 + 1");
        stmt.addBatch("insert into t1 values(1)");
        /* Check to be sure the exception is the one we expect */
        /* Overflow is first stmt in the batch, so expect no update count */
        if (usingEmbedded())
            assertBatchExecuteError("22003", stmt, new int[] {});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", stmt, new int[] {-3,1});

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        if (usingEmbedded())
            assertEquals("there should be 1 row in the table", 
                    1, rs.getInt(1));
        if (usingDerbyNetClient())
            assertEquals("there should be 2 rows in the table", 
                    2, rs.getInt(1));
        rs.close();

        // trying update as the nth statement
        println("Negative Statement: statement testing overflow error" +
            " as nth statement in the batch");
        stmt.addBatch("insert into t1 values(1)");
        stmt.addBatch("update t1 set c1=2147483647 + 1");
        stmt.addBatch("insert into t1 values(1)");
        /* Check to be sure the exception is the one we expect */
        /* Update is second statement in the batch, expect 1 update count */
        if (usingEmbedded())
            assertBatchExecuteError("22003", stmt, new int[] {1});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", stmt, new int[] {1,-3,1});

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        if (usingEmbedded())
            assertEquals("expected: 2 rows", 2, rs.getInt(1));
        if (usingDerbyNetClient())
            assertEquals("expected: 4 rows", 4, rs.getInt(1));
        rs.close();

        // trying select as the last statement
        println("Negative Statement: statement testing overflow error" +
            " as last stat in the batch");
        stmt.addBatch("insert into t1 values(1)");
        stmt.addBatch("insert into t1 values(1)");
        stmt.addBatch("update t1 set c1=2147483647 + 1");
        /* Check to be sure the exception is the one we expect */
        /* Update is last statement in the batch, expect 2 update counts */
        if (usingEmbedded())
            assertBatchExecuteError("22003", stmt, new int[] {1,1});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", stmt, new int[] {1,1,-3});

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        if (usingEmbedded())
            assertEquals("expected: 4 rows", 4, rs.getInt(1));
        if (usingDerbyNetClient())
            assertEquals("expected: 6 rows", 6, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // try transaction error, in this particular case time out while
    // getting the lock
    public void testTransactionErrorBatch() throws SQLException {

        // conn is just default connection
        Connection conn = getConnection();
        Connection conn2 = openDefaultConnection();
        conn.setAutoCommit(false);
        conn2.setAutoCommit(false);        
        Statement stmt = conn.createStatement();
        Statement stmt2 = conn2.createStatement();
        
        int[] updateCount = null;

        println("Negative Statement: statement testing time out" +
            " while getting the lock in the batch");

        stmt.execute("insert into t1 values(1)");
        stmt2.execute("insert into t1 values(2)");

        stmt.addBatch("update t1 set c1=3 where c1=2");
        stmt2.addBatch("update t1 set c1=4 where c1=1");

        try
        {
            stmt.executeBatch();
            fail ("Batch is expected to fail");
            updateCount = stmt2.executeBatch();
        } catch (BatchUpdateException bue) {
            /* Ensure the exception is time out while getting lock */
            if (usingEmbedded())
                assertSQLState("40XL1", bue);
            else if (usingDerbyNetClient())
                assertSQLState("XJ208", bue);
            updateCount = ((BatchUpdateException)bue).getUpdateCounts();
            if (updateCount != null) {
                if (usingEmbedded())
                    assertEquals("first statement in the batch caused time out" +
                        " while getting the lock, there should be no update count", 
                        0, updateCount.length);
                else if (usingDerbyNetClient())
                    /* first statement in the batch caused time out while getting
                     *  the lock, there should be 1 update count of -3 */
                    assertBatchUpdateCounts(new int[] {-3}, updateCount);
            }
        }
        conn.rollback();
        conn2.rollback();
        stmt.clearBatch();
        stmt2.clearBatch();
        commit();
    }
    
    /* Fixtures that test batch updates with CallableStatements */

    // try callable statements
    public void testCallableStatementBatch() throws SQLException {

        println("Positive Callable Statement: " +
            "statement testing callable statement batch");
        CallableStatement cs = prepareCall("insert into t1 values(?)");

        cs.setInt(1, 1);
        cs.addBatch();
        cs.setInt(1,2);
        cs.addBatch();
        try
        {
            executeBatchCallableStatement(cs);
        }
        catch (SQLException sqle)
        {   
            fail("The executeBatch should have succeeded");
        }
        cleanUpCallableStatement(cs, "t1");

        /* For 'beetle' bug 2813 - setDate/setTime/setTimestamp
         * calls on callableStatement throws ClassNotFoundException 
         * verify setXXXX() works with Date, Time and Timestamp 
         * on CallableStatement.
         */
        cs = prepareCall("insert into datetab values(?)");

        cs.setDate(1, Date.valueOf("1990-05-05"));
        cs.addBatch();
        cs.setDate(1,Date.valueOf("1990-06-06"));
        cs.addBatch();
        try
        {
            executeBatchCallableStatement(cs);
        }
        catch (SQLException sqle)
        {   
            fail("The executeBatch should have succeeded");
        }
        cleanUpCallableStatement(cs, "datetab");

        cs = prepareCall("insert into timetab values(?)");

        cs.setTime(1, Time.valueOf("11:11:11"));
        cs.addBatch();
        cs.setTime(1, Time.valueOf("12:12:12"));
        cs.addBatch();
        try
        {
            executeBatchCallableStatement(cs);
        }
        catch (SQLException sqle)
        {   
            fail("The executeBatch should have succeeded");
        }
        cleanUpCallableStatement(cs, "timestamptab");

        cs = prepareCall("insert into timestamptab values(?)");

        cs.setTimestamp(1, Timestamp.valueOf("1990-05-05 11:11:11.1"));
        cs.addBatch();
        cs.setTimestamp(1, Timestamp.valueOf("1992-07-07 12:12:12.2"));
        cs.addBatch();
        try
        {
            executeBatchCallableStatement(cs);
        }
        catch (SQLException sqle)
        {   
            fail("The executeBatch should have succeeded");
        }
        cleanUpCallableStatement(cs, "timestamptab");

        // Try with a user type
        cs = prepareCall("insert into usertypetab values(?)");

        cs.setObject(1, Date.valueOf("1990-05-05"));
        cs.addBatch();
        cs.setObject(1,Date.valueOf("1990-06-06"));
        cs.addBatch();
        try
        {
            executeBatchCallableStatement(cs);
        }
        catch (SQLException sqle)
        {   
            fail("The executeBatch should have succeeded");
        }
        cleanUpCallableStatement(cs, "usertypetab");
    }
    
    // helper method to testCallableStatementBatch 
    // executes and evaluates callable statement
    private static void executeBatchCallableStatement(CallableStatement cs)
    throws SQLException
    {
        int updateCount[];

        updateCount = cs.executeBatch();
        assertEquals("there were 2 statements in the batch", 
            2, updateCount.length);
        for (int i=0; i<updateCount.length; i++) 
        {
            assertEquals("update count should be 1", 1, updateCount[i]);
        }
    }

    // helper method to testCallableStatementBatch - 
    // removes all rows from table
    protected void cleanUpCallableStatement(
        CallableStatement cs, String tableName)
    throws SQLException
    {
        getConnection();
        cs.close();
        rollback();
        cs = prepareCall("delete from " + tableName);
        cs.executeUpdate();
        cs.close();
        commit();
    }
    
    // try callable statements with output parameters
    public void testCallableStatementWithOutputParamBatch() 
    throws SQLException {

        println("Negative Callable Statement: " +
            "callable statement with output parameters in the batch");
        Statement s = createStatement();

        s.execute("CREATE PROCEDURE " +
            "takesString(OUT P1 VARCHAR(40), IN P2 INT) " +
            "EXTERNAL NAME '" + this.getClass().getName() + ".takesString'" +
        " NO SQL LANGUAGE JAVA PARAMETER STYLE JAVA");

        CallableStatement cs = prepareCall("call takesString(?,?)");
        cs.registerOutParameter(1, Types.CHAR);
        cs.setInt(2, Types.INTEGER);
        try
        {
            cs.addBatch();
            if (usingEmbedded())
                fail("Expected to see error XJ04C");
            else if (usingDerbyNetClient()) {
                executeBatchCallableStatement(cs);       
            }
        }
        catch (SQLException sqle)
        {
            // Check to be sure the exception is callback related
            assertSQLState("XJ04C", sqle);
        }

        cs.close();
        s.execute("drop procedure takesString");
        s.close();
        rollback();
        commit();
    }
    
    // helper method to be used as procedure in test 
    // testCallableStatementWithOutputParamBatch
    public static void takesString(String[] outparam, int type) 
    throws Throwable
    {
        // method is stripped from takesString in jdbcapi/outparams.java
        outparam[0] = "3";
    }

    /* Fixtures that test correct usage with PreparedStatements */    
    
    // try executing a batch which nothing in it. Should work.
    public void testEmptyValueSetPreparedBatch() throws SQLException {

        Statement stmt = createStatement();
        
        // try executing a batch which nothing in it. Should work.
        println("Positive Prepared Stat: " +
            "set no parameter values and run the batch");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(?)");

        assertBatchUpdateCounts(new int[] {}, pStmt.executeBatch());

        pStmt.close();
        commit();
    }
    
    // try prepared statement batch with just no settable parameters.
    public void testNoParametersPreparedBatch() throws SQLException {

        // TODO: analyze & implement for NetworkServer when DERBY-2112 is fixed
        // test fails with NullPointerException with NetworkServer
        // see DERBY-2112
        if (!usingEmbedded())
            return;
     
        Statement stmt = createStatement();
        ResultSet rs;

        println("Positive Prepared Stat: no settable parameters");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(5)");
        pStmt.addBatch();
        pStmt.addBatch();
        pStmt.addBatch();
        /* 3 parameters were set in the batch, update count length
         *  should be 3 */
        assertBatchUpdateCounts(new int[] {1,1,1}, pStmt.executeBatch());

        pStmt.close();
        rs = stmt.executeQuery("select count(*) from t1 where c1=5");
        rs.next();
        assertEquals("There should be 3 rows with c1 = 5", 3, rs.getInt(1));
        rs.close();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 3 rows", 3, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // try prepared statement batch with just one set of values.
    public void testSingleValueSetPreparedBatch() throws SQLException {

        Statement stmt = createStatement();
        ResultSet rs;

        // try prepared statement batch with just one set of values
        println("Positive Prepared Stat: " +
            "set one set of parameter values and run the batch");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(?)");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* 1 parameter was set in batch, update count length should be 1 */
        assertBatchUpdateCounts(new int[] {1}, pStmt.executeBatch());

        pStmt.close();
        rs = stmt.executeQuery("select count(*) from t1 where c1=1");
        rs.next();
        assertEquals("There should be 1 row with c1=1", 1, rs.getInt(1));
        
        rs.close();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 1 row", 1, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // try executing a batch with 3 different parameter sets in it.
    public void testMultipleValueSetPreparedBatch() throws SQLException {

        Statement stmt = createStatement();
        ResultSet rs;

        // try prepared statement batch with just one set of values
        println("Positive Prepared Stat: " +
            "set 3 set of parameter values and run the batch");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(?)");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 2);
        pStmt.addBatch();
        pStmt.setInt(1, 3);
        pStmt.addBatch();
        /* 3 parameters were set , update count length should be 3 */
        assertBatchUpdateCounts(new int[] {1,1,1}, pStmt.executeBatch());

        pStmt.close();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 3 rows", 3, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // try prepared statement batch with just 2 set of values 
    // and there value is null. 
    // tests fix for 'beetle' bug 4002: Execute batch for
    // preparedStatement gives nullPointerException
    public void testMultipleValueSetNullPreparedBatch() throws SQLException {

        Statement stmt = createStatement();
        ResultSet rs;

        // try prepared statement batch with just one set of values
        println("Positive Prepared Stat: " +
            "set one set of parameter values to null and run the batch");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(?)");
        pStmt.setNull(1, Types.INTEGER);
        pStmt.addBatch();
        pStmt.setNull(1, Types.INTEGER);
        pStmt.addBatch();
        /* 2 parameters were set in the batch, 
         * update count length should be 2 */
        assertBatchUpdateCounts(new int[] {1,1}, pStmt.executeBatch());

        pStmt.close();
        rs = stmt.executeQuery("select count(*) from t1 where c1 is null");
        rs.next();
        assertEquals("There should be 2 rows with c1 is null",
            2, rs.getInt(1));
        rs.close();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 2 rows", 2, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // try executing a batch with 1000 statements in it.
    public void test1000ValueSetPreparedBatch() throws SQLException {
        
        Statement stmt = createStatement();
        int updateCount[];
        ResultSet rs;

        println("Positive Prepared Stat: 1000 parameter set batch");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(?)");
        for (int i=0; i<1000; i++){
            pStmt.setInt(1, 1);
            pStmt.addBatch();
        }
        updateCount = pStmt.executeBatch();

        assertEquals("there were 1000 parameters set in the batch," +
            " update count length should be 1000",
            1000, updateCount.length);
        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 1000 rows in the table",
            1000, rs.getInt(1));
        rs.close();

        pStmt.close();
        commit();
    }

    // try executing batches with various rollback and commit combinations.
    public void testPreparedStatRollbackAndCommitCombinations() 
    throws SQLException {

        Statement stmt = createStatement();
        ResultSet rs;

        println("Positive Prepared Stat: batch, rollback," +
            " batch and commit combinations");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(?)");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* there were 2 statements in the batch, 
         * update count length should be 2 */
        assertBatchUpdateCounts(new int[] {1,1}, pStmt.executeBatch());

        rollback();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 0 rows after rollback", 0, rs.getInt(1));
        rs.close();

        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* there were 2 statements in the batch, 
         * update count length should be 2 */
        assertBatchUpdateCounts(new int[] {1,1}, pStmt.executeBatch());

        commit();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 2 rows", 2, rs.getInt(1));
        
        rs.close();

        // try batch and commit
        println("Positive Prepared Stat: batch and commit combinations");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* there were 2 statements in the batch, 
         * update count length should be 2 */
        assertBatchUpdateCounts(new int[] {1,1}, pStmt.executeBatch());

        commit();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 4 rows", 4, rs.getInt(1));
        rs.close();

        // try batch, batch and rollback
        println("Positive Prepared Stat: batch, " +
            "batch and rollback combinations");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* there were 2 statements in the batch, 
         * update count should be 2 */
        assertBatchUpdateCounts(new int[] {1,1}, pStmt.executeBatch());

        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* there were 2 statements in the batch, 
         * update count length should be 2 */
        assertBatchUpdateCounts(new int[] {1,1}, pStmt.executeBatch());

        rollback();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 4 rows", 4, rs.getInt(1));
        rs.close();

        // try batch, batch and commit
        println("Positive Prepared Stat: " +
            "batch, batch and commit combinations");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* there were 2 statements in the batch, 
         * update count length should be 2 */
        assertBatchUpdateCounts(new int[] {1,1}, pStmt.executeBatch());

        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* there were 2 statements in the batch, 
         * update count length should be 2 */
        assertBatchUpdateCounts(new int[] {1,1}, pStmt.executeBatch());

        commit();

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 8 rows", 8, rs.getInt(1));

        rs.close();
        pStmt.close();

        commit();
    }

    // try prepared statement batch with autocommit true
    public void testAutoCommitTruePreparedStatBatch() throws SQLException {

        ResultSet rs;

        getConnection().setAutoCommit(true);    
        Statement stmt = createStatement();

        // prepared statement batch with autocommit true
        println("Positive Prepared Stat: testing batch with autocommit true");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(?)");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        /* there were 3 statements in the batch, 
         * update count length should be 3 */
        assertBatchUpdateCounts(new int[] {1,1,1}, pStmt.executeBatch());

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 3 rows in the table", 3, rs.getInt(1));
        rs.close();
        pStmt.close();

        // turn it false again after the above negative test
        // should happen automatically, but doesn't hurt
        getConnection().setAutoCommit(false);    

        commit();
    }
    
    // try combinations of clear batch.
    public void testCombinationsOfClearPreparedStatBatch() 
    throws SQLException {

        Statement stmt = createStatement();
        int updateCount[];
        ResultSet rs;

        println("Positive Prepared Stat: add 3 statements, " +
            "clear batch and execute batch");
        PreparedStatement pStmt = 
            prepareStatement("insert into t1 values(?)");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 2);
        pStmt.addBatch();
        pStmt.setInt(1, 3);
        pStmt.addBatch();
        pStmt.clearBatch();
        /* there were 0 statements in the batch, 
         * update count length should be 0 */
        assertBatchUpdateCounts(new int[] {}, pStmt.executeBatch());

        println("Positive Prepared Stat: " +
            "add 3 statements, clear batch, add 3 and execute batch");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 2);
        pStmt.addBatch();
        pStmt.setInt(1, 3);
        pStmt.addBatch();
        pStmt.clearBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 2);
        pStmt.addBatch();
        pStmt.setInt(1, 3);
        pStmt.addBatch();
        updateCount = pStmt.executeBatch();

        assertEquals("there were 3 statements in the batch, " +
            "update count should be 3",
            3, updateCount.length);
        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 3 rows in the table", 3, rs.getInt(1));
        rs.close();
        pStmt.close();

        commit();
    }
    
    /* Fixtures that test incorrect usage with PreparedStatements */
    
    // statements which will return a resultset are not allowed in
    // batch Updates. Following case should throw an exception for select.
    public void testPreparedStmtWithResultSetBatch() throws SQLException {

        Statement stmt = createStatement();
        ResultSet rs;

        println("Negative Prepared Stat: testing select in the batch");
        PreparedStatement pStmt = 
            prepareStatement("select * from t1 where c1=?");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        if (usingEmbedded())
            /* Ensure the exception is the ResultSetReturnNotAllowed */
            /* "Select is first statement in the batch, 
             * so there should not be any update counts */
            assertBatchExecuteError("X0Y79", pStmt, new int[] {});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ117", pStmt, new int[] {-3});

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be no rows in the table",
            0, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // try executing a batch with regular statement intermingled.
    public void testPreparedStmtNonBatchStuffInBatch() throws SQLException {
        
        Statement stmt = createStatement();

        int updateCount[] = null;
        ResultSet rs;

        // trying execute in the middle of batch
        println("Negative Prepared Stat: " +
            "testing execute in the middle of batch");
        PreparedStatement pStmt = 
            prepareStatement("select * from t1 where c1=?");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        try
        {
            pStmt.execute();
            if (usingEmbedded())
                fail("Expected executeBatch to fail");
            else if (usingDerbyNetClient())
                updateCount = pStmt.executeBatch();
        } catch (SQLException sqle) {
            if (usingEmbedded())
                /* Check to be sure the exception is the MIDDLE_OF_BATCH */
                assertSQLState("XJ068", sqle);
            else if (usingDerbyNetClient())
                assertSQLState("XJ117", sqle);
            // do clearBatch so we can proceed
            stmt.clearBatch();
        }

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be no rows in the table", 
            0, rs.getInt(1));
        rs.close();

        // trying executeQuery in the middle of batch
        println("Negative Prepared Statement: " +
            "testing executeQuery in the middle of batch");
        pStmt = 
            prepareStatement("select * from t1 where c1=?");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        try
        {
            pStmt.executeQuery();
            if (usingEmbedded())
                fail("Expected executeBatch to fail");
            else if (usingDerbyNetClient())
                updateCount = pStmt.executeBatch();
        } catch (SQLException sqle) {
            if (usingEmbedded())
                /* Check to be sure the exception is the MIDDLE_OF_BATCH */
                assertSQLState("XJ068", sqle);
            else if (usingDerbyNetClient())
                assertSQLState("XJ117", sqle);
            // do clearBatch so we can proceed
            stmt.clearBatch();
        }

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be no rows in the table", 
            0, rs.getInt(1));
        rs.close();

        //  trying executeUpdate in the middle of batch
        println("Negative Prepared Stat: " +
            "testing executeUpdate in the middle of batch");
        pStmt = 
            prepareStatement("select * from t1 where c1=?");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        try
        {
            pStmt.executeUpdate();
            if (usingEmbedded())
                fail("Expected executeBatch to fail");
            else if (usingDerbyNetClient())
                updateCount = pStmt.executeBatch();
        } catch (SQLException sqle) {
            if (usingEmbedded())
                /* Check to be sure the exception is the MIDDLE_OF_BATCH */
                assertSQLState("XJ068", sqle);
            else if (usingDerbyNetClient())
                assertSQLState("X0Y79", sqle);
            // do clearBatch so we can proceed
            stmt.clearBatch();
        }

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be no rows in the table", 
            0, rs.getInt(1));
        rs.close();

        commit();
    }
    
    // Below trying placements of overflow update statement in the batch
    public void testPreparedStmtWithErrorsBatch() throws SQLException {

        Statement stmt = createStatement();
        ResultSet rs;
        PreparedStatement pStmt = null;

        stmt.executeUpdate("insert into t1 values(1)");

        println("Negative Prepared Stat: " +
            "testing overflow as first set of values");
        pStmt = prepareStatement("update t1 set c1=(? + 1)");
        pStmt.setInt(1, java.lang.Integer.MAX_VALUE);
        pStmt.addBatch();
        if (usingEmbedded())
            /* Check to be sure the exception is the one we expect */
            /* Overflow is first statement in the batch, 
             * so there should not be any update count */
            assertBatchExecuteError("22003", pStmt, new int[] {});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", pStmt, new int[] {-3});

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 1 row in the table", 1, rs.getInt(1));
        rs.close();

        println("Negative Prepared Stat: " +
            "testing overflow as nth set of values");
        pStmt = prepareStatement("update t1 set c1=(? + 1)");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, java.lang.Integer.MAX_VALUE);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        if (usingEmbedded())
            /* Check to be sure the exception is the one we expect */
            /* Overflow is second statement in the batch, 
             * so there should be only 1 update count */
            assertBatchExecuteError("22003", pStmt, new int[] {1});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", pStmt, new int[] {1,-3,1});

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 1 row in the table", 1, rs.getInt(1));
        rs.close();

        // trying select as the last statement
        println("Negative Prepared Stat: " +
            "testing overflow as last set of values");
        pStmt = prepareStatement("update t1 set c1=(? + 1)");
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, 1);
        pStmt.addBatch();
        pStmt.setInt(1, java.lang.Integer.MAX_VALUE);
        pStmt.addBatch();
        if (usingEmbedded())
            /* Check to be sure the exception is the one we expect */
            /* Overflow is last statement in the batch, 
             * so there should be 2 update counts */
            assertBatchExecuteError("22003", pStmt, new int[] {1,1});
        else if (usingDerbyNetClient())
            assertBatchExecuteError("XJ208", pStmt, new int[] {1,1,-3});

        rs = stmt.executeQuery("select count(*) from t1");
        rs.next();
        assertEquals("There should be 1 row in the table", 1, rs.getInt(1));
        rs.close();
        pStmt.close();

        commit();
    }
    
    // try transaction error, in this particular case 
    // time out while getting the lock
    public void testTransactionErrorPreparedStmtBatch() throws SQLException {

        Connection conn = getConnection();
        Connection conn2 = openDefaultConnection();
        conn.setAutoCommit(false);
        conn2.setAutoCommit(false);        
        Statement stmt = conn.createStatement();
        Statement stmt2 = conn2.createStatement();

        int updateCount[] = null;

        println("Negative Prepared Statement: " +
            "testing transaction error, time out while getting the lock");

        stmt.execute("insert into t1 values(1)");
        stmt2.execute("insert into t1 values(2)");

        PreparedStatement pStmt1 = 
            conn.prepareStatement("update t1 set c1=3 where c1=?");
        pStmt1.setInt(1, 2);
        pStmt1.addBatch();

        PreparedStatement pStmt2 = 
            conn.prepareStatement("update t1 set c1=4 where c1=?");
        pStmt2.setInt(1, 1);
        pStmt2.addBatch();

        try
        {
            pStmt1.executeBatch();
            updateCount = pStmt2.executeBatch();
            fail ("Batch is expected to fail");
        } catch (BatchUpdateException bue) {
            /* Check that the exception is time out while 
             * getting the lock */
            if (usingEmbedded())
                assertSQLState("40XL1", bue);
            else if (usingDerbyNetClient())
                assertSQLState("XJ208", bue);
            updateCount = ((BatchUpdateException)bue).getUpdateCounts();
            if (updateCount != null) {
                if (usingEmbedded())
                    assertEquals("first statement in the batch caused time out" +
                        " while getting the lock, there should be no update count", 
                        0, updateCount.length);
                else if (usingDerbyNetClient())
                    /* first statement in the batch caused time out while getting
                     *  the lock, there should be 1 update count of -3 */
                    assertBatchUpdateCounts(new int[] {-3}, updateCount);
            }
        }

        conn.rollback();
        conn2.rollback();
        commit();
    }
}
