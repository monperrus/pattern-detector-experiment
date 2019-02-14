/*
 
   Derby - Class org.apache.derby.impl.jdbc.EmbedPreparedStatement42
 
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

package org.apache.derby.impl.jdbc;

import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.Types;

import org.apache.derby.iapi.reference.SQLState;

/**
 * <p>
 * PreparedStatement methods added by JDBC 4.2 which require Java 8.
 * </p>
 */
public class EmbedPreparedStatement42 extends EmbedPreparedStatement40
{    
    public EmbedPreparedStatement42
        (
         EmbedConnection conn, String sql, boolean forMetaData,
         int resultSetType, int resultSetConcurrency, int resultSetHoldability,
         int autoGeneratedKeys, int[] columnIndexes, String[] columnNames
         ) throws SQLException
    {
        super(conn, sql, forMetaData, resultSetType, resultSetConcurrency, resultSetHoldability,
            autoGeneratedKeys, columnIndexes, columnNames);
    }

    public  void setObject
        ( int parameterIndex, java.lang.Object x, SQLType targetSqlType )
        throws SQLException
    {
        checkStatus();
        setObject
            (
             parameterIndex, x,
             Util42.getTypeAsInt( this, targetSqlType )
             );
    }
    
    public void setObject
        (
         int parameterIndex, java.lang.Object x,
         SQLType targetSqlType, int scaleOrLength
         )  throws SQLException
    {
        checkStatus();
        setObject
            (
             parameterIndex, x,
             Util42.getTypeAsInt( this, targetSqlType ),
             scaleOrLength
             );
    }

}    

