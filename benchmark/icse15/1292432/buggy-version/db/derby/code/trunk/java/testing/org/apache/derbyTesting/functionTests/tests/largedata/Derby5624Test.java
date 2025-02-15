package org.apache.derbyTesting.functionTests.tests.largedata;

import org.apache.derbyTesting.junit.BaseJDBCTestCase;
import org.apache.derbyTesting.junit.CleanDatabaseTestSetup;
import org.apache.derbyTesting.junit.DatabasePropertyTestSetup;
import org.apache.derbyTesting.junit.JDBC;
import org.apache.derbyTesting.junit.TestConfiguration;

import org.apache.derby.shared.common.sanity.SanityManager;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;


/*
Class org.apache.derbyTesting.functionTests.tests.store.Derby5624Test

Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to you under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/


/**

Test to reproduce DERBY-5624, An expanding update fails with an nospc.U error.

**/

public class Derby5624Test extends BaseJDBCTestCase
{
    /**************************************************************************
     * Fields of the class
     **************************************************************************
     */

    /**************************************************************************
     * Constructors for This class:
     **************************************************************************
     */

    /**************************************************************************
     * Private/Protected methods of This class:
     **************************************************************************
     */

    /**************************************************************************
     * Public Methods of This class:
     **************************************************************************
     */

    /**************************************************************************
     * Public Methods of XXXX class:
     **************************************************************************
     */

    public Derby5624Test(String name) 
    {
        super(name);
    }

    
    /**
     * DERBY-5624 test case
     * <p>
     *
     **/
    public void testDERBY_5624()
        throws SQLException
    {
        Statement stmt = createStatement();

        PreparedStatement insert_stmt = 
            prepareStatement(
                "INSERT INTO TESTBIGTABLE (col1, col2, col3) VALUES(?, ?, ?)");

        for (int i = 0; i < 3000; i++)
        {
            insert_stmt.setInt(1, i);
            insert_stmt.setInt(2, -(i));
            insert_stmt.setInt(3, i);
            insert_stmt.executeUpdate();
        }

        commit();

        // create index on each column
        for (int i = 0; i < 1000; i++)
        {
            stmt.executeUpdate(
                "CREATE INDEX INDEXBIG" + i + 
                " on TESTBIGTABLE (col" + i + ")");
        }
        commit();

        // see if compress succeeeds 
        stmt.executeUpdate(
            "call SYSCS_UTIL.SYSCS_COMPRESS_TABLE('APP', 'TESTBIGTABLE', 0)");

        commit();

        // verify access to table
        JDBC.assertUnorderedResultSet(
            prepareStatement(
                "select col1, col2 from TESTBIGTABLE where col1 = 10").executeQuery(),
            new String[][] {{"10", "-10"}});

    }

    protected static Test baseSuite(String name) 
    {
        TestSuite suite = new TestSuite(name);
        suite.addTestSuite(Derby5624Test.class);
        return new CleanDatabaseTestSetup(
                DatabasePropertyTestSetup.setLockTimeouts(suite, 2, 4)) 
        {
            /**
             * Creates the tables used in the test cases.
             * @exception SQLException if a database error occurs
             */
            protected void decorateSQL(Statement stmt) throws SQLException
            {
                Connection conn = stmt.getConnection();

                // create table with 1000 columns
                StringBuffer create_table_qry = new StringBuffer(10000);

                create_table_qry.append("CREATE TABLE TESTBIGTABLE (col0 int");

                for (int colnum = 1; colnum < 1000; colnum++)
                {
                    create_table_qry.append(", col" + colnum + " int");
                }
                create_table_qry.append(")");

                // CREATE TABLE TESTBIGTABLE (
                //     col0 int, col1 int, ... , col999 int)
                stmt.executeUpdate(create_table_qry.toString());

                conn.setAutoCommit(false);
            }
        };
    }

    public static Test suite() 
    {
        TestSuite suite = new TestSuite("Derby5624Test");
        suite.addTest(baseSuite("Derby5624Test:embedded"));
        return suite;
    }
}
