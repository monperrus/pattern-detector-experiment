/*
 *
 * Derby - Class ScrollResultSetTest
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
import org.apache.derbyTesting.functionTests.util.SQLStateConstants;
import junit.framework.*;
import java.sql.*;

/**
 * Tests scrollable result sets
 *
 * @author Fernanda Pizzorno
 *
 * Tests:
 * - testNextOnLastRowForwardOnly: tests that the result set is closed when all
 * rows have been retreived and next has been called from the last row, 
 * autocommit = true, the result set is not holdable and type forward 
 * only. (DERBY-1295)
 * - testNextOnLastRowScrollable: tests that the result set is not closed when 
 * next is called while the result set is positioned in the last row, 
 * autocommit = true, the result set is not holdable type scrollable 
 * insensitive. (DERBY-1295)
 *
 */
public class ScrollResultSetTest extends BaseJDBCTestCase {
    
    /** Creates a new instance of ScrollResultSetTest */
    public ScrollResultSetTest(String name) {
        super(name);
    }

    /**
     * Set up the connection to the database.
     */
    public void setUp() throws  Exception {       
        con = getConnection();
        con.setAutoCommit(true);

        String createTableWithPK = "CREATE TABLE tableWithPK (" +
                "c1 int primary key," +
                "c2 int)";
        String insertData = "INSERT INTO tableWithPK values " +
                "(1, 1), (2, 2), (3, 3), (4, 4), (5, 5)";
        Statement stmt = con.createStatement();
        stmt.execute(createTableWithPK);
        
        stmt.execute(insertData);
    }
    
    /**
     * Drop the table
     */
    public void tearDown() throws Exception {
        println("TearDown");
        try { 
            con.createStatement().executeUpdate("DROP TABLE tableWithPK");
            con.commit();
            con.close();
        } catch (SQLException e) {
            printStackTrace(e);
        }      
    }
    
    /**
     * Test that moving to next row after positioned at the last row on a 
     * forward only result set will close the result set
     */
    public void testNextOnLastRowForwardOnly()  throws SQLException{

        con.setAutoCommit(true);
        con.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
        Statement roStmt = con.createStatement(
                ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_READ_ONLY);

        ResultSet rs = roStmt.executeQuery("SELECT c1 FROM tableWithPK");

        // call next until positioned after last
        while (rs.next());
        
        try {
            // forward only result set should be closed now, an exception will
            // be thrown
            rs.next();
            assertTrue("Excepted exception to be thrown - result set is closed", 
                       false);
        } catch (SQLException se) {
            if (!usingDerbyNet()) {
                assertSQLState("Unexpected SQL State",
                               SQLStateConstants.RESULT_SET_IS_CLOSED, se);
            }
        }

    }

    /**
     * Test that moving to next row after positioned at the last row on a 
     * scrollable result set will not close the result set
     */
    public void testNextOnLastRowScrollable()  throws SQLException{

        con.setAutoCommit(true);
        con.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
        Statement roStmt = con.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, 
                ResultSet.CONCUR_READ_ONLY);

        ResultSet rs = roStmt.executeQuery("SELECT c1 FROM tableWithPK");
        // move to last position and then call next
        rs.last();
        rs.next();
        
        // scrollable result set should still be open and not throw no 
        // exception will be thrown
        assertFalse("Calling next while positioned after last returns " +
                "false", rs.next());
        assertTrue("Moving to absolute(2) returns true", rs.absolute(2));
        rs.close();

    }

    /* Connection established in setUp() */
    protected Connection con = null;
       
}
