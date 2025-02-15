/*

   Derby - Class org.apache.derby.impl.sql.execute.rts.RealSetOpResultSetStatistics

   Copyright 2006 The Apache Software Foundation or its licensors, as applicable.

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

package org.apache.derby.impl.sql.execute.rts;

import org.apache.derby.iapi.services.i18n.MessageService;
import org.apache.derby.iapi.reference.SQLState;

import org.apache.derby.impl.sql.compile.IntersectOrExceptNode;

/**
  ResultSetStatistics implementation for SetOpResultSet.

  @author yip

*/
public class RealSetOpResultSetStatistics 
    extends RealNoPutResultSetStatistics
{
	
    /* Leave these fields public for object inspectors */
    public int opType;
    public int rowsSeenLeft;
    public int rowsSeenRight;
    public int rowsReturned;
    public ResultSetStatistics leftResultSetStatistics;
    public ResultSetStatistics rightResultSetStatistics;

    // CONSTRUCTORS

    /**
     *  Constructs a new <code>RealSetOpResultSetStatistics</code> object 
     *  to represent the runtime statistics for <code>SetOpResultSet</code>.  
     * 
     *  @param   opType                       set operation type 
     *  @param   numOpens                     number of open
     *  @param   rowsSeen                     rows seen
     *  @param   rowsFiltered                 rows filtered
     *  @param   constructorTime              the time for construction
     *  @param   openTime                     the time for open operation
     *  @param   nextTime                     the time for next operation
     *  @param   closeTime                    the time for close operation
     *  @param   resultSetNumber              the result set number
     *  @param   rowsSeenLeft                 rows seen by left source input
     *  @param   rowsSeenRight                rows seen by right source input
     *  @param   rowsReturned                 rows returned
     *  @param   optimizerEstimatedRowCount   optimizer estimated row count
     *  @param   optimizerEstimatedCost       optimizer estimated cost
     *  @param   leftResultSetStatistics      left source runtime statistics
     *  @param   rightResultSetStatistics     right source runtime statistics
     *  
     *  @see     org.apache.derby.impl.sql.execute.SetOpResultSet  
     */
    public	RealSetOpResultSetStatistics(
                                 int opType,
                                 int numOpens,
                                 int rowsSeen,
                                 int rowsFiltered,
                                 long constructorTime,
                                 long openTime,
                                 long nextTime,
                                 long closeTime,
                                 int resultSetNumber,
                                 int rowsSeenLeft,
                                 int rowsSeenRight,
                                 int rowsReturned,
                                 double optimizerEstimatedRowCount,
                                 double optimizerEstimatedCost,
                                 ResultSetStatistics leftResultSetStatistics,
                                 ResultSetStatistics rightResultSetStatistics 
                                )
    {
        super(
            numOpens,
            rowsSeen,
            rowsFiltered,
            constructorTime,
            openTime,
            nextTime,
            closeTime,
            resultSetNumber,
            optimizerEstimatedRowCount,
            optimizerEstimatedCost
            );
		
        this.opType = opType;
        this.rowsSeenLeft = rowsSeenLeft;
        this.rowsSeenRight = rowsSeenRight;
        this.rowsReturned = rowsReturned;
        this.leftResultSetStatistics = leftResultSetStatistics;
        this.rightResultSetStatistics = rightResultSetStatistics;
    }

    // ResultSetStatistics methods

    /**
     * Return the statement execution plan as a <code>String</code>.
     *
     * @param depth	Indentation level.
     *
     * @return the statement execution plan as a <code>String</code>.
     */
    public String getStatementExecutionPlanText(int depth) {
        initFormatInfo(depth);

        String rs = (opType == IntersectOrExceptNode.INTERSECT_OP) 
                ? (SQLState.RTS_INTERSECT_RS) : (SQLState.RTS_EXCEPT_RS);
         
        return
            indent + MessageService.getTextMessage(rs) + 
                ":\n" +
            indent + MessageService.getTextMessage(SQLState.RTS_NUM_OPENS) + 
                " = " + numOpens + "\n" +
            indent + MessageService.getTextMessage(
                                            SQLState.RTS_ROWS_SEEN_LEFT) +
                " = " + rowsSeenLeft + "\n" + 
            indent + MessageService.getTextMessage(
                                            SQLState.RTS_ROWS_SEEN_RIGHT) + 
                " = " + rowsSeenRight + "\n" +
            indent + MessageService.getTextMessage(
                                            SQLState.RTS_ROWS_RETURNED) +
                " = " + rowsReturned + "\n" + 
            dumpTimeStats(indent, subIndent) + "\n" +
            dumpEstimatedCosts(subIndent) + "\n" +
            indent + MessageService.getTextMessage(SQLState.RTS_LEFT_RS) +
                ":\n" + 
            leftResultSetStatistics.getStatementExecutionPlanText(sourceDepth) +
                "\n" + 
            indent + MessageService.getTextMessage(SQLState.RTS_RIGHT_RS) +
                ":\n" + 
            rightResultSetStatistics.getStatementExecutionPlanText(
            		                                              sourceDepth) + 
                "\n";
    }

    /**
     * Return information on the scan nodes from the statement execution 
     * plan as a <code>String</code>.
     *
     * @param depth	Indentation level.
     * @param tableName if not NULL then print information for this table only
     *
     * @return String	The information on the scan nodes from the 
     *					statement execution plan as a <code>String</code>.
     */
    public String getScanStatisticsText(String tableName, int depth) {
        return leftResultSetStatistics.getScanStatisticsText(tableName, depth)
            + rightResultSetStatistics.getScanStatisticsText(tableName, depth);
    }

    // Class implementation

    /**
     * Return the runtime statistics of this object in textual representation
     *
     * @return the runtime statistics of this object in textual representation 
     *         as a <code>String</code>.
     */
    public String toString() {
        return getStatementExecutionPlanText(0);
    }

    /**
     * Retrieves the children runtime statistics of this <code>
     * RealSetOpResultSetStatistics</code> object 
     *
     * @return the children runtime statistics of this <code>
     *         RealSetOpResultSetStatistics</code> object stored in a <code>
     *         Vector</code>. 
     *         
     */
    public java.util.Vector getChildren() {
        java.util.Vector children = new java.util.Vector();
        children.addElement(leftResultSetStatistics);
        children.addElement(rightResultSetStatistics);
        return children;
    }

    /**
     * Format for display, a name for this node.
     *
     * @return the name of the node as a <code>String</code>.
     */
    public String getNodeName() {
        String nodeName = 
            (opType == IntersectOrExceptNode.INTERSECT_OP) 
                ? (SQLState.RTS_INTERSECT) : (SQLState.RTS_EXCEPT);
		
        return MessageService.getTextMessage(nodeName);
    }
} 
