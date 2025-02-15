/*

   Derby - Class org.apache.derby.impl.sql.compile.GroupByColumn

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

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.compile.Visitable;

import org.apache.derby.iapi.types.TypeId;

import org.apache.derby.iapi.reference.SQLState;

import org.apache.derby.iapi.services.sanity.SanityManager;

import java.util.Vector;

/**
 * A GroupByColumn is a column in the GROUP BY clause.
 *
 */
public class GroupByColumn extends OrderedColumn 
{
	private ValueNode columnExpression;
	
	/**
	 * Initializer.
	 *
	 * @param colRef	The ColumnReference for the grouping column
	 */
	public void init(Object colRef) 
	{
		this.columnExpression = (ValueNode)colRef;
	}

	/**
	 * Prints the sub-nodes of this object.  See QueryTreeNode.java for
	 * how tree printing is supposed to work.
	 *
	 * @param depth		The depth of this node in the tree
	 */

	public void printSubNodes(int depth)
	{
		if (SanityManager.DEBUG)
		{
			super.printSubNodes(depth);

			if (columnExpression != null)
			{
				printLabel(depth, "columnExpression: ");
				columnExpression.treePrint(depth + 1);
			}
		}
	}

	/**
	 * Get the name of this column
	 *
	 * @return	The name of this column
	 */
	public String getColumnName() 
	{
		return columnExpression.getColumnName();
	}

	/**
	 * Bind this grouping column.
	 *
	 * @param fromList			The FROM list to use for binding
	 * @param subqueryList		The SubqueryList we are building as we hit
	 *							SubqueryNodes.
	 * @param aggregateVector	The aggregate vector we build as we hit 
	 *							AggregateNodes.
	 *
	 * @exception StandardException	Thrown on error
	 */

	public void bindExpression(
			FromList fromList, 
			SubqueryList subqueryList,
			Vector	aggregateVector) 
				throws StandardException
	{
		/* Bind the ColumnReference to the FromList */
		columnExpression = (ValueNode) columnExpression.bindExpression(fromList,
							  subqueryList,
							  aggregateVector);

		// Verify that we can group on the column
		if (columnExpression.isParameterNode()) 
		{
			throw StandardException.newException(SQLState.LANG_INVALID_COL_REF_GROUPED_SELECT_LIST,
					columnExpression);
		}
		/*
		 * Do not check to see if we can map user types
		 * to built-in types.  The ability to do so does
		 * not mean that ordering will work.  In fact,
		 * as of version 2.0, ordering does not work on
		 * user types.
		 */
		TypeId ctid = columnExpression.getTypeId();
		if (! ctid.orderable(getClassFactory()))
		{
			throw StandardException.newException(SQLState.LANG_COLUMN_NOT_ORDERABLE_DURING_EXECUTION, 
							ctid.getSQLTypeName());
		}
	}

	public ValueNode getColumnExpression() 
	{
		return columnExpression;
	}

	public void setColumnExpression(ValueNode cexpr) 
	{
		this.columnExpression = cexpr;
		
	}

	/**
	 * Accept the visitor for all visitable children of this node.
	 *
	 * @param v the visitor
	 *
	 * @exception StandardException on error
	 */
	void acceptChildren(Visitor v)
		throws StandardException {

		super.acceptChildren(v);

		if (columnExpression != null) {
			columnExpression = (ValueNode)columnExpression.accept(v);
		}
	}
}
