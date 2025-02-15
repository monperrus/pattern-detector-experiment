/*
 *
 * Derby - Class SURBaseTest
 *
 * Copyright 2006 The Apache Software Foundation or its 
 * licensors, as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific 
 * language governing permissions and limitations under the License.
 */
package org.apache.derbyTesting.functionTests.tests.jdbcapi;
import org.apache.derbyTesting.functionTests.util.BaseJDBCTestCase;
import junit.framework.*;
import java.sql.*;

/**
 * Base class for testing Scrollable Updatable ResultSets. 
 * The setUp() provides a Connection to the database.
 * 
 * Tests of this class needs to be decorated by a DBSetup
 * and SURDataModelSetup.
 * 
 * @author Andreas Korneliussen 
 */
abstract public class SURBaseTest extends BaseJDBCTestCase {
    
    /** Creates a new instance of SURBaseTest */
    public SURBaseTest(String name) {
        super(name);
        recordCount = SURDataModelSetup.recordCount;  
    }

    /** Creates a new instance of SURBaseTest*/
    public SURBaseTest(String name, int records) {
        super(name);
        recordCount = records;  
    }
    
    /**
     * Get a JDBC Connection to the Derby database.
     * The autocommit flag is set to false, and the isolation level
     * for the transactions is set to repeatable read.
     */
    protected Connection getNewConnection() 
        throws SQLException
    {
        final Connection rcon = getConnection();
        rcon.setAutoCommit(false);
        rcon.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        return rcon;
    }

    /**
     * Set up the connection to the database.
     */
    public void setUp() throws  Exception {       
        println("SetUp");
        con = getNewConnection();
    }
    
    /**
     * Rollback the transaction
     */
    public void tearDown() throws Exception {
        println("TearDown");
        try { 
            con.rollback();
            con.close();
        } catch (SQLException e) {
            printStackTrace(e);
        }      
    }
    
    /**
     * Verify the data of a tuple in the ResultSet, based on the data 
     * that were inserted.
     */
    protected void verifyTuple(ResultSet rs) throws SQLException {
        int id = rs.getInt(1);
        int a = rs.getInt(2);
        int b = rs.getInt(3);
        int sum = a + id + 17;
        println("Reading tuple:(" + id + "," + a  + "," + b + ",'" + 
                rs.getString(4) + "')");
        assertEquals("Expecting b==id+a+17, got: id=" + id + 
                     ",a=" + a + ",b=" + b + ",c=" +rs.getString(4), b, sum);
    }
    
    /**
     * Update the current tuple in the ResultSet using updateXXX() and 
     * updateRow()
     */
    protected void updateTuple(ResultSet rs) throws SQLException {
        assertTrue("Cannot use updateRow() in autocommit mode", 
                   !con.getAutoCommit());
        int id = rs.getInt(1);
        int a = rs.getInt(2);
        int b = rs.getInt(3);        
        int newA = a*2 +id + 37;
        int newB = newA + id + 17;
        println("Updating record (" + id + "," + newA + "," + newB + ")");
        rs.updateInt(2, newA);
        rs.updateInt(3, newB); 
        rs.updateRow();
    }
    
    /**
     * Update the current tuple in the ResultSet using positioned update
     */
    protected void updateTuplePositioned(ResultSet rs) throws SQLException {
        int id = rs.getInt(1);
        int a = rs.getInt(2);
        int b = rs.getInt(3);        
        int newA = a*2 +id + 37;
        int newB = newA + id + 17;
        PreparedStatement ps = con.
            prepareStatement("update T1 set a=?,b=? where current of " +
                             rs.getCursorName());
        ps.setInt(1, newA);
        ps.setInt(2, newB);
        assertEquals("Expected one tuple to be updated", 1, ps.executeUpdate());
    }
    
    /**
     * Scroll forward to the end of the ResultSet, and verify tuples while 
     * scrolling. 
     */
    protected void scrollForward(ResultSet rs) throws SQLException
    {        
        boolean ignoreCount = rs.getType()==ResultSet.TYPE_FORWARD_ONLY 
            || !rs.isBeforeFirst();
        int nRecords = 0; 
        while (rs.next()) {
            nRecords++;
            verifyTuple(rs);
        }
        if (!ignoreCount) {
            assertEquals("Expected  " + recordCount + " records", nRecords, 
                         recordCount);
        }
    }
    
    /**
     * Scroll backward to the beginning of the ResultSet, and verify tuples 
     * while scrolling.
     */
    protected void scrollBackward(ResultSet rs) throws SQLException
    {
        boolean ignoreCount = rs.getType()==ResultSet.TYPE_FORWARD_ONLY 
            || !rs.isAfterLast();
        
        int nRecords = 0; 
        while (rs.previous()) {
            nRecords++;
            verifyTuple(rs);
        }
        if (!ignoreCount) {
            assertEquals("Expected  " + recordCount + " records", nRecords, 
                         recordCount);
        }
    }
    
    /**
     * Scroll forward and update the tuples using updateXXX() and updateRow()
     */
    protected void scrollForwardAndUpdate(ResultSet rs) throws SQLException
    {
        int nRecords = 0; 
        boolean ignoreCount = rs.getType()==ResultSet.TYPE_FORWARD_ONLY 
            || !rs.isBeforeFirst();
        
        while (rs.next()) {
            nRecords++;
            verifyTuple(rs);
            updateTuple(rs);
        }
        if (!ignoreCount) {
            assertEquals("Expected  " + recordCount + " records", nRecords, 
                         recordCount);
        }
        assertNotNull("rs.getCursorName()", rs.getCursorName());
    }
    
    /**
     * Scroll forward and do positioned updates.
     */
    protected void scrollForwardAndUpdatePositioned(ResultSet rs) 
        throws SQLException
    {
        int nRecords = 0; 
        boolean ignoreCount = rs.getType()==ResultSet.TYPE_FORWARD_ONLY 
            || !rs.isBeforeFirst();
        while (rs.next()) {
            nRecords++;
            verifyTuple(rs);
            updateTuplePositioned(rs);
        }
        if (!ignoreCount) {
            assertEquals("Expected  " + recordCount + " records", nRecords, 
                         recordCount);
        }
        assertNotNull("rs.getCursorName()", rs.getCursorName());
    }
    
    /**
     * Scroll backward and update the records using updateXXX() and updateRow()
     */
    protected void scrollBackwardAndUpdate(ResultSet rs) throws SQLException
    {
        int nRecords = 0; 
        boolean ignoreCount = rs.getType()==ResultSet.TYPE_FORWARD_ONLY 
            || !rs.isAfterLast();
        while (rs.previous()) {
            nRecords++;
            verifyTuple(rs);
            updateTuple(rs);
        }
        if (!ignoreCount) {
            assertEquals("Expected  " + recordCount + " records", nRecords, 
                         recordCount);
        }
        assertNotNull("rs.getCursorName()", rs.getCursorName());
    }
    
    /**
     * Scroll backward and update the records using positioned updates.
     */
    protected void scrollBackwardAndUpdatePositioned(ResultSet rs) 
        throws SQLException
    {
        int nRecords = 0; 
        boolean ignoreCount = rs.getType()==ResultSet.TYPE_FORWARD_ONLY 
            || !rs.isAfterLast();
        while (rs.previous()) {
            nRecords++;
            verifyTuple(rs);
            updateTuplePositioned(rs);
        }
        if (!ignoreCount) {
            assertEquals("Expected  " + recordCount + " records", nRecords, 
                         recordCount);
        }
        assertNotNull("rs.getCursorName()", rs.getCursorName());
    }
    
    /**
     * Assert that update of ResultSet fails with a SQLException
     * due to read-only ResultSet.
     */
    protected void assertFailOnUpdate(ResultSet rs) 
        throws SQLException
    {
        boolean failedCorrect = false;
        try {
            updateTuple(rs);
        } catch (SQLException e) {
            failedCorrect = true;
            assertEquals("Unexpected SQL state", 
                         RESULTSET_NOT_UPDATABLE_SQL_STATE, 
                         e.getSQLState());
            
        }
        assertTrue("Expected cursor to fail on update, since it is read only", 
                   failedCorrect);
    }
    
    /**
     * Assert that a warning was received
     */
    protected void assertWarning(SQLWarning warn, String sqlState) 
        throws SQLException
    {
        if (warn!=null || usingEmbedded()) {
            assertEquals("Unexpected SQL state", 
                         sqlState,
                         warn.getSQLState());
        } else {
            println("Expected warning with SQLState = '" + sqlState +
                    "', however warning not propagated to client driver");
        }
    }
    
    protected Connection con = null; // Connection established in setUp()
    final int recordCount;
    
       
    /**
     * Error codes and SQL state
     */
    final static String FOR_UPDATE_NOT_PERMITTED_SQL_STATE = "42Y90";
    final static String CURSOR_NOT_UPDATABLE_SQL_STATE = "42X23";
    final static String RESULTSET_NOT_UPDATABLE_SQL_STATE = "XJ083";
    final static String LOCK_TIMEOUT_SQL_STATE = "40XL1";
    final static String LOCK_TIMEOUT_EXPRESSION_SQL_STATE = "38000";
    final static String INVALID_CURSOR_STATE_NO_CURRENT_ROW = "24000";
    final static String CURSOR_OPERATION_CONFLICT = "01001";
    final static String QUERY_NOT_QUALIFIED_FOR_UPDATABLE_RESULTSET = "01J06";
}
