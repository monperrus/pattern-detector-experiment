/*

   Derby - Class org.apache.derby.impl.sql.compile.CursorNode

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

package	org.apache.derby.impl.sql.compile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.compiler.MethodBuilder;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.shared.common.sanity.SanityManager;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.conn.Authorizer;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.impl.sql.CursorInfo;
import org.apache.derby.impl.sql.CursorTableReference;

/**
 * A CursorNode represents a result set that can be returned to a client.
 * A cursor can be a named cursor created by the DECLARE CURSOR statement,
 * or it can be an unnamed cursor associated with a SELECT statement (more
 * precisely, a table expression that returns rows to the client).  In the
 * latter case, the cursor does not have a name.
 *
 */

public class CursorNode extends DMLStatementNode
{
    final static int UNSPECIFIED = 0;
	public final static int READ_ONLY = 1;
    final static int UPDATE = 2;

	private String		name;
	private OrderByList	orderByList;
	private ValueNode   offset;     // <result offset clause> value
	private ValueNode   fetchFirst; // <fetch first clause> value
    private boolean hasJDBClimitClause; // true if using JDBC limit/offset escape syntax
	private String		statementType;
	private int		updateMode;
	private boolean		needTarget;

	/**
	** There can only be a list of updatable columns when FOR UPDATE
	** is specified as part of the cursor specification.
	*/
	private List<String> updatableColumns;
	private FromTable updateTable;
    /**
     * List of {@code TableDescriptor}s for base tables whose associated
     * indexes should be checked for stale statistics.
     */
    private ArrayList<TableDescriptor> statsToUpdate;
    private boolean checkIndexStats;

	//If cursor references session schema tables, save the list of those table names in savedObjects in compiler context
	//Following is the position of the session table names list in savedObjects in compiler context
	//At generate time, we save this position in activation for easy access to session table names list from compiler context
	private int indexOfSessionTableNamesInSavedObjects = -1;

	/**
     * Constructor for a CursorNode
	 *
     * @param statementType      Type of statement (SELECT, UPDATE, INSERT)
     * @param resultSet          A ResultSetNode specifying the result set for
     *                           the cursor
     * @param name               The name of the cursor, null if no name
     * @param orderByList        The order by list for the cursor, null if no
     *                           order by list
     * @param offset             The value of a <result offset clause> if
     *                           present
     * @param fetchFirst         The value of a <fetch first clause> if present
     * @param hasJDBClimitClause True if the offset/fetchFirst clauses come
     *                           from JDBC limit/offset escape syntax
     * @param updateMode         The user-specified update mode for the cursor,
     *                           for example, CursorNode.READ_ONLY
     * @param updatableColumns   The array of updatable columns specified by
     *                           the user in the FOR UPDATE clause, null if no
     *                           updatable columns specified.  May only be
     *                           provided if the updateMode parameter is
     *                           CursorNode.UPDATE.
     * @param cm                 The context manager
	 */
    CursorNode(String         statementType,
               ResultSetNode  resultSet,
               String         name,
               OrderByList    orderByList,
               ValueNode      offset,
               ValueNode      fetchFirst,
               boolean        hasJDBClimitClause,
               int            updateMode,
               String[]       updatableColumns,
               ContextManager cm)
	{
        super(resultSet, cm);
        this.name = name;
        this.statementType = statementType;
        this.orderByList = orderByList;
        this.offset = offset;
        this.fetchFirst = fetchFirst;
        this.hasJDBClimitClause = hasJDBClimitClause;
        this.updateMode = updateMode;
        this.updatableColumns =
                updatableColumns == null ?
                null : Arrays.asList(updatableColumns);

		/*
		** This is a sanity check and not an error since the parser
		** controls setting updatableColumns and updateMode.
		*/
        if (SanityManager.DEBUG) {
            SanityManager.ASSERT(
                    this.updatableColumns == null ||
                    this.updatableColumns.isEmpty() ||
                    this.updateMode == UPDATE,
                    "Can only have explicit updatable columns if " +
                        "update mode is UPDATE");
        }
	}

	/**
	 * Convert this object to a String.  See comments in QueryTreeNode.java
	 * for how this should be done for tree printing.
	 *
	 * @return	This object as a String
	 */
    @Override
	public String toString()
	{
		if (SanityManager.DEBUG)
		{
			return "name: " + name + "\n" +
				"updateMode: " + updateModeString(updateMode) + "\n" +
				super.toString();
		}
		else
		{
			return "";
		}
	}

    String statementToString()
	{
		return statementType;
	}

	/**
	 * Support routine for translating an updateMode identifier to a String
	 *
	 * @param updateMode	An updateMode identifier
	 *
	 * @return	A String representing the update mode.
	 */

	private static String updateModeString(int updateMode)
	{
		if (SanityManager.DEBUG)
		{
			switch (updateMode)
			{
			  case UNSPECIFIED:
				return "UNSPECIFIED (" + UNSPECIFIED + ")";

			  case READ_ONLY:
				return "READ_ONLY (" + READ_ONLY + ")";

			  case UPDATE:
				return "UPDATE (" + UPDATE + ")";

			  default:
				return "UNKNOWN VALUE (" + updateMode + ")";
			}
		}
		else
		{
			return "";
		}
	}

	/**
	 * Prints the sub-nodes of this object.  See QueryTreeNode.java for
	 * how tree printing is supposed to work.
	 *
	 * @param depth		The depth of this node in the tree
	 */
    @Override
    void printSubNodes(int depth)
	{
		if (SanityManager.DEBUG)
		{
			super.printSubNodes(depth);

            if (orderByList != null) {
                printLabel(depth, "orderByList: "  + depth);
                orderByList.treePrint(depth + 1);
            }

            if (offset != null) {
                printLabel(depth, "offset:");
                offset.treePrint(depth + 1);
            }

            if (fetchFirst != null) {
                printLabel(depth, "fetch first/next:");
                fetchFirst.treePrint(depth + 1);
            }
		}
	}

	/**
	 * Bind this CursorNode.  This means looking up tables and columns and
	 * getting their types, and figuring out the result types of all
	 * expressions, as well as doing view resolution, permissions checking,
	 * etc. It also includes determining whether an UNSPECIFIED cursor
	 * is updatable or not, and verifying that an UPDATE cursor actually is.
	 *
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public void bindStatement() throws StandardException
	{
		DataDictionary				dataDictionary;

		dataDictionary = getDataDictionary();
        checkIndexStats = (dataDictionary.getIndexStatsRefresher(true) != null);

		// This is how we handle queries like: SELECT A FROM T ORDER BY B.
		// We pull up the order by columns (if they don't appear in the SELECT
		// LIST) and let the bind() do the job.  Note that the pullup is done
		// before the bind() and we may avoid pulling up ORDERBY columns that
		// would otherwise be avoided, e.g., "SELECT * FROM T ORDER BY B".
		// Pulled-up ORDERBY columns that are duplicates (like the above "SELECT
		// *" query will be removed in bindOrderByColumns().
		// Finally, given that extra columns may be added to the SELECT list, we
		// inject a ProjectRestrictNode so that only the user-specified columns
		// will be returned (see genProjectRestrict() in SelectNode.java).
		if (orderByList != null)
		{
			orderByList.pullUpOrderByColumns(resultSet);
		}

		getCompilerContext().pushCurrentPrivType(getPrivType());
		try {
            FromList    fromList = new FromList(
                    getOptimizerFactory().doJoinOrderOptimization(),
					getContextManager());

			/* Check for ? parameters directly under the ResultColums */
			resultSet.rejectParameters();

			super.bind(dataDictionary);

			// bind the query expression
			resultSet.bindResultColumns(fromList);

			// this rejects any untyped nulls in the select list
			// pass in null to indicate that we don't have any
			// types for this node
			resultSet.bindUntypedNullsToResultColumns(null);

			// Reject any XML values in the select list; JDBC doesn't
			// define how we bind these out, so we don't allow it.
			resultSet.rejectXMLValues();

			/* Verify that all underlying ResultSets reclaimed their FromList */
			if (SanityManager.DEBUG) {
				SanityManager.ASSERT(fromList.size() == 0,
					"fromList.size() is expected to be 0, not "
							+ fromList.size()
							+ " on return from RS.bindExpressions()");
			}
			
			//DERBY-4191 Make sure that we have minimum select privilege on 
			//each of the tables in the query.
			getCompilerContext().pushCurrentPrivType(Authorizer.MIN_SELECT_PRIV);
			FromList resultSetFromList = resultSet.getFromList();
			for (int index = 0; index < resultSetFromList.size(); index++) {
                Object fromTable = resultSetFromList.elementAt(index);
                if (fromTable instanceof FromBaseTable) {
                    collectTablePrivsAndStats((FromBaseTable)fromTable);
                }
            }
			getCompilerContext().popCurrentPrivType();
		}
		finally
		{
			getCompilerContext().popCurrentPrivType();
		}

		// bind the order by
		if (orderByList != null)
		{
			orderByList.bindOrderByColumns(resultSet);
		}


        bindOffsetFetch(offset, fetchFirst);

		// bind the updatability

		// if it says it is updatable, verify it.
		if (updateMode == UPDATE)
		{
			int checkedUpdateMode;

			checkedUpdateMode = determineUpdateMode(dataDictionary);
			if (SanityManager.DEBUG)
			SanityManager.DEBUG("DumpUpdateCheck","update mode is UPDATE ("+updateMode+") checked mode is "+checkedUpdateMode);
			if (updateMode != checkedUpdateMode)
					throw StandardException.newException(SQLState.LANG_STMT_NOT_UPDATABLE);
		}

		// if it doesn't know if it is updatable, determine it
		if (updateMode == UNSPECIFIED)
		{
		    // If the statement is opened with CONCUR_READ_ONLY, the upgrade mode is 
		    // set to read only.
		    
		    // NOTE: THIS IS NOT COMPATIBLE WITH THE ISO/ANSI SQL STANDARD.

		    // According to the SQL-standard:
		    // If updatability is not specified, a SELECT * FROM T will be implicitely
		    // read only in the context of a cursor which is insensitive, scrollable or
		    // have an order by clause. Otherwise it is implicitely updatable.
		    
		    // In Derby, we make a SELECT * FROM T updatable if the concurrency mode is
		    // ResultSet.CONCUR_UPDATE. If we do make all SELECT * FROM T  updatable
		    // by default, we cannot use an index on any single-table select, unless it
		    // was declared FOR READ ONLY. This would be pretty terrible, so we are
		    // breaking the ANSI rules.

		    if (getLanguageConnectionContext().getStatementContext().isForReadOnly()) {
			updateMode = READ_ONLY;
		    } else {
			updateMode = determineUpdateMode(dataDictionary);
		    }
		    		    
			//if (SanityManager.DEBUG)
			//SanityManager.DEBUG("DumpUpdateCheck","update mode is UNSPECIFIED ("+UNSPECIFIED+") checked mode is "+updateMode);
		}
		
		if (updateMode == READ_ONLY) {
		    updatableColumns = null; // don't need them any more
		}

		// bind the update columns
		if (updateMode == UPDATE)
		{
			bindUpdateColumns(updateTable);

			// If the target table is a FromBaseTable, mark the updatable
            // columns.
            if (updateTable != null)
			{
                updateTable.markUpdatableByCursor(updatableColumns);
				//make sure that alongwith the FromTable, we keep other ResultSetLists
				//in correct state too. ResultSetMetaData.isWritable looks at this to
				//return the correct value.
				resultSet.getResultColumns().markColumnsInSelectListUpdatableByCursor(
					updatableColumns);
			}
		}

		resultSet.renameGeneratedResultNames();

		//need to look for SESSION tables only if global temporary tables declared for the connection
		if (getLanguageConnectionContext().checkIfAnyDeclaredGlobalTempTablesForThisConnection())
		{
			//If this cursor has references to session schema tables, save the names of those tables into compiler context
			//so they can be passed to execution phase.
			ArrayList<String> sessionSchemaTableNames = getSessionSchemaTableNamesForCursor();
			if (sessionSchemaTableNames != null)
				indexOfSessionTableNamesInSavedObjects = getCompilerContext().addSavedObject(sessionSchemaTableNames);
		}

	}

    /**
     * Collects required privileges for all types of tables, and table
     * descriptors for base tables whose index statistics we want to check for
     * staleness (or to create).
     *
     * @param fromTable the table
     */
    private void collectTablePrivsAndStats(FromBaseTable fromTable) {
        TableDescriptor td = fromTable.getTableDescriptor();
        if (fromTable.isPrivilegeCollectionRequired()) {
            // We ask for MIN_SELECT_PRIV requirement of the first column in
            // the table. The first column is just a place holder. What we
            // really do at execution time when we see we are looking for
            // MIN_SELECT_PRIV privilege is as follows:
            //
            // 1) We will look for SELECT privilege at table level.
            // 2) If not found, we will look for SELECT privilege on
            //    ANY column, not necessarily the first column. But since
            //    the constructor for column privilege requires us to pass
            //    a column descriptor, we just choose the first column for
            //    MIN_SELECT_PRIV requirement.
            getCompilerContext().addRequiredColumnPriv(
                    td.getColumnDescriptor(1));
        }
        // Save a list of base tables to check the index statistics for at a
        // later time. We want to compute statistics for base user tables only,
        // not for instance system tables or VTIs (see TableDescriptor for a
        // list of all available "table types").
        if (checkIndexStats &&
                td.getTableType() == TableDescriptor.BASE_TABLE_TYPE) {
            if (statsToUpdate == null) {
                statsToUpdate = new ArrayList<TableDescriptor>();
            }
            statsToUpdate.add(td);
        }
    }

	/**
	 * Return true if the node references SESSION schema tables (temporary or permanent)
	 *
	 * @return	true if references SESSION schema tables, else false
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public boolean referencesSessionSchema()
		throws StandardException
	{
		//If this node references a SESSION schema table, then return true. 
		return resultSet.referencesSessionSchema();
	}

	//Check if this cursor references any session schema tables. If so, pass those names to execution phase through savedObjects
	//This list will be used to check if there are any holdable cursors referencing temporary tables at commit time.
	//If yes, then the data in those temporary tables should be preserved even if they are declared with ON COMMIT DELETE ROWS option
	protected ArrayList<String> getSessionSchemaTableNamesForCursor()
		throws StandardException
	{
		FromList fromList = resultSet.getFromList();
		int fromListSize = fromList.size();
		FromTable fromTable;
		ArrayList<String> sessionSchemaTableNames = null;

		for( int i = 0; i < fromListSize; i++)
		{
			fromTable = (FromTable) fromList.elementAt(i);
			if (fromTable instanceof FromBaseTable && isSessionSchema(fromTable.getTableDescriptor().getSchemaDescriptor()))
			{
				if (sessionSchemaTableNames == null)
					sessionSchemaTableNames = new ArrayList<String>();
				sessionSchemaTableNames.add(fromTable.getTableName().getTableName());
			}
		}

		return sessionSchemaTableNames;
	}

	/**
	 * Take a cursor and determine if it is UPDATE
	 * or READ_ONLY based on the shape of the cursor specification.
	 * <p>
	 * The following conditions make a cursor read only:
	 * <UL>
	 * <LI>if it says FOR READ ONLY
	 * <LI>if it says ORDER BY
	 * <LI>if its query specification is not read only. At present this
	 *     is explicitly tested here, with these conditions.  At some future
	 *     point in time, this checking ought to be moved into the
	 *     ResultSet nodes themselves.  The conditions for a query spec.
     *     not to be read only include:
	 *     <UL>
	 *     <LI>if it has a set operation such as UNION or INTERSECT, i.e.
	 *         does not have a single outermost SELECT
	 *     <LI>if it does not have exactly 1 table in its FROM list;
	 *         0 tables would occur if we ever support a SELECT without a
	 *         FROM e.g., for generating a row without an underlying table
	 *         (like what we do for an INSERT of a VALUES list); >1 tables
	 *         occurs when joins are in the tree.
	 *     <LI>if the table in its FROM list is not a base table (REMIND
	 *         when views/from subqueries are added, this should be relaxed to
     *         be that the table is not updatable)
	 *     <LI>if it has a GROUP BY or HAVING (NOTE I am assuming that if
	 *         and aggregate is detected in a SELECT w/o a GROUP BY, one
	 *         has been added to show that the whole table is a group)
	 *     <LI> NOTE that cursors are updatable even if none of the columns
	 *         in the select are updatable -- what they care about is the
	 *         updatability of the columns of the target table.
	 *     </UL>
	 * </UL>
	 *
	 * @return the known update mode for the cursor.
	 *
	 * @exception StandardException		Thrown on error
	 */
	private int determineUpdateMode(DataDictionary dataDictionary)
		throws StandardException
	{
		SelectNode selectNode;
		FromList tables;
		FromTable targetTable;

		if (updateMode == READ_ONLY)
		{
			return READ_ONLY;
		}

		if (orderByList != null)
		{
			if (SanityManager.DEBUG)
			SanityManager.DEBUG("DumpUpdateCheck","cursor has order by");
			return READ_ONLY;
		}

		// get the ResultSet to tell us what it thinks it is
		// and the target table
		if (! resultSet.isUpdatableCursor(dataDictionary))
		{
			return READ_ONLY;
		}

		// The FOR UPDATE clause has two uses:
		//
		// for positioned cursor updates
		//
		// to change locking behaviour of the select
		// to reduce deadlocks on subsequent updates
		// in the same transaction.
		//
		// We now support this latter case, without requiring
		// that the source of the rows be able to implement
		// a positioned update.

		updateTable = resultSet.getCursorTargetTable();

		/* Tell the table that it is the cursor target */
		if (updateTable.markAsCursorTargetTable()) {
			/* Cursor is updatable - remember to generate the position code */
			needTarget = true;
		}


		return UPDATE;
	}

	/**
	 * Optimize a DML statement (which is the only type of statement that
	 * should need optimizing, I think). This method over-rides the one
	 * in QueryTreeNode.
	 *
	 * This method takes a bound tree, and returns an optimized tree.
	 * It annotates the bound tree rather than creating an entirely
	 * new tree.
	 *
	 * Throws an exception if the tree is not bound, or if the binding
	 * is out of date.
	 *
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public void optimizeStatement() throws StandardException
	{
		// Push the order by list down to the ResultSet
		if (orderByList != null)
		{
			// If we have more than 1 ORDERBY columns, we may be able to
			// remove duplicate columns, e.g., "ORDER BY 1, 1, 2".
			if (orderByList.size() > 1)
			{
				orderByList.removeDupColumns();
			}

			resultSet.pushOrderByList(orderByList);
			orderByList = null;
		}

        resultSet.pushOffsetFetchFirst( offset, fetchFirst, hasJDBClimitClause );

        super.optimizeStatement();

	}

	/**
	 * Returns the type of activation this class
	 * generates.
	 * 
	 * @return either (NEED_CURSOR_ACTIVATION
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	int activationKind()
	{
		return NEED_CURSOR_ACTIVATION;
	}

	/**
	 * Do code generation for this CursorNode
	 *
	 * @param acb	The ActivationClassBuilder for the class being built
	 * @param mb	The method the generated code is to go into
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    void generate(ActivationClassBuilder acb, MethodBuilder mb) throws StandardException
	{
		if (indexOfSessionTableNamesInSavedObjects != -1 ) //if this cursor references session schema tables, do following
		{
			MethodBuilder constructor = acb.getConstructor();
			constructor.pushThis();
			constructor.push(indexOfSessionTableNamesInSavedObjects);
			constructor.putField(org.apache.derby.iapi.reference.ClassName.BaseActivation, "indexOfSessionTableNamesInSavedObjects", "int");
			constructor.endStatement();
    }

		// generate the parameters
		generateParameterValueSet(acb);

		// tell the outermost result set that it is the outer
		// result set of the statement.
		resultSet.markStatementResultSet();

		// this will generate an expression that will be a ResultSet
	    resultSet.generate(acb, mb);

		/*
		** Generate the position code if this cursor is updatable.  This
		** involves generating methods to get the cursor result set, and
		** the target result set (which is for the base row).  Also,
		** generate code to store the cursor result set in a generated
		** field.
		*/
		if (needTarget)
		{
			// PUSHCOMPILE - could be put into a single method
			acb.rememberCursor(mb);
			acb.addCursorPositionCode();
		}
	}

	// class interface

    String getUpdateBaseTableName()
	{
		return (updateTable == null) ? null : updateTable.getBaseTableName();
	}

    String getUpdateExposedTableName()
		throws StandardException
	{
		return (updateTable == null) ? null : updateTable.getExposedName();
	}

    String getUpdateSchemaName()
		throws StandardException
	{
		//we need to use the base table for the schema name
		return (updateTable == null) ? null : ((FromBaseTable)updateTable).getTableNameField().getSchemaName();
	}

    int getUpdateMode()
	{
		return updateMode;
	}

	/**
	 * Returns whether or not this Statement requires a set/clear savepoint
	 * around its execution.  The following statement "types" do not require them:
	 *		Cursor	- unnecessary and won't work in a read only environment
	 *		Xact	- savepoint will get blown away underneath us during commit/rollback
	 *
	 * @return boolean	Whether or not this Statement requires a set/clear savepoint
	 */
    @Override
	public boolean needsSavepoint()
	{
		return false;
	}

	/**
	 * Get information about this cursor.  For sps,
	 * this is info saved off of the original query
	 * tree (the one for the underlying query).
	 *
	 * @return	the cursor info
	 * @exception StandardException thrown if generation fails
	 */
    @Override
	public Object getCursorInfo()
		throws StandardException
	{
		if (!needTarget)
			return null;

		return new CursorInfo(updateMode,
								new CursorTableReference(
										getUpdateExposedTableName(),
										getUpdateBaseTableName(),
										getUpdateSchemaName()),
                                updatableColumns);
	}

	/**
		Bind the update columns by their names to the target table
		of the cursor specification.
		Doesn't check for duplicates in the list, although it could...
		REVISIT: If the list is empty, should it expand it out? at present,
		it leaves it empty.
	
		@param targetTable	The underlying target table 
	
		@exception StandardException		Thrown on error
	 */
	private void bindUpdateColumns(FromTable targetTable)
					throws StandardException 
	{
		int size = updatableColumns.size();
		TableDescriptor tableDescriptor;
		String columnName;
        ResultColumnList rcl = resultSet.getResultColumns();

		for (int index = 0; index < size; index++)
		{
		    columnName = updatableColumns.get(index);
		    tableDescriptor = targetTable.getTableDescriptor();
		    if ( tableDescriptor.getColumnDescriptor(columnName) == null)
		    {
					throw StandardException.newException(SQLState.LANG_COLUMN_NOT_FOUND, columnName);
		    }

            // Make sure that we are not using correlation names for updatable
            // columns.
            //
            // Example:
            //     select c11 as col1, 2, c13 as col3 from t1
            //         for update of c11, c12
            //
            // The correlation name for c11 will cause an exception because
            // Derby does not support correlation name for updatable
            // columns. However, a correlation name for c13 is ok because it is
            // a read only column.

            for (ResultColumn rc : rcl) {
                // Look through each column in the resultset for cursor.
                if (rc.getSourceTableName() == null) {
                    // Continue to look at the next column because this is
                    // a derived column in the select list.
                    continue;
                }

                if (rc.getExpression() != null && rc.getExpression().getColumnName().equals(columnName) &&  !rc.getName().equals(columnName)) {
                    throw StandardException.newException(SQLState.LANG_CORRELATION_NAME_FOR_UPDATABLE_COLUMN_DISALLOWED_IN_CURSOR, columnName);
                }
		    }
		}
	}

    String getXML()
	{
		return null;
	}

    /**
     * Returns a list of base tables for which the index statistics of the
     * associated indexes should be updated.
     *
     * @return A list of table descriptors (potentially empty).
     * @throws StandardException if accessing the index descriptors of a base
     *      table fails
     */
    @Override
    public TableDescriptor[] updateIndexStatisticsFor()
            throws StandardException {
        if (!checkIndexStats || statsToUpdate == null) {
            return EMPTY_TD_LIST;
        }
        // Remove table descriptors whose statistics are considered up-to-date.
        // Iterate backwards to remove elements, chances are high the stats are
        // mostly up-to-date (minor performance optimization to avoid copy).
        for (int i=statsToUpdate.size() -1; i >= 0; i--) {
            TableDescriptor td = statsToUpdate.get(i);
            if (td.getAndClearIndexStatsIsUpToDate()) {
                statsToUpdate.remove(i);
            }
        }
        if (statsToUpdate.isEmpty()) {
            return EMPTY_TD_LIST;
        } else {
            TableDescriptor[] tmp = new TableDescriptor[statsToUpdate.size()];
            statsToUpdate.toArray(tmp);
            statsToUpdate.clear();
            return tmp;
        }
    }
    
	/**
	 * Accept the visitor for all visitable children of this node.
	 * 
	 * @param v the visitor
	 *
	 * @exception StandardException on error
	 */
    @Override
	void acceptChildren(Visitor v)
		throws StandardException
	{
        super.acceptChildren(v);

        if (orderByList != null) { orderByList.acceptChildren( v ); }
        if (offset != null) { offset.acceptChildren( v ); }
        if (fetchFirst != null) { fetchFirst.acceptChildren( v ); }
	}

}
