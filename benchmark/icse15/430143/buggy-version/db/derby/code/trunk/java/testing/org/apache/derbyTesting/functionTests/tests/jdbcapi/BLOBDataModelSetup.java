/**
 *
 * Derby - Class BLOBDataModelSetup
 *
 * Copyright 2006 The Apache Software Foundation or its
 * licensors, as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import org.apache.derbyTesting.functionTests.util.TestInputStream;
import junit.extensions.TestSetup;
import junit.framework.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.InputStream;

/**
 * Sets up a data model with very large BLOBs.
 * The table created will have three fields: 
 *  1. a value field (val), which is the value for every byte in the BLOB.
 *  2. a length (length) field which is the actual size of the BLOB
 *  3. the data field (data), which is the actual BLOB data.
 *
 * @author Andreas Korneliussen
 */
final public class BLOBDataModelSetup extends TestSetup
{
    
    /** 
     * Constructor
     * @param test test object being decorated by this TestSetup
     */
    public BLOBDataModelSetup(Test test) 
    {
        super(test);
    }

    /**
     * The setup creates a Connection to the database, and creates a table
     * with blob columns.
     * @exception Exception any exception will cause test to fail with error.
     */
    protected final void setUp() 
        throws Exception
    {
        con = BaseJDBCTestCase.getConnection();
        con.setAutoCommit(false);
        
        // Create table:
        final Statement statement = con.createStatement();
        statement.executeUpdate("CREATE TABLE " + tableName + " ("+
                                " val INTEGER," +
                                " length INTEGER, " +
                                " data BLOB(2G) NOT NULL)");
        statement.close();
        // Insert some data:
        final PreparedStatement preparedStatement =
            con.prepareStatement
            ("INSERT INTO " + tableName + "(val, length, data) VALUES (?,?, ?)");
        
        // Insert 10 records with size of 1MB
        for (int i = 0; i < regularBlobs; i++) {
            final int val = i;
            final InputStream stream = new TestInputStream(size, val);
            preparedStatement.setInt(1, val);
            preparedStatement.setInt(2, size);
            preparedStatement.setBinaryStream(3, stream, size);
            preparedStatement.executeUpdate();
        }
        
        // Insert 1 record with size of 64 MB
        BaseJDBCTestCase.println("Insert BLOB with size = " + bigSize);
        preparedStatement.setInt(1, bigVal);
        preparedStatement.setInt(2, bigSize);
        final InputStream stream = new TestInputStream(bigSize, bigVal);
        preparedStatement.setBinaryStream(3, stream, bigSize);
        
        BaseJDBCTestCase.println("Execute update");
        preparedStatement.executeUpdate();
        preparedStatement.close();
        
        BaseJDBCTestCase.println("Commit");
        con.commit();
    }
    
    /**
     * Teardown test.
     * Rollback connection and close it.
     * @exception Exceptions causes the test to fail with error
     */
    protected final void tearDown() 
        throws Exception
    {
        try { 
            Statement statement = con.createStatement();
            statement.execute("DROP TABLE " + tableName);
            statement.close();
            con.commit();
            con.close();
        } catch (SQLException e) {
            BaseJDBCTestCase.printStackTrace(e);
        }      
    }

    /**
     * Return table name 
     * @return table name
     */
    public static final String getBlobTableName() 
    {
        return tableName;
    }
    
    /** Size of regular Blobs (currently 1MB) */
    final static int size = 1024 * 1024;
    
    /** Number of regular Blobs */
    final static int regularBlobs = 10;

    /** Size of big record (currently 64 MB) */
    final static int bigSize = 64 * 1024 * 1024;
    
    /** Val for big  record */
    final static int bigVal = regularBlobs + 1;
    
    /** JDBC Connection */        
    private Connection con;
    
    /** Name of table */
    private static final String tableName = "TESTBLOBTABLE";
}
