/*

   Derby - Class org.apache.derby.iapi.jdbc.BrokeredConnection30

   Copyright 2002, 2004 The Apache Software Foundation or its licensors, as applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.iapi.jdbc;

import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import org.apache.derby.iapi.reference.JDBC30Translation;

/**
	Extends BrokeredConnection to provide the JDBC 3.0 connection methods.
 */
public class BrokeredConnection30 extends BrokeredConnection
{

	public	BrokeredConnection30(BrokeredConnectionControl control)
	{
		super(control);
	}

	public final Statement createStatement(int resultSetType,
                                 int resultSetConcurrency,
                                 int resultSetHoldability)
								 throws SQLException {
		try {
            resultSetHoldability = statementHoldabilityCheck(resultSetHoldability);
			return control.wrapStatement(getRealConnection().createStatement(resultSetType,
                    resultSetConcurrency, resultSetHoldability));
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}
	public final CallableStatement prepareCall(String sql,
                                     int resultSetType,
                                     int resultSetConcurrency,
                                     int resultSetHoldability)
									 throws SQLException {
		try {
            resultSetHoldability = statementHoldabilityCheck(resultSetHoldability);
			return control.wrapStatement(
				getRealConnection().prepareCall(sql, resultSetType,
                        resultSetConcurrency, resultSetHoldability), sql);
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}

	public final Savepoint setSavepoint()
		throws SQLException
	{
		try {
			control.checkSavepoint();
			return getRealConnection().setSavepoint();
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}

	public final Savepoint setSavepoint(String name)
		throws SQLException
	{
		try {
			control.checkSavepoint();
			return getRealConnection().setSavepoint(name);
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}

	public final void rollback(Savepoint savepoint)
		throws SQLException
	{
		try {
			control.checkRollback();
			getRealConnection().rollback(savepoint);
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}

	public final void releaseSavepoint(Savepoint savepoint)
		throws SQLException
	{
		try {
			getRealConnection().releaseSavepoint(savepoint);
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}


	public final void setHoldability(int holdability)
		throws SQLException
	{
		try {
			holdability = control.checkHoldCursors(holdability, false);
			getRealConnection().setHoldability(holdability);
			stateHoldability = holdability;
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}

	public final PreparedStatement prepareStatement(
			String sql,
			int autoGeneratedKeys)
    throws SQLException
	{
		try {
			return control.wrapStatement(getRealConnection().prepareStatement(sql, autoGeneratedKeys), sql, new Integer(autoGeneratedKeys));
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}

	public final PreparedStatement prepareStatement(
			String sql,
			int[] columnIndexes)
    throws SQLException
	{
		try {
			return control.wrapStatement(getRealConnection().prepareStatement(sql, columnIndexes), sql, columnIndexes);
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}

	public final PreparedStatement prepareStatement(
			String sql,
			String[] columnNames)
    throws SQLException
	{
		try {
			return control.wrapStatement(getRealConnection().prepareStatement(sql, columnNames), sql, columnNames);
		}
		catch (SQLException se)
		{
			notifyException(se);
			throw se;
		}
	}

	public BrokeredPreparedStatement newBrokeredStatement(BrokeredStatementControl statementControl, String sql, Object generatedKeys) throws SQLException {
		return new BrokeredPreparedStatement30(statementControl, getJDBCLevel(), sql, generatedKeys);
	}
	public BrokeredCallableStatement newBrokeredStatement(BrokeredStatementControl statementControl, String sql) throws SQLException {
		return new BrokeredCallableStatement30(statementControl, getJDBCLevel(), sql);
	}

	int getJDBCLevel() { return 3;}

}
