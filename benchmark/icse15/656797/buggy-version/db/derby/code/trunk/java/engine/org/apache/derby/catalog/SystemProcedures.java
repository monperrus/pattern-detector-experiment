/*

   Derby - Class org.apache.derby.catalog.SystemProcedures

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

package org.apache.derby.catalog;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Policy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.StringTokenizer;

import org.apache.derby.iapi.db.Factory;
import org.apache.derby.iapi.db.PropertyInfo;
import org.apache.derby.iapi.error.PublicAPI;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.Property;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.cache.CacheManager;
import org.apache.derby.iapi.services.i18n.MessageService;
import org.apache.derby.iapi.services.property.PropertyUtil;
import org.apache.derby.iapi.sql.conn.ConnectionUtil;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.util.IdUtil;
import org.apache.derby.iapi.util.StringUtil;
import org.apache.derby.impl.jdbc.EmbedDatabaseMetaData;
import org.apache.derby.impl.jdbc.Util;
import org.apache.derby.impl.load.Export;
import org.apache.derby.impl.load.Import;
import org.apache.derby.impl.sql.execute.JarUtil;
import org.apache.derby.jdbc.InternalDriver;


/**
	Some system built-in procedures, and help routines.  Now used for network server.
	These procedures are built-in to the SYSIBM schema which match the DB2 SYSIBM procedures.
	Currently information on those can be found at url: 
	ftp://ftp.software.ibm.com/ps/products/db2/info/vr8/pdf/letter/db2l2e80.pdf
	
	<P>
	Also used for builtin-routines, such as SYSFUN functions, when direct calls
	into Java libraries cannot be made.
*/
public class SystemProcedures  {


	private final static int SQL_BEST_ROWID = 1;
	private final static int SQL_ROWVER = 2;
	private final static String DRIVER_TYPE_OPTION = "DATATYPE";
	private final static String ODBC_DRIVER_OPTION = "'ODBC'";

    // This token delimiter value is used to separate the tokens for multiple 
    // error messages.  This is used in DRDAConnThread
    /**
     * <code>SQLERRMC_MESSAGE_DELIMITER</code> When message argument tokes are sent,
     * this value separates the tokens for mulitiple error messages 
     */
    public  static String SQLERRMC_MESSAGE_DELIMITER = new String(new char[] {(char)20,(char)20,(char)20});

	/**
	  Method used by Derby Network Server to get localized message (original call
	  from jcc.

	  @param sqlcode	sqlcode, not used.
	  @param errmcLen	sqlerrmc length
	  @param sqlerrmc	sql error message tokens, variable part of error message (ie.,
						arguments) plus messageId, separated by separator.
	  @param sqlerrp	not used
	  @param errd0  	not used
	  @param errd1  	not used
	  @param errd2  	not used
	  @param errd3  	not used
	  @param errd4  	not used
	  @param errd5  	not used
	  @param warn		not used
	  @param sqlState	5-char sql state
	  @param file		not used
	  @param localeStr	client locale in string
	  @param msg		OUTPUT parameter, localized error message
	  @param rc			OUTPUT parameter, return code -- 0 for success
	 */
	public static void SQLCAMESSAGE(int sqlcode, short errmcLen, String sqlerrmc,
										String sqlerrp, int errd0, int errd1, int errd2,
										int errd3, int errd4, int errd5, String warn,
										String sqlState, String file, String localeStr,
										String[] msg, int[] rc)
	{
		int numMessages = 1;
        

		// Figure out if there are multiple exceptions in sqlerrmc. If so get each one
		// translated and append to make the final result.
		for (int index=0; ; numMessages++)
		{
			if (sqlerrmc.indexOf(SQLERRMC_MESSAGE_DELIMITER, index) == -1)
				break;
			index = sqlerrmc.indexOf(SQLERRMC_MESSAGE_DELIMITER, index) + 
                        SQLERRMC_MESSAGE_DELIMITER.length();
		}

		// Putting it here instead of prepareCall it directly is because inter-jar reference tool
		// cannot detect/resolve this otherwise
		if (numMessages == 1)
			MessageService.getLocalizedMessage(sqlcode, errmcLen, sqlerrmc, sqlerrp, errd0, errd1,
											errd2, errd3, errd4, errd5, warn, sqlState, file,
											localeStr, msg, rc);
		else
		{
			int startIdx=0, endIdx;
			String sqlError;
			String[] errMsg = new String[2];
			for (int i=0; i<numMessages; i++)
			{
				endIdx = sqlerrmc.indexOf(SQLERRMC_MESSAGE_DELIMITER, startIdx);
				if (i == numMessages-1)				// last error message
					sqlError = sqlerrmc.substring(startIdx);
				else sqlError = sqlerrmc.substring(startIdx, endIdx);

				if (i > 0)
				{
					/* Strip out the SQLState */
					sqlState = sqlError.substring(0, 5);
					sqlError = sqlError.substring(6);
					msg[0] += " SQLSTATE: " + sqlState + ": ";
				}

				MessageService.getLocalizedMessage(sqlcode, (short)sqlError.length(), sqlError,
											sqlerrp, errd0, errd1, errd2, errd3, errd4, errd5,
											warn, sqlState, file, localeStr, errMsg, rc);

				if (rc[0] == 0)			// success
				{
					if (i == 0)
						msg[0] = errMsg[0];
					else msg[0] += errMsg[0];	// append the new message
				}
				startIdx = endIdx + SQLERRMC_MESSAGE_DELIMITER.length();
			}
		}
	}
	
	/**
	 * Get the default or nested connection corresponding to the URL
	 * jdbc:default:connection. We do not use DriverManager here
	 * as it is not supported in JSR 169. IN addition we need to perform
	 * more checks for null drivers or the driver returing null from connect
	 * as that logic is in DriverManager.
	 * @return The nested connection
	 * @throws SQLException Not running in a SQL statement
	 */
	private static Connection getDefaultConn()throws SQLException
	{
		InternalDriver id = InternalDriver.activeDriver();
		if (id != null) { 
			Connection conn = id.connect("jdbc:default:connection", null);
			if (conn != null)
				return conn;
		}
		throw Util.noCurrentConnection();
	}

	/**
	 *  Get the DatabaseMetaData for the current connection for use in
	 *  mapping the jcc SYSIBM.* calls to the Derby DatabaseMetaData methods 
	 *
	 *  @return The DatabaseMetaData object of the current connection
	 */
	private static DatabaseMetaData getDMD() throws SQLException {
		Connection conn = getDefaultConn();
		return conn.getMetaData();
	}

	/**
	 *  Map SQLProcedures to EmbedDatabaseMetaData.getProcedures
	 *
	 *  @param catalogName SYSIBM.SQLProcedures CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLProcedures SchemaName  varchar(128),
	 *  @param procName    SYSIBM.SQLProcedures ProcName    varchar(128),
	 *  @param options     SYSIBM.SQLProcedures Options     varchar(4000))
	 *  @param rs          output parameter, the resultset object containing 
     *                     the result of getProcedures
	 *  	If options contains the string 'DATATYPE='ODBC'', call the ODBC
	 *  	version of this procedure.
	 */
	public static void SQLPROCEDURES (String catalogName, String schemaName, String procName,
										String options, ResultSet[] rs) throws SQLException
	{
		rs[0] = isForODBC(options)
			? ((EmbedDatabaseMetaData)getDMD()).getProceduresForODBC(
				catalogName, schemaName, procName)
			: getDMD().getProcedures(catalogName, schemaName, procName);
	}

	/**
	 *  Map SQLFunctions to EmbedDatabaseMetaData.getFunctions
	 *
	 *  @param catalogName SYSIBM.SQLFunctions CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLFunctions SchemaName  varchar(128),
	 *  @param funcName    SYSIBM.SQLFunctions ProcName    varchar(128),
	 *  @param options     SYSIBM.SQLFunctions Options     varchar(4000)) 
	 *                     (not used)
	 *  @param rs          output parameter, the resultset object containing 
	 *                     the result of getFunctions
	 */
	public static void SQLFUNCTIONS(String catalogName, 
									String schemaName, 
									String funcName,
									String options, 
									ResultSet[] rs) throws SQLException
	{
		rs[0] = ((EmbedDatabaseMetaData)getDMD()).
			getFunctions(catalogName, schemaName, funcName);
	}

	/**
	 * Map SQLTables to EmbedDatabaseMetaData.getSchemas, getCatalogs,
	 * getTableTypes and getTables, and return the result of the
	 * DatabaseMetaData calls.
	 *
	 * <p>JCC and DNC overload this method:
	 * <ul>
	 * <li>If options contains the string 'GETSCHEMAS=1',
	 *     call getSchemas()</li>
	 * <li>If options contains the string 'GETSCHEMAS=2',
	 *     call getSchemas(String, String)</li>
	 * <li>If options contains the string 'GETCATALOGS=1',
	 *     call getCatalogs()</li>
	 * <li>If options contains the string 'GETTABLETYPES=1',
	 *     call getTableTypes()</li>
	 * <li>otherwise, call getTables()</li>
	 * </ul>
	 *
	 *  @param catalogName SYSIBM.SQLTables CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLTables SchemaName  varchar(128),
	 *  @param tableName   SYSIBM.SQLTables TableName   varchar(128),
	 *  @param tableType   SYSIBM.SQLTables TableType   varchar(4000))
	 *  @param options     SYSIBM.SQLTables Options     varchar(4000))
	 *  @param rs          output parameter, the resultset object 
	 */
	public static void SQLTABLES (String catalogName, String schemaName, String tableName,
										String tableType, String options, ResultSet[] rs)
		throws SQLException
	{

		String optionValue = getOption("GETCATALOGS", options);
		if (optionValue != null && optionValue.trim().equals("1"))
		{
			rs[0] = getDMD().getCatalogs();
			return;
		}
		optionValue = getOption("GETTABLETYPES", options);
		if (optionValue != null && optionValue.trim().equals("1"))
		{
			rs[0] = getDMD().getTableTypes();
			return;
		}
		optionValue = getOption("GETSCHEMAS", options);
		if (optionValue != null) {
			optionValue = optionValue.trim();
			if (optionValue.equals("1")) {
				rs[0] = getDMD().getSchemas();
				return;
			}
			if (optionValue.equals("2")) {
				EmbedDatabaseMetaData edmd = (EmbedDatabaseMetaData) getDMD();
				rs[0] = edmd.getSchemas(catalogName, schemaName);
				return;
			}
		}
			 	

		String[] typeArray = null;
		if (tableType != null)
		{
			StringTokenizer st = new StringTokenizer(tableType,"',");
			typeArray = new String[st.countTokens()];
			int i = 0;

			while (st.hasMoreTokens()) 
			{
				typeArray[i] = st.nextToken();
				i++;
			}
		}
		rs[0] = getDMD().getTables(catalogName, schemaName, tableName, typeArray);
	}

	/**
	 *  Map SQLForeignKeys to EmbedDatabaseMetaData.getImportedKeys, getExportedKeys, and getCrossReference
	 *
	 *  @param pkCatalogName SYSIBM.SQLForeignKeys PKCatalogName varchar(128),
	 *  @param pkSchemaName  SYSIBM.SQLForeignKeys PKSchemaName  varchar(128),
	 *  @param pkTableName   SYSIBM.SQLForeignKeys PKTableName   varchar(128),
	 *  @param fkCatalogName SYSIBM.SQLForeignKeys FKCatalogName varchar(128),
	 *  @param fkSchemaName  SYSIBM.SQLForeignKeys FKSchemaName  varchar(128),
	 *  @param fkTableName   SYSIBM.SQLForeignKeys FKTableName   varchar(128),
	 *  @param options       SYSIBM.SQLForeignKeys Options       varchar(4000))
	 *  @param rs            output parameter, the resultset object 
	 *                     	 containing the result of the DatabaseMetaData calls
	 *  			 JCC overloads this method:
	 *  			 If options contains the string 'EXPORTEDKEY=1', call getImportedKeys
	 *  			 If options contains the string 'IMPORTEDKEY=1', call getExportedKeys
	 *  			 otherwise, call getCrossReference
	 */
	public static void SQLFOREIGNKEYS (String pkCatalogName, String pkSchemaName, String pkTableName,
										String fkCatalogName, String fkSchemaName, String fkTableName,
										String options, ResultSet[] rs)
		throws SQLException
	{

		String exportedKeyProp = getOption("EXPORTEDKEY", options);
		String importedKeyProp = getOption("IMPORTEDKEY", options);

		if (importedKeyProp != null && importedKeyProp.trim().equals("1"))
			rs[0] = getDMD().getImportedKeys(fkCatalogName,
										fkSchemaName,fkTableName);
		else if (exportedKeyProp != null && exportedKeyProp.trim().equals("1"))
			rs[0] = getDMD().getExportedKeys(pkCatalogName,
										pkSchemaName,pkTableName);
		else
			//ODBC allows table name value 'null'. JDBC does not
			rs[0] = isForODBC(options)
				? ((EmbedDatabaseMetaData)getDMD()).getCrossReferenceForODBC(
										pkCatalogName, pkSchemaName, pkTableName,
										fkCatalogName, fkSchemaName, fkTableName)
				: getDMD().getCrossReference (
										pkCatalogName, pkSchemaName, pkTableName,
										fkCatalogName, fkSchemaName, fkTableName);
	}

	/**
	 *  Helper for SQLForeignKeys and SQLTables 
	 *
	 *  @return option	String containing the value for a given option 
	 *  @param  pattern 	String containing the option to search for
	 *  @param  options 	String containing the options to search through
	 */
	private static String getOption(String pattern, String options)
	{
		if (options == null)
			return null;
		int start = options.lastIndexOf(pattern);
		if (start < 0)  // not there
			return null;
		int valueStart = options.indexOf('=', start);
		if (valueStart < 0)  // invalid options string
			return null;
		int valueEnd = options.indexOf(';', valueStart);
		if (valueEnd < 0)  // last option
			return options.substring(valueStart + 1);
		else
			return options.substring(valueStart + 1, valueEnd);
	}
	
	/**
	 *  Map SQLProcedureCols to EmbedDatabaseMetaData.getProcedureColumns
	 *
	 *  @param catalogName SYSIBM.SQLProcedureCols CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLProcedureCols SchemaName  varchar(128),
	 *  @param procName    SYSIBM.SQLProcedureCols ProcName    varchar(128),
	 *  @param paramName   SYSIBM.SQLProcedureCols ParamName   varchar(128),
	 *  @param options     SYSIBM.SQLProcedureCols Options     varchar(4000))
	 *  @param rs          output parameter, the resultset object containing 
	 *			           the result of getProcedureColumns
	 *  	If options contains the string 'DATATYPE='ODBC'', call the ODBC
	 *  	version of this procedure.
	 */
	public static void SQLPROCEDURECOLS (String catalogName, String schemaName, String procName,
										String paramName, String options, ResultSet[] rs)
		throws SQLException
	{
		rs[0] = isForODBC(options)
			? ((EmbedDatabaseMetaData)getDMD()).getProcedureColumnsForODBC(
				catalogName, schemaName, procName, paramName)
			: getDMD().getProcedureColumns(catalogName, schemaName, procName, paramName);
	}
	
	/**
	 *  Map SQLFunctionParameters to
	 *  EmbedDatabaseMetaData.getFunctionColumns()
	 *
	 * @param catalogName SYSIBM.SQLFunctionParameters CatalogName
	 * varchar(128),
	 * @param schemaName SYSIBM.SQLFunctionParameters SchemaName
	 * varchar(128),
	 * @param funcName SYSIBM.SQLFunctionParameters FuncName
	 * varchar(128),
	 * @param paramName SYSIBM.SQLFunctionParameters ParamName
	 * varchar(128),
	 * @param options SYSIBM.SQLFunctionParameters Options
	 * varchar(4000))
	 * @param rs output parameter, the resultset object containing the
	 * result of getFunctionColumns(). 
	 */
	public static void SQLFUNCTIONPARAMS(String catalogName,
										 String schemaName,
										 String funcName,
										 String paramName,
										 String options,
										 ResultSet[] rs) throws SQLException
        {
			rs[0] = ((EmbedDatabaseMetaData)getDMD()).
				getFunctionColumns(catalogName, schemaName, funcName, 
									  paramName);
        }
	

	/**
	 *  Map SQLColumns to EmbedDatabaseMetaData.getColumns
	 *
	 *  @param catalogName SYSIBM.SQLColumns CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLColumns SchemaName  varchar(128),
	 *  @param tableName   SYSIBM.SQLColumns TableName   varchar(128),
	 *  @param columnName  SYSIBM.SQLColumns ColumnName  varchar(128),
	 *  @param options     SYSIBM.SQLColumns Options     varchar(4000))
	 *  	If options contains the string 'DATATYPE='ODBC'', call the ODBC
	 *  	version of this procedure.
	 *  @param rs          output parameter, the resultset object containing 
     *                     the result of getProcedures
	 */
	public static void SQLCOLUMNS (String catalogName, String schemaName, String tableName,
										String columnName, String options, ResultSet[] rs)
		throws SQLException
	{
		rs[0] = isForODBC(options)
			? ((EmbedDatabaseMetaData)getDMD()).getColumnsForODBC(
				catalogName, schemaName, tableName, columnName)
			: getDMD().getColumns(catalogName, schemaName, tableName, columnName);
	}

	/**
	 *  Map SQLColPrivileges to EmbedDatabaseMetaData.getColumnPrivileges
	 *
	 *  @param catalogName SYSIBM.SQLColPrivileges CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLColPrivileges SchemaName  varchar(128),
	 *  @param tableName   SYSIBM.SQLColPrivileges ProcName    varchar(128),
	 *  @param columnName  SYSIBM.SQLColPrivileges ColumnName  varchar(128),
	 *  @param options     SYSIBM.SQLColPrivileges Options     varchar(4000))
	 *  @param rs          output parameter, the resultset object containing 
     *                     the result of getColumnPrivileges
	 */
	public static void SQLCOLPRIVILEGES (String catalogName, String schemaName, String tableName,
										String columnName, String options, ResultSet[] rs)
		throws SQLException
	{
		rs[0] = getDMD().getColumnPrivileges(catalogName, schemaName, tableName, columnName);
	}

	/**
	 *  Map SQLTablePrivileges to EmbedDatabaseMetaData.getTablePrivileges
	 *
	 *  @param catalogName SYSIBM.SQLTablePrivileges CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLTablePrivileges SchemaName  varchar(128),
	 *  @param tableName   SYSIBM.SQLTablePrivileges ProcName    varchar(128),
	 *  @param options     SYSIBM.SQLTablePrivileges Options     varchar(4000))
	 *  @param rs          output parameter, the resultset object containing 
     *                     the result of getTablePrivileges
	 */
	public static void SQLTABLEPRIVILEGES (String catalogName, String schemaName, String tableName,
										String options, ResultSet[] rs)
		throws SQLException
	{
		rs[0] = getDMD().getTablePrivileges(catalogName, schemaName, tableName);
	}

	/**
	 *  Map SQLPrimaryKeys to EmbedDatabaseMetaData.getPrimaryKeys
	 *
	 *  @param catalogName SYSIBM.SQLPrimaryKeys CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLPrimaryKeys SchemaName  varchar(128),
	 *  @param tableName   SYSIBM.SQLPrimaryKeys TableName   varchar(128),
	 *  @param options     SYSIBM.SQLPrimaryKeys Options     varchar(4000))
	 *  	If options contains the string 'DATATYPE='ODBC'', call the ODBC
	 *  	version of this procedure.
	 *  @param rs          output parameter, the resultset object containing 
     *                     the result of getPrimaryKeys
	 */
	public static void SQLPRIMARYKEYS (String catalogName, String schemaName, String tableName, String options, ResultSet[] rs)
		throws SQLException
	{
		rs[0] = getDMD().getPrimaryKeys(catalogName, schemaName, tableName);
	}

	/**
	 *  Map SQLGetTypeInfo to EmbedDatabaseMetaData.getTypeInfo
	 *
	 *  @param dataType  SYSIBM.SQLGetTypeInfo DataType smallint,
	 *  @param options   SYSIBM.SQLGetTypeInfo Options  varchar(4000))
	 *  	If options contains the string 'DATATYPE='ODBC'', call the ODBC
	 *  	version of this procedure.
	 *  @param rs        output parameter, the resultset object containing the
     *                   result of getTypeInfo
	 */
	public static void SQLGETTYPEINFO (short dataType, String options, ResultSet[] rs)
		throws SQLException
	{
		rs[0] = isForODBC(options)
			? ((EmbedDatabaseMetaData)getDMD()).getTypeInfoForODBC()
			: getDMD().getTypeInfo();
	}

	/**
	 *  Map SQLStatistics to EmbedDatabaseMetaData.getIndexInfo
	 *
	 *  @param catalogName SYSIBM.SQLStatistics CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLStatistics SchemaName  varchar(128),
	 *  @param tableName   SYSIBM.SQLStatistics TableName   varchar(128),
	 *  @param unique      SYSIBM.SQLStatistics Unique      smallint; 0=SQL_INDEX_UNIQUE(0); 1=SQL_INDEX_ALL(1),
	 *  @param approximate SYSIBM.SQLStatistics Approximate smallint; 1=true; 0=false,
	 *  @param options     SYSIBM.SQLStatistics Options     varchar(4000))
	 *  	If options contains the string 'DATATYPE='ODBC'', call the ODBC
	 *  	version of this procedure.
	 *  @param rs          output parameter, the resultset object containing 
     *                     the result of getIndexInfo
	 */
	public static void SQLSTATISTICS (String catalogName, String schemaName, String tableName,
										short unique, short approximate, String options, ResultSet[] rs)
		throws SQLException
	{
		boolean boolUnique = (unique == 0) ? true: false;
		boolean boolApproximate = (approximate == 1) ? true: false;
			
		rs[0] = isForODBC(options)
			? ((EmbedDatabaseMetaData)getDMD()).getIndexInfoForODBC(
				catalogName, schemaName, tableName, boolUnique, boolApproximate)
			: getDMD().getIndexInfo(catalogName, schemaName, tableName, boolUnique, boolApproximate);
	}

	/**
	 *  Map SQLSpecialColumns to EmbedDatabaseMetaData.getBestRowIdentifier and getVersionColumns
	 *
	 *  @param colType     SYSIBM.SQLSpecialColumns ColType     smallint,
	 *			where 1 means getBestRowIdentifier and 2 getVersionColumns was called.
	 *  @param catalogName SYSIBM.SQLSpecialColumns CatalogName varchar(128),
	 *  @param schemaName  SYSIBM.SQLSpecialColumns SchemaName  varchar(128),
	 *  @param tableName   SYSIBM.SQLSpecialColumns TableName   varchar(128),
	 *  @param scope       SYSIBM.SQLSpecialColumns Scope       smallint,
	 *  @param nullable    SYSIBM.SQLSpecialColumns Nullable    smallint; 0=false, 1=true,
	 *  @param options     SYSIBM.SQLSpecialColumns Options     varchar(4000))
	 *  	If options contains the string 'DATATYPE='ODBC'', call the ODBC
	 *  	version of this procedure.
	 *  @param rs          output parameter, the resultset object containing 
     *                     the result of the DatabaseMetaData call
	 */
	public static void SQLSPECIALCOLUMNS (short colType, String catalogName, String schemaName, String tableName,
										short scope, short nullable, String options, ResultSet[] rs)
		throws SQLException
	{

		boolean boolNullable = (nullable == 1) ? true: false;
		if (colType == SQL_BEST_ROWID)
		{
			rs[0] = isForODBC(options)
				? ((EmbedDatabaseMetaData)getDMD()).getBestRowIdentifierForODBC(
					catalogName, schemaName, tableName, scope, boolNullable)
				: getDMD().getBestRowIdentifier(catalogName, schemaName, tableName, scope, boolNullable);
		}
		else // colType must be SQL_ROWVER
		{
			rs[0] = isForODBC(options)
				? ((EmbedDatabaseMetaData)getDMD()).getVersionColumnsForODBC(
					catalogName, schemaName, tableName)
				: getDMD().getVersionColumns(catalogName, schemaName, tableName);
		}
	}

	/**
	 *  Map SQLUDTS to EmbedDatabaseMetaData.getUDTs
	 *
	 *  @param catalogName     SYSIBM.SQLUDTS CatalogName          varchar(128),
	 *  @param schemaPattern   SYSIBM.SQLUDTS Schema_Name_Pattern  varchar(128),
	 *  @param typeNamePattern SYSIBM.SQLUDTS Type_Name_Pattern    varchar(128),
	 *  @param udtTypes        SYSIBM.SQLUDTS UDTTypes             varchar(128),
	 *  @param options         SYSIBM.SQLUDTS Options              varchar(4000))
	 *  @param rs              output parameter, the resultset object containing
     *                         the result of getUDTs, which will be empty
	 */
	public static void SQLUDTS (String catalogName, String schemaPattern, String typeNamePattern,
										String udtTypes, String options, ResultSet[] rs)
		throws SQLException
	{

		int[] types = null;
		
		if( udtTypes != null && udtTypes.length() > 0)
		{
			StringTokenizer tokenizer = new StringTokenizer( udtTypes, " \t\n\t,");
			int udtTypeCount = tokenizer.countTokens();
			types = new int[ udtTypeCount];
			String udtType = "";
			try
			{
				for( int i = 0; i < udtTypeCount; i++)
				{
					udtType = tokenizer.nextToken();
					types[i] = Integer.parseInt( udtType);
				}
			}
			catch( NumberFormatException nfe)
			{
				throw new SQLException( "Invalid type, " + udtType + ", passed to getUDTs.");
			}
			catch( NoSuchElementException nsee)
			{
				throw new SQLException( "Internal failure: NoSuchElementException in getUDTs.");
			}
		}
		rs[0] = getDMD().getUDTs(catalogName, schemaPattern, typeNamePattern, types);
	}

	/*
	 *  Map SYSIBM.METADATA to appropriate EmbedDatabaseMetaData methods 
	 *  for now, using the sps in org.apache.derby.iapi.db.jdbc.datadictionary.metadata_net.properties
	 *
	 */
	public static void METADATA (ResultSet[] rs)
		throws SQLException
	{
		rs[0] = ((EmbedDatabaseMetaData) getDMD()).getClientCachedMetaData();
	}

	/**
	 * Helper for ODBC metadata calls.
	 * @param options	String containig the options to search through.
	 * @return True if options contain ODBC indicator; false otherwise.
	 */
	private static boolean isForODBC(String options) {

		String optionValue = getOption(DRIVER_TYPE_OPTION, options);
		return ((optionValue != null) && optionValue.toUpperCase().equals(ODBC_DRIVER_OPTION));

	}

    /**
     * Set/delete the value of a property of the database in current connection.
     * <p>
     * Will be called as SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY.
     *
     * @param key       The property key.
     * @param value     The new value, if null the property is deleted.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    public static void SYSCS_SET_DATABASE_PROPERTY(
    String  key,
    String  value)
        throws SQLException
    {
        PropertyInfo.setDatabaseProperty(key, value);
    }

    /**
     * Get the value of a property of the database in current connection.
     * <p>
     * Will be called as SYSCS_UTIL.SYSCS_GET_DATABASE_PROPERTY.
     *
     * @param key       The property key.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    public static String SYSCS_GET_DATABASE_PROPERTY(
    String  key)
        throws SQLException
    {
        LanguageConnectionContext lcc = ConnectionUtil.getCurrentLCC();

        try {
            return PropertyUtil.getDatabaseProperty(lcc.getTransactionExecute(), key);
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        }
    }

    /**
     * Compress the table.
     * <p>
     * Calls the "alter table compress {sequential}" sql.  This syntax
     * is not db2 compatible so it mapped by a system routine.  This
     * routine will be called when an application calls:
     *
     *     SYSCS_UTIL.SYSCS_COMPRESS_TABLE
     * <p>
     *
     * @param schema        schema name of the table to compress.  Must be
     *                      non-null, no default is used.
     * @param tablename     table name of the table to compress.  Must be
     *                      non-null.
     * @param sequential    if non-zero then rebuild indexes sequentially,
     *                      if 0 then rebuild all indexes in parallel.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    public static void SYSCS_COMPRESS_TABLE(
    String  schema,
    String  tablename,
    int     sequential)
        throws SQLException
    {

        String query = 
            "alter table " + "\"" + schema + "\"" + "." + "\"" +  tablename + "\"" + 
			" compress" +  (sequential != 0 ? " sequential" : "");

		Connection conn = getDefaultConn();
        
        PreparedStatement ps = conn.prepareStatement(query);
		ps.executeUpdate();
        ps.close();

		conn.close();
    }

    /**
     * Freeze the database.
     * <p>
     * Call internal routine to freeze the database so that a backup
     * can be made.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    public static void SYSCS_FREEZE_DATABASE()
		throws SQLException
    {
        Factory.getDatabaseOfConnection().freeze();
    }

    /**
     * Unfreeze the database.
     * <p>
     * Call internal routine to unfreeze the database, which was "freezed"
     * by calling SYSCS_FREEZE_DATABASE().
     * can be made.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    public static void SYSCS_UNFREEZE_DATABASE()
		throws SQLException
    {
        Factory.getDatabaseOfConnection().unfreeze();
    }

    public static void SYSCS_CHECKPOINT_DATABASE()
		throws SQLException
    {
        Factory.getDatabaseOfConnection().checkpoint();
    }

    /**
     * Backup the database to a backup directory. 
     *
     * This procedure will throw error, if there are any unlogged 
     * operation executed in the same transaction backup is started.
     * If there any unlogged operations in progess in other transaction, it
     * will wait until those transactions are completed before starting the backup.
     *
     * Examples of unlogged operations include: create index and bulk insert.
     * Note that once the backup begins these operations will not block, 
     * instead they are automatically converted into logged operations.
     * 
     * @param backupDir the name of the directory where the backup should be
     *                  stored. This directory will be created if it 
     *                  does not exist.
     * @exception StandardException thrown on error
     */
    public static void SYSCS_BACKUP_DATABASE(String  backupDir)
		throws SQLException
    {
        Factory.getDatabaseOfConnection().backup(backupDir, true);
    }

    /**
     * Backup the database to a backup directory.
     *
     * This procedure will throw error, if there are any uncommitted unlogged 
     * operation before stating the backup. It will not wait for the unlogged
     * operations to complete.
     * 
     * Examples of unlogged operations include: create index and bulk insert.
     * Note that once the backup begins these operations will not block, 
     * instead they are automatically converted into logged operations.
     * 
     * @param backupDir the name of the directory where the backup should be
     *                  stored. This directory will be created if it 
     *                  does not exist.
     * @exception StandardException thrown on error
     */
    public static void SYSCS_BACKUP_DATABASE_NOWAIT(String  backupDir)
        throws SQLException
    {
        Factory.getDatabaseOfConnection().backup(backupDir, false);
    }


    /**
     * Backup the database to a backup directory and enable the log archive
     * mode that will keep the archived log files required for roll-forward
     * from this version of the backup.
     *
     * This procedure will throw error if there are any unlogged 
     * operation executed in the same transaction backup is started.
     * If there any unlogged operations in progess in other transaction, it
     * will wait until those transactions are completed before starting the backup.
     *
     * Examples of unlogged operations include: create index and bulk insert.
     * Note that once the backup begins these operations will not block, 
     * instead they are automatically converted into logged operations.
     *
     * @param backupDir the name of the directory where the backup should be
     *                  stored. This directory will be created if not it 
     *                  does not exist.   
     * @param deleteOnlineArchivedLogFiles  If <tt>non-zero</tt> deletes online 
     *                 archived log files that exist before this backup, delete 
     *                 will occur  only after the backup is  complete.
     * @exception StandardException thrown on error.
     */
    public static void SYSCS_BACKUP_DATABASE_AND_ENABLE_LOG_ARCHIVE_MODE(
    String  backupDir,
    int     deleteOnlineArchivedLogFiles)
		throws SQLException
    {

        Factory.getDatabaseOfConnection().backupAndEnableLogArchiveMode(
                backupDir, 
                (deleteOnlineArchivedLogFiles != 0),
                true);
	}

    /**
     * Backup the database to a backup directory and enable the log archive
	 * mode that will keep the archived log files required for roll-forward
	 * from this version backup.
     *
     * This procedure will throw error, if there are any uncommitted unlogged 
     * operation before stating the backup. It will not wait for the unlogged
     * operations to complete.
     * 

     * Examples of unlogged operations include: create index and bulk insert.
     * Note that once the backup begins these operations will not block, 
     * instead they are automatically converted into logged operations.
     *
     * @param backupDir the name of the directory where the backup should be
     *                  stored. This directory will be created if not it 
     *                  does not exist.   
     *
     * @param deleteOnlineArchivedLogFiles  If <tt>non-zero</tt> deletes online 
     *                  archived log files that exist before this backup, delete     
     *                  will occur  only after the backup is  complete.
     *
     * @exception StandardException thrown on error.
     */
    public static void SYSCS_BACKUP_DATABASE_AND_ENABLE_LOG_ARCHIVE_MODE_NOWAIT(
    String  backupDir,
    int     deleteOnlineArchivedLogFiles)
		throws SQLException
    {

        Factory.getDatabaseOfConnection().backupAndEnableLogArchiveMode(
                backupDir,
                (deleteOnlineArchivedLogFiles != 0),
                false);
	}


    /**
	 * Disables the log archival process, i.e No old log files
	 * will be kept around for a roll-forward recovery.
     *
	 * @param deleteOnlineArchivedLogFiles  If <tt>non-zero</tt> deletes all the
	 *        online archived log files that exist before this call immediately.
     *
	 * @exception StandardException Thrown on error
	 */

    public static void SYSCS_DISABLE_LOG_ARCHIVE_MODE(
    int     deleteOnlineArchivedLogFiles)
		throws SQLException
    {
        Factory.getDatabaseOfConnection().disableLogArchiveMode(
                (deleteOnlineArchivedLogFiles != 0));
    }


    public static void SYSCS_SET_RUNTIMESTATISTICS(
    int     enable)
		throws SQLException
    {
		ConnectionUtil.getCurrentLCC().setRunTimeStatisticsMode(enable != 0 ? true : false);
    }

    public static void SYSCS_SET_STATISTICS_TIMING(
    int     enable)
		throws SQLException
    {
		ConnectionUtil.getCurrentLCC().setStatisticsTiming(enable != 0 ? true : false);
    }

    public static int SYSCS_CHECK_TABLE(
    String  schema,
    String  tablename)
		throws SQLException
    {
        boolean ret_val = 
            org.apache.derby.iapi.db.ConsistencyChecker.checkTable(
                schema, tablename);

        return(ret_val ? 1 : 0);
    }

    /**

    Implementation of SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE().
    <p>
    Code which implements the following system procedure:

    void SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE(
        IN SCHEMANAME        VARCHAR(128),
        IN TABLENAME         VARCHAR(128),
        IN PURGE_ROWS        SMALLINT,
        IN DEFRAGMENT_ROWS   SMALLINT,
        IN TRUNCATE_END      SMALLINT)
    <p>
    Use the SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE system procedure to reclaim 
    unused, allocated space in a table and its indexes. Typically, unused allocated
    space exists when a large amount of data is deleted from a table, and there
    have not been subsequent inserts to use the space freed by the deletes.  
    By default, Derby does not return unused space to the operating system. For 
    example, once a page has been allocated to a table or index, it is not 
    automatically returned to the operating system until the table or index is 
    destroyed. SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE allows you to return unused 
    space to the operating system.
    <p>
    This system procedure can be used to force 3 levels of in place compression
    of a SQL table: PURGE_ROWS, DEFRAGMENT_ROWS, TRUNCATE_END.  Unlike 
    SYSCS_UTIL.SYSCS_COMPRESS_TABLE() all work is done in place in the existing
    table/index.
    <p>
    Syntax:
    SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE(
        IN SCHEMANAME        VARCHAR(128),
        IN TABLENAME         VARCHAR(128),
        IN PURGE_ROWS        SMALLINT,
        IN DEFRAGMENT_ROWS   SMALLINT,
        IN TRUNCATE_END      SMALLINT)
    <p>
    SCHEMANAME: 
    An input argument of type VARCHAR(128) that specifies the schema of the table. Passing a null will result in an error.
    <p>
    TABLENAME:
    An input argument of type VARCHAR(128) that specifies the table name of the 
    table. The string must exactly match the case of the table name, and the 
    argument of "Fred" will be passed to SQL as the delimited identifier 'Fred'. 
    Passing a null will result in an error.
    <p>
    PURGE_ROWS:
    If PURGE_ROWS is set to non-zero then a single pass is made through the table 
    which will purge committed deleted rows from the table.  This space is then
    available for future inserted rows, but remains allocated to the table.
    As this option scans every page of the table, it's performance is linearly 
    related to the size of the table.
    <p>
    DEFRAGMENT_ROWS:
    If DEFRAGMENT_ROWS is set to non-zero then a single defragment pass is made
    which will move existing rows from the end of the table towards the front
    of the table.  The goal of the defragment run is to empty a set of pages
    at the end of the table which can then be returned to the OS by the
    TRUNCATE_END option.  It is recommended to only run DEFRAGMENT_ROWS, if also
    specifying the TRUNCATE_END option.  This option scans the whole table and
    needs to update index entries for every base table row move, and thus execution
    time is linearly related to the size of the table.
    <p>
    TRUNCATE_END:
    If TRUNCATE_END is set to non-zero then all contiguous pages at the end of
    the table will be returned to the OS.  Running the PURGE_ROWS and/or 
    DEFRAGMENT_ROWS passes options may increase the number of pages affected.  
    This option itself does no scans of the table, so performs on the order of a 
    few system calls.
    <p>
    SQL example:
    To compress a table called CUSTOMER in a schema called US, using all 
    available compress options:
    call SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE('US', 'CUSTOMER', 1, 1, 1);

    To quickly just return the empty free space at the end of the same table, 
    this option will run much quicker than running all phases but will likely
    return much less space:
    call SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE('US', 'CUSTOMER', 0, 0, 1);

    Java example:
    To compress a table called CUSTOMER in a schema called US, using all 
    available compress options:

    CallableStatement cs = conn.prepareCall
    ("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, ?, ?, ?)");
    cs.setString(1, "US");
    cs.setString(2, "CUSTOMER");
    cs.setShort(3, (short) 1);
    cs.setShort(4, (short) 1);
    cs.setShort(5, (short) 1);
    cs.execute();

    To quickly just return the empty free space at the end of the same table, 
    this option will run much quicker than running all phases but will likely
    return much less space:

    CallableStatement cs = conn.prepareCall
    ("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, ?, ?, ?)");
    cs.setString(1, "US");
    cs.setString(2, "CUSTOMER");
    cs.setShort(3, (short) 0);
    cs.setShort(4, (short) 0);
    cs.setShort(5, (short) 1);
    cs.execute();

    <p>
    It is recommended that the SYSCS_UTIL.SYSCS_COMPRESS_TABLE procedure is 
    issued in auto-commit mode.
    Note: This procedure acquires an exclusive table lock on the table being compressed. All statement plans dependent on the table or its indexes are invalidated. For information on identifying unused space, see the Derby Server and Administration Guide.

    TODO LIST:
    o defragment requires table level lock in nested user transaction, which
      will conflict with user lock on same table in user transaction.

    **/
    public static void SYSCS_INPLACE_COMPRESS_TABLE(
    String  schema,
    String  tablename,
    int     purgeRows,
    int     defragmentRows,
    int     truncateEnd)
		throws SQLException
    {
 
        String query = 
            "alter table " + "\"" + schema + "\"" + "." + "\"" +  tablename + "\"" + 
			" compress inplace" +  (purgeRows != 0 ? " purge" : "")
			 +  (defragmentRows != 0 ? " defragment" : "")
			  +  (truncateEnd != 0 ? " truncate_end" : "");

		Connection conn = getDefaultConn();
        
        PreparedStatement ps = conn.prepareStatement(query);
		ps.executeUpdate();
        ps.close();

		conn.close();
    }

    public static String SYSCS_GET_RUNTIMESTATISTICS()
		throws SQLException
    {

		Object rts = ConnectionUtil.getCurrentLCC().getRunTimeStatisticsObject();

		if (rts == null)
			return null;

		return rts.toString();

    }


	/*
	** SQLJ Procedures.
	*/

	/**
		Install a jar file in the database.

		SQLJ.INSTALL_JAR

		@param url URL of the jar file to be installed in the database.
		@param jar SQL name jar will be installed as.
		@param deploy Ignored.

		@exception SQLException Error installing jar file.
	*/
	public static void INSTALL_JAR(String url, String jar, int deploy)
		throws SQLException {

		try {
            
            LanguageConnectionContext lcc = ConnectionUtil.getCurrentLCC();

			String[] st = IdUtil.parseMultiPartSQLIdentifier(jar.trim());

			String schemaName;
			String sqlName;
            
            if (st.length == 1)
            {
				schemaName = lcc.getCurrentSchemaName();
				sqlName = st[0];
            }
            else
            {
                schemaName = st[0];
				sqlName = st[1];
			}

			checkJarSQLName(sqlName);
            
            JarUtil.install(lcc, schemaName, sqlName, url);
		} 
		catch (StandardException se) {
			throw PublicAPI.wrapStandardException(se);
		}
	}

	/**
		Replace a jar file in the database.

		SQLJ.REPLACE_JAR

		@param url URL of the jar file to be installed in the database.
		@param jar SQL name of jar to be replaced.

		@exception SQLException Error replacing jar file.
	*/
	public static void REPLACE_JAR(String url, String jar)
		throws SQLException {

		try {
            
            LanguageConnectionContext lcc = ConnectionUtil.getCurrentLCC();

			String[] st = IdUtil.parseMultiPartSQLIdentifier(jar.trim());

            String schemaName;
            String sqlName;
            
            if (st.length == 1)
            {
                schemaName = lcc.getCurrentSchemaName();
                sqlName = st[0];
            }
            else
            {
                schemaName = st[0];
                sqlName = st[1];
            }

			checkJarSQLName(sqlName);
            
            JarUtil.replace(lcc,
                    schemaName, sqlName, url);
		} 
		catch (StandardException se) {
			throw PublicAPI.wrapStandardException(se);
		}
	}
	/**
		Remove a jar file from the database.

		@param jar      SQL name of jar to be replaced.
		@param undeploy Ignored.

		@exception SQLException Error removing jar file.
	*/
	public static void REMOVE_JAR(String jar, int undeploy)
		throws SQLException {

		try {
            
            LanguageConnectionContext lcc = ConnectionUtil.getCurrentLCC();

			String[] st = IdUtil.parseMultiPartSQLIdentifier(jar.trim());

            String schemaName;
            String sqlName;
            
            if (st.length == 1)
            {
                schemaName = lcc.getCurrentSchemaName();
                sqlName = st[0];
            }
            else
            {
                schemaName = st[0];
                sqlName = st[1];
            }

			checkJarSQLName(sqlName);
            
            JarUtil.drop(lcc, schemaName, sqlName);

		} 
		catch (StandardException se) {
			throw PublicAPI.wrapStandardException(se);
		}
	}

	private static void checkJarSQLName(String sqlName)
		throws StandardException {

		// weed out a few special cases that cause problems.
		if (   (sqlName.length() == 0)
			|| (sqlName.indexOf(':') != -1)
			) {

			throw StandardException.newException(SQLState.ID_PARSE_ERROR);
		}
	}

	/**
     * Export data from a table to given file.
     * <p>
     * Will be called by system procedure:
	 * SYSCS_EXPORT_TABLE(IN SCHEMANAME  VARCHAR(128), 
	 * IN TABLENAME    VARCHAR(128),  IN FILENAME VARCHAR(32672) , 
	 * IN COLUMNDELIMITER CHAR(1),  IN CHARACTERDELIMITER CHAR(1) ,  
	 * IN CODESET VARCHAR(128))
	 * @exception  StandardException  Standard exception policy.
     **/
	public static void SYSCS_EXPORT_TABLE(
	String  schemaName,
    String  tableName,
	String  fileName,
	String  columnDelimiter,
	String  characterDelimiter,
	String  codeset)
        throws SQLException
    {
		Connection conn = getDefaultConn();
		Export.exportTable(conn, schemaName , tableName , fileName ,
							  columnDelimiter , characterDelimiter, codeset);
		//export finished successfully, issue a commit 
		conn.commit();
	}


    /**
     * Export data from a table to given files. Large objects 
     * are exported to an external file and the reference to it is written 
     * in the main export file. 
     * <p>
     * Will be called by system procedure:
     * SYSCS_EXPORT_TABLE_LOBS_TO_EXTFILE(IN SCHEMANAME  VARCHAR(128), 
     * IN TABLENAME    VARCHAR(128),  IN FILENAME VARCHAR(32672) , 
     * IN COLUMNDELIMITER CHAR(1),  IN CHARACTERDELIMITER CHAR(1) ,  
     * IN CODESET VARCHAR(128), IN LOBSFILENAME VARCHAR(32672))
     * @exception  StandardException  Standard exception policy.
     **/
    public static void SYSCS_EXPORT_TABLE_LOBS_TO_EXTFILE(
    String  schemaName,
    String  tableName,
    String  fileName,
    String  columnDelimiter,
    String  characterDelimiter,
    String  codeset,
    String  lobsFileName)
        throws SQLException
    {
        Connection conn = getDefaultConn();
        Export.exportTable(conn, schemaName , tableName , fileName ,
                           columnDelimiter , characterDelimiter, 
                           codeset, lobsFileName);
        //export finished successfully, issue a commit 
        conn.commit();
    }

	

	
	/**
     * Export data from a  select statement to given file.
     * <p>
     * Will be called as 
	 * SYSCS_EXPORT_QUERY(IN SELECTSTATEMENT  VARCHAR(32672), 
	 * IN FILENAME VARCHAR(32672) , 
	 * IN COLUMNDELIMITER CHAR(1),  IN CHARACTERDELIMITER CHAR(1) ,  
	 * IN CODESET VARCHAR(128))
	 *
	 * @exception  StandardException  Standard exception policy.
     **/
	public static void SYSCS_EXPORT_QUERY(
    String  selectStatement,
	String  fileName,
	String  columnDelimiter,
	String  characterDelimiter,
	String  codeset)
        throws SQLException
    {
		Connection conn = getDefaultConn();
		Export.exportQuery(conn, selectStatement, fileName ,
							   columnDelimiter , characterDelimiter, codeset);
		
		//export finished successfully, issue a commit 
		conn.commit();
	}

    

    /**
     * Export data from a  select statement to given file. Large objects 
     * are exported to an external file and the reference to it is written 
     * in the main export file. 
     * <p>
     * Will be called as 
     * SYSCS_EXPORT_QUERY_LOBS_TO_EXTFILE(IN SELECTSTATEMENT  VARCHAR(32672),
     * IN FILENAME VARCHAR(32672) , 
     * IN COLUMNDELIMITER CHAR(1),  IN CHARACTERDELIMITER CHAR(1) ,  
     * IN CODESET VARCHAR(128), IN LOBSFILENAME VARCHAR(32672))
     *
     * @exception  StandardException  Standard exception policy.
     **/
    public static void SYSCS_EXPORT_QUERY_LOBS_TO_EXTFILE(
    String  selectStatement,
    String  fileName,
    String  columnDelimiter,
    String  characterDelimiter,
    String  codeset,
    String  lobsFileName)
        throws SQLException
    {
        Connection conn = getDefaultConn();
        Export.exportQuery(conn, selectStatement, fileName ,
                           columnDelimiter , characterDelimiter, 
                           codeset, lobsFileName);

        //export finished successfully, issue a commit 
        conn.commit();
    }





	/**
     * Import  data from a given file to a table.
     * <p>
     * Will be called by system procedure as
	 * SYSCS_IMPORT_TABLE(IN SCHEMANAME  VARCHAR(128), 
	 * IN TABLENAME    VARCHAR(128),  IN FILENAME VARCHAR(32672) , 
	 * IN COLUMNDELIMITER CHAR(1),  IN CHARACTERDELIMITER CHAR(1) ,  
	 * IN CODESET VARCHAR(128), IN  REPLACE SMALLINT)
	 * @exception  StandardException  Standard exception policy.
     **/
	public static void SYSCS_IMPORT_TABLE(
	String  schemaName,
    String  tableName,
	String  fileName,
	String  columnDelimiter,
	String  characterDelimiter,
	String  codeset,
	short   replace)
        throws SQLException
    {
		Connection conn = getDefaultConn();
		try{
			Import.importTable(conn, schemaName , tableName , fileName ,
                               columnDelimiter , characterDelimiter, codeset, 
                               replace, false);
		}catch(SQLException se)
		{
			//issue a rollback on any errors
			conn.rollback();
			throw  se;
		}
		//import finished successfull, commit it.
		conn.commit();
	}


    /**
     * Import  data from a given file to a table. Data for large object 
     * columns is in an external file, the reference to it is in the main 
     * input file. Read the lob data from the external file using the 
     * lob location info in the main import file. 
     * <p>
     * Will be called by system procedure as
     * SYSCS_IMPORT_TABLE_LOBS_FROM_EXTFILE(IN SCHEMANAME  VARCHAR(128), 
     * IN TABLENAME    VARCHAR(128),  IN FILENAME VARCHAR(32672) , 
     * IN COLUMNDELIMITER CHAR(1),  IN CHARACTERDELIMITER CHAR(1) ,  
     * IN CODESET VARCHAR(128), IN  REPLACE SMALLINT)
     * @exception  StandardException  Standard exception policy.
     **/
    public static void SYSCS_IMPORT_TABLE_LOBS_FROM_EXTFILE(
    String  schemaName,
    String  tableName,
    String  fileName,
    String  columnDelimiter,
    String  characterDelimiter,
    String  codeset,
    short   replace)
        throws SQLException
    {
        Connection conn = getDefaultConn();
        try{
            Import.importTable(conn, schemaName , tableName , fileName ,
                               columnDelimiter , characterDelimiter, 
                               codeset, replace, 
                               true //lobs in external file
                               );
        }catch(SQLException se)
        {
            //issue a rollback on any errors
            conn.rollback();
            throw  se;
        }
        //import finished successfull, commit it.
        conn.commit();
    }



	/**
      * Import data from a given file into the specified table columns from the 
	 * specified columns in the file.
     * <p>
     * Will be called as 
	 * SYSCS_IMPORT_DATA (IN SCHEMANAME VARCHAR(128), IN TABLENAME VARCHAR(128),
	 *                    IN INSERTCOLUMNLIST VARCHAR(32762), IN COLUMNINDEXES VARCHAR(32762),
	 *                    IN FILENAME VARCHAR(32762), IN COLUMNDELIMITER CHAR(1), 
	 *                    IN CHARACTERDELIMITER CHAR(1), IN CODESET VARCHAR(128), 
	 *                    IN REPLACE SMALLINT)
	 *
	 * @exception  StandardException  Standard exception policy.
     **/
	public static void SYSCS_IMPORT_DATA(
    String  schemaName,
    String  tableName,
	String  insertColumnList,
	String  columnIndexes,
	String  fileName,
	String  columnDelimiter,
	String  characterDelimiter,
	String  codeset,
	short   replace)
        throws SQLException
    {
		Connection conn = getDefaultConn();
		try{
			Import.importData(conn, schemaName , tableName ,
								  insertColumnList, columnIndexes, fileName,
								  columnDelimiter, characterDelimiter, 
								  codeset, replace, false);
		}catch(SQLException se)
		{
			//issue a rollback on any errors
			conn.rollback();
			throw  se;
		}

		//import finished successfull, commit it.
		conn.commit();
	}


    /**
     * Import data from a given file into the specified table columns 
     * from the  specified columns in the file. Data for large object 
     * columns is in an  external file, the reference to it is in the 
     * main input file. Read the lob data from the external file using 
     * the lob location info in the main import file. 
     * <p>
     * Will be called as 
     * SYSCS_IMPORT_DATA_LOBS_FROM_EXTFILE(IN SCHEMANAME VARCHAR(128), 
     *               IN TABLENAME VARCHAR(128),
     *               IN INSERTCOLUMNLIST VARCHAR(32762), 
     *               IN COLUMNINDEXES VARCHAR(32762),
     *               IN FILENAME VARCHAR(32762), IN COLUMNDELIMITER CHAR(1), 
     *               IN CHARACTERDELIMITER CHAR(1), IN CODESET VARCHAR(128), 
     *               IN REPLACE SMALLINT)
     *
     * @exception  StandardException  Standard exception policy.
     **/
    public static void SYSCS_IMPORT_DATA_LOBS_FROM_EXTFILE(
    String  schemaName,
    String  tableName,
    String  insertColumnList,
    String  columnIndexes,
    String  fileName,
    String  columnDelimiter,
    String  characterDelimiter,
    String  codeset,
    short   replace)
        throws SQLException
    {
        Connection conn = getDefaultConn();
        try{
            Import.importData(conn, schemaName , tableName ,
                              insertColumnList, columnIndexes, fileName,
                              columnDelimiter, characterDelimiter, 
                              codeset, replace, true);
        }catch(SQLException se)
        {
            //issue a rollback on any errors
            conn.rollback();
            throw  se;
        }

        //import finished successfull, commit it.
        conn.commit();
    }


	/**
     * Perform bulk insert using the specificed vti .
     * <p>
     * Will be called as 
	 * SYSCS_BULK_INSERT (IN SCHEMANAME VARCHAR(128), IN TABLENAME VARCHAR(128), 
	 *                    IN VTINAME VARCHAR(32762), IN VTIARG VARCHAR(32762))
	 *
	 * @exception  StandardException  Standard exception policy.
     **/
	public static void SYSCS_BULK_INSERT(
    String  schemaName,
    String  tableName,
	String  vtiName,
	String  vtiArg
	)
        throws SQLException
    {
		Connection conn = getDefaultConn();
		
		String entityName = (schemaName == null ? tableName : schemaName + "." + tableName); 
		String binsertSql = 
			"insert into " + entityName +
			" --DERBY-PROPERTIES insertMode=bulkInsert \n" +
			"select * from new " + vtiName + 
			"(" + 
			"'" + schemaName + "'" + ", " + 
			"'" + tableName + "'" +  ", " + 
			"'" + vtiArg + "'" +  ")" + 
			" as t"; 

		PreparedStatement ps = conn.prepareStatement(binsertSql);
		ps.executeUpdate();
		ps.close();
	}
	
    /**
     * Reload the policy file.
     * <p>
     * System procedure called thusly:
     *
     * SYSCS_UTIL.SYSCS_RELOAD_SECURITY_POLICY()
     **/
    public static void SYSCS_RELOAD_SECURITY_POLICY()
        throws SQLException
    {
        // If no security manager installed then there
        // is no policy to refresh. Calling Policy.getPolicy().refresh()
        // without a SecurityManager seems to lock in a policy with
        // no permissions thus ignoring the system property java.security.policy
        // when later installing a SecurityManager.
        if (System.getSecurityManager() == null)
            return;
        
        try {
            AccessController.doPrivileged(
                    new PrivilegedAction() {
                        public Object run() {
                            Policy.getPolicy().refresh();
                            return null;
                        }
                    });
        } catch (SecurityException se) {
            throw Util.policyNotReloaded(se);
        }
    }

	/**
	 * Method to return the constant PI.
	 * SYSFUN.PI().
	 * @return PI
	 */
	public static double PI()
	{
		return StrictMath.PI;
	}
	
	/**
	 * Constant for natural log(10).
	 */
	private static final double LOG10 = StrictMath.log(10.0d);
	
	/**
	 * Base 10 log function. SYSFUN.LOG10
	 * Calculated by
	 * <code>
	 * log(value) / log(10)
	 * </code>
	 * where log is the natural log.
	 */
	public static double LOG10(double value)
	{
		return StrictMath.log(value) / LOG10;
	}

	/**
	 * Cotangent function. SYSFUN.COT
	 * @see <a href="http://mathworld.wolfram.com/HyperbolicFunctions.html">HyperbolicFunctions</a>
	 * @return 1 / tan(x)
	 */
	public static double COT(double value)
	{
		return 1.0 / StrictMath.tan(value);
	}

	/**
	 * Hyperbolic Cosine function. SYSFUN.COSH
	 * @see <a href="http://mathworld.wolfram.com/HyperbolicFunctions.html">HyperbolicFunctions</a>
	 * @return 1/2 (e^x + e^-x)
	 */
	public static double COSH(double value)
	{
		return (StrictMath.exp(value) + StrictMath.exp(-value)) / 2.0;
	}

	/**
	 * Hyperbolic Sine function. SYSFUN.SINH
	 * @see <a href="http://mathworld.wolfram.com/HyperbolicFunctions.html">HyperbolicFunctions</a>
	 * @return 1/2 (e^x - e^-x)
	 */
	public static double SINH(double value)
	{
		return (StrictMath.exp(value) - StrictMath.exp(-value)) / 2.0;
	}

	/**
	 * Hyperbolic Tangent function. SYSFUN.TANH
	 * @see <a href="http://mathworld.wolfram.com/HyperbolicFunctions.html">HyperbolicFunctions</a>
	 * @return (e^x - e^-x) / (e^x + e^-x)
	 */
	public static double TANH(double value)
	{
		return (StrictMath.exp(value) - StrictMath.exp(-value)) /
			(StrictMath.exp(value) + StrictMath.exp(-value));
	}

	/**
	 * Method to return the sign of the given value.
	 * SYSFUN.SIGN().
	 * @return 0, 1 or -1
	 */
	public static int SIGN(double value)
	{
		return value < 0 ? -1 : value > 0 ? 1 : 0;
	}

	/**
	 * Pseudo-random number function.
	 * @return a random number
	 */
	public static double RAND(int seed)
	{
		return (new Random(seed)).nextDouble();
	}
    
    /**
     * Set the connection level authorization for
     * a specific user - SYSCS_UTIL.SYSCS_SET_USER_ACCESS.
     * 
     * @param userName name of the user in its normal form (not a SQL identifier).
     * @param connectionPermission
     * @throws SQLException Error setting the permission
     */
    public static void SYSCS_SET_USER_ACCESS(String userName,
            String connectionPermission)
        throws SQLException
    {
         try {
            
            if (userName == null)
                 throw StandardException.newException(SQLState.AUTH_INVALID_USER_NAME,
                         userName);
            
            String addListProperty;
            if (Property.FULL_ACCESS.equals(connectionPermission))
            {
                addListProperty = Property.FULL_ACCESS_USERS_PROPERTY;
            }
            else if (Property.READ_ONLY_ACCESS.equals(connectionPermission))
            {               
                addListProperty = Property.READ_ONLY_ACCESS_USERS_PROPERTY;
            }
            else if (connectionPermission == null)
            {
                // Remove from the lists but don't add back into any.
                addListProperty = null;
            }
            else
                throw StandardException.newException(SQLState.UU_UNKNOWN_PERMISSION,
                        connectionPermission);

            // Always remove from both lists to avoid any repeated
            // user on list errors.
            removeFromAccessList(Property.FULL_ACCESS_USERS_PROPERTY,
                    userName);
            removeFromAccessList(Property.READ_ONLY_ACCESS_USERS_PROPERTY,
                    userName);
            
            
            if (addListProperty != null) {
                String addList = SYSCS_GET_DATABASE_PROPERTY(addListProperty);
                SYSCS_SET_DATABASE_PROPERTY(addListProperty,
                    IdUtil.appendNormalToList(userName, addList));
            }
            
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        }
    }
  
    /**
     * Utility method for SYSCS_SET_USER_ACCESS removes a user from
     * one of the access lists, driven by the property name.
     */
    private static void removeFromAccessList(
            String listProperty, String userName)
        throws SQLException, StandardException
    {
        String removeList = SYSCS_GET_DATABASE_PROPERTY(listProperty);
        if (removeList != null)
        {
            SYSCS_SET_DATABASE_PROPERTY(listProperty,
                    IdUtil.deleteId(userName, removeList));
        }
    }
    
    /**
     * Get the connection level authorization for
     * a specific user - SYSCS_UTIL.SYSCS_GET_USER_ACCESS.
     * 
     * @param userName name of the user in its normal form (not a SQL identifier).

     */
    public static String SYSCS_GET_USER_ACCESS(String userName)
        throws SQLException
    {
        try {
            
            if (userName == null)
                throw StandardException.newException(SQLState.AUTH_INVALID_USER_NAME,
                        userName);
           
            String fullUserList =
                SYSCS_GET_DATABASE_PROPERTY(Property.FULL_ACCESS_USERS_PROPERTY);
            if (IdUtil.idOnList(userName, fullUserList))
                return Property.FULL_ACCESS;
            
            String readOnlyUserList =
                SYSCS_GET_DATABASE_PROPERTY(Property.READ_ONLY_ACCESS_USERS_PROPERTY);
            if (IdUtil.idOnList(userName, readOnlyUserList))
                return Property.READ_ONLY_ACCESS;
            
            String defaultAccess = 
                SYSCS_GET_DATABASE_PROPERTY(Property.DEFAULT_CONNECTION_MODE_PROPERTY);
            if (defaultAccess != null)
                defaultAccess = StringUtil.SQLToUpperCase(defaultAccess);
            else
                defaultAccess = Property.FULL_ACCESS; // is the default.
            
            return defaultAccess;
            
        } catch (StandardException se) {
            throw PublicAPI.wrapStandardException(se);
        }
    }
    
    /**
     * Empty as much of the cache as possible. It is not guaranteed 
     * that the cache is empty after this call, as statements may be kept
     * by currently executing queries, activations that are about to be garbage
     * collected.
     */
    public static void SYSCS_EMPTY_STATEMENT_CACHE()
       throws SQLException
    {
       LanguageConnectionContext lcc = ConnectionUtil.getCurrentLCC();
       
       CacheManager statementCache =
           lcc.getLanguageConnectionFactory().getStatementCache();
       
       if (statementCache != null)
           statementCache.ageOut();
    }
}
