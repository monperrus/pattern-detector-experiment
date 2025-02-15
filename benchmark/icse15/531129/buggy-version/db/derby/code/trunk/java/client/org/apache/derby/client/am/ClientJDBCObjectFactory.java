/*
 
   Derby - Class org.apache.derby.client.am.ClientJDBCObjectFactory
 
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

package org.apache.derby.client.am;

import org.apache.derby.client.ClientPooledConnection;
import org.apache.derby.jdbc.ClientDataSource;
import java.sql.SQLException;
import org.apache.derby.jdbc.ClientBaseDataSource;

/**
 *
 * The methods of this interface are used to return JDBC interface
 * implementations to the user depending on the JDBC version supported
 * by the jdk
 *
 */

public interface ClientJDBCObjectFactory {
    
    /**
     * This method is used to return an instance of
     * ClientPooledConnection (or ClientPooledConnection40) class which
     * implements javax.sql.PooledConnection
     */
    ClientPooledConnection newClientPooledConnection(ClientBaseDataSource ds,
            LogWriter logWriter,String user,String password)
            throws SQLException;
    
    /**
     * This method is used to return an instance of
     * ClientPooledConnection(or ClientPooledConnection40) class which
     * implements javax.sql.PooledConnection
     */
    ClientPooledConnection newClientPooledConnection(ClientBaseDataSource ds,
            LogWriter logWriter,String user,String password,int rmId)
            throws SQLException;
    
    /**
     * Returns an instance of org.apache.derby.client.am.CallableStatement.
     * or CallableStatement40 which implements java.sql.CallableStatement
     *
     * @param agent       The instance of NetAgent associated with this
     *                    CallableStatement object.
     * @param connection  The connection object associated with this
     *                    PreparedStatement Object.
     * @param sql         A String object that is the SQL statement to be sent 
     *                    to the database.
     * @param type        One of the ResultSet type constants
     * @param concurrency One of the ResultSet concurrency constants
     * @param holdability One of the ResultSet holdability constants
     * @param cpc         The PooledConnection object that will be used to 
     *                    notify the PooledConnection reference of the Error 
     *                    Occurred and the Close events.
     * @return a CallableStatement object
     * @throws SqlException
     */
    CallableStatement newCallableStatement(Agent agent,
            org.apache.derby.client.am.Connection connection, String sql,
            int type,int concurrency,int holdability,
            ClientPooledConnection cpc) throws SqlException;
    
    /**
     * Returns an instance of LogicalConnection.
     * This method returns an instance of LogicalConnection
     * (or LogicalConnection40) which implements java.sql.Connection.
     */
    LogicalConnection newLogicalConnection(
                    org.apache.derby.client.am.Connection physicalConnection,
                    ClientPooledConnection pooledConnection)
        throws SqlException;
    
    /**
     * This method returns an instance of PreparedStatement
     * (or PreparedStatement40) which implements java.sql.PreparedStatement
     * It has the ClientPooledConnection as one of its parameters 
     * this is used to raise the Statement Events when the prepared
     * statement is closed
     *
     * @param agent The instance of NetAgent associated with this
     *              CallableStatement object.
     * @param connection The connection object associated with this
     *                   PreparedStatement Object.
     * @param sql        A String object that is the SQL statement to be sent
     *                   to the database.
     * @param section    Section
     * @param cpc The ClientPooledConnection wraps the underlying physical
     *            connection associated with this prepared statement.
     *            It is used to pass the Statement closed and the Statement
     *            error occurred events that occur back to the
     *            ClientPooledConnection.
     * @return a PreparedStatement object
     * @throws SqlException
     */
    PreparedStatement newPreparedStatement(Agent agent,
            org.apache.derby.client.am.Connection connection,
            String sql,Section section,ClientPooledConnection cpc) 
            throws SqlException;
    
    /**
     * Returns an instance of PreparedStatement
     * (or PreparedStatement40) which implements java.sql.PreparedStatement
     * It has the ClientPooledConnection as one of its parameters 
     * this is used to raise the Statement Events when the prepared
     * statement is closed
     *
     * @param agent The instance of NetAgent associated with this
     *              CallableStatement object.
     * @param connection  The connection object associated with this
     *                    PreparedStatement Object.
     * @param sql         A String object that is the SQL statement
     *                    to be sent to the database.
     * @param type        One of the ResultSet type constants.
     * @param concurrency One of the ResultSet concurrency constants.
     * @param holdability One of the ResultSet holdability constants.
     * @param autoGeneratedKeys a flag indicating whether auto-generated
     *                          keys should be returned.
     * @param columnNames an array of column names indicating the columns that
     *                    should be returned from the inserted row or rows.
     * @param cpc The ClientPooledConnection wraps the underlying physical
     *            connection associated with this prepared statement
     *            it is used to pass the Statement closed and the Statement
     *            error occurred events that occur back to the
     *            ClientPooledConnection.
     * @return a PreparedSatement object
     * @throws SqlException
     */
    PreparedStatement newPreparedStatement(Agent agent,
            org.apache.derby.client.am.Connection connection,String sql,
            int type,int concurrency,int holdability,int autoGeneratedKeys,
            String [] columnNames,ClientPooledConnection cpc) 
            throws SqlException;
    
    
    /**
     * This method returns an instance of NetConnection (or NetConnection40) class
     * which extends from org.apache.derby.client.am.Connection
     * this implements the java.sql.Connection interface
     */
    org.apache.derby.client.am.Connection newNetConnection(
            LogWriter netLogWriter,
            String databaseName,java.util.Properties properties)
            throws SqlException;
    
    /**
     * This method returns an instance of NetConnection (or NetConnection40) class
     * which extends from org.apache.derby.client.am.Connection
     * this implements the java.sql.Connection interface
     */
    org.apache.derby.client.am.Connection newNetConnection(
            LogWriter netLogWriter,
            org.apache.derby.jdbc.ClientBaseDataSource clientDataSource,String user,
            String password) throws SqlException;
    
    /**
     * This method returns an instance of NetConnection (or NetConnection40)
     * class which extends from org.apache.derby.client.am.Connection
     * this implements the java.sql.Connection interface
     */
    org.apache.derby.client.am.Connection newNetConnection(
            LogWriter netLogWriter,
            int driverManagerLoginTimeout,String serverName,
            int portNumber,String databaseName,java.util.Properties properties)
            throws SqlException;
    
    /**
     * This method returns an instance of NetConnection (or NetConnection40)
     * class which extends from org.apache.derby.client.am.Connection
     * this implements the java.sql.Connection interface
     */
    org.apache.derby.client.am.Connection newNetConnection(
            LogWriter netLogWriter,
            String user,String password,
            org.apache.derby.jdbc.ClientBaseDataSource dataSource,int rmId,
            boolean isXAConn) throws SqlException;
    
    /**
     * This methos returns an instance of NetConnection
     * (or NetConnection40) class which extends from
     * org.apache.derby.client.am.Connection this implements the
     * java.sql.Connection interface
     */
    org.apache.derby.client.am.Connection newNetConnection(
            LogWriter netLogWriter,String ipaddr,
            int portNumber,org.apache.derby.jdbc.ClientBaseDataSource dataSource,
            boolean isXAConn) throws SqlException;
    
    /**
     * This method returns an instance of NetConnection (or NetConnection40)
     * class which extends from org.apache.derby.client.am.Connection
     * this implements the java.sql.Connection interface
     * This method is used to pass the ClientPooledConnection
     * object to the NetConnection object which can then be used to pass the 
     * statement events back to the user
     *
     * @param netLogWriter placeholder for NetLogWriter object associated with this connection
     * @param user         user id for this connection
     * @param password     password for this connection
     * @param dataSource   The DataSource object passed from the PooledConnection 
     *                     object from which this constructor was called
     * @param rmId         The Resource Manager ID for XA Connections
     * @param isXAConn     true if this is a XA connection
     * @param cpc          The ClientPooledConnection object from which this 
     *                     NetConnection constructor was called. This is used
     *                     to pass StatementEvents back to the pooledConnection
     *                     object
     * @throws             SqlException
     */
    org.apache.derby.client.am.Connection newNetConnection(
            LogWriter netLogWriter,
            String user,String password,
            org.apache.derby.jdbc.ClientBaseDataSource dataSource,int rmId,
            boolean isXAConn,ClientPooledConnection cpc) throws SqlException;
    
    /**
     * This method returns an instance of NetResultSet(or NetResultSet40)
     * which extends from org.apache.derby.client.am.ResultSet
     * which implements java.sql.ResultSet
     */
    ResultSet newNetResultSet(Agent netAgent,MaterialStatement netStatement,
            Cursor cursor,
            int qryprctyp, int sqlcsrhld, int qryattscr, int qryattsns,
            int qryattset,long qryinsid,int actualResultSetType,
            int actualResultSetConcurrency,int actualResultSetHoldability)
            throws SqlException;
    
    /**
     * This method provides an instance of NetDatabaseMetaData
     * (or NetDatabaseMetaData40) which extends from
     * org.apache.derby.client.am.DatabaseMetaData which implements
     * java.sql.DatabaseMetaData
     */
    DatabaseMetaData newNetDatabaseMetaData(Agent netAgent,
            org.apache.derby.client.am.Connection netConnection);
    
    /**
     * This method provides an instance of Statement or Statement40 
     * depending on the jdk version under use
     * @param  agent      Agent
     * @param  connection Connection
     * @return a java.sql.Statement implementation 
     * @throws SqlException
     *
     */
     Statement newStatement(Agent agent, 
             org.apache.derby.client.am.Connection connection) 
             throws SqlException;
     
     /**
     * This method provides an instance of Statement or Statement40 
     * depending on the jdk version under use
     * @param  agent            Agent
     * @param  connection       Connection
     * @param  type             int
     * @param  concurrency      int
     * @param  holdability      int
     * @param autoGeneratedKeys int
     * @param columnNames       String[]
     * @return a java.sql.Statement implementation 
     * @throws SqlException
     *
     */
     Statement newStatement(Agent agent, 
                     org.apache.derby.client.am.Connection connection, int type, 
                     int concurrency, int holdability,
                     int autoGeneratedKeys, String[] columnNames) 
                     throws SqlException;
     
    /**
     * Returns an instanceof ColumnMetaData or ColumnMetaData40 depending 
     * on the jdk version under use
     *
     * @param logWriter LogWriter
     * @return a ColumnMetaData implementation
     *
     */
    ColumnMetaData newColumnMetaData(LogWriter logWriter); 

    /**
     * Returns an instanceof ColumnMetaData or ColumnMetaData40 depending 
     * on the jdk version under use
     *
     * @param logWriter  LogWriter
     * @param upperBound int
     * @return a ColumnMetaData implementation
     *
     */
    ColumnMetaData newColumnMetaData(LogWriter logWriter, int upperBound);
    
    /**
     * 
     * returns an instance of ParameterMetaData or ParameterMetaData40 depending 
     * on the jdk version under use
     *
     * @param columnMetaData ColumnMetaData
     * @return a ParameterMetaData implementation
     *
     */
    ParameterMetaData newParameterMetaData(ColumnMetaData columnMetaData);
}
