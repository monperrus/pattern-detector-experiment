/*

   Derby - Class org.apache.derbyTesting.functionTests.harness.procedure

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

package org.apache.derbyTesting.functionTests.tests.storetests;

import org.apache.derby.tools.ij;

import java.sql.*;

/**
 * Repro for DERBY-1939.  In effect what we have to do is execute
 * a query (using a PreparedStatement) for which the optimizer
 * will choose to do a Hash Join using an IndexToBaseRow result
 * result.  But that's not enough--at execution time, we then
 * have to force a situation where the Hash Table "spills" to
 * disk, and only then will the error occur.
 *
 * In order to get the optimizer to choose the necessary plan
 * we have a moderately complex query that has a predicate
 * which can be pushed to table T1.  T1 in turn has an index
 * declared on the appropriate column.  The optimizer will
 * then choose to do a Hash Join between T2 and T1 and
 * will use the index on T1, as desired.
 *
 * Then, in order to force the "spill" to disk, we use the
 * Derby property "maxMemoryPerTable" and set it to a
 * "magic" value that a) is large enough to allow the optimizer
 * to choose a Hash Join, but b) is small enough to cause
 * hash-table-spill-over at execution time.  It took a while
 * find out what value this property should have given the
 * data in the tables, but having found it we can now reliably
 * reproduce the failure.
 */
public class st_derby1939 {

	// We have a VARCHAR column in the table to help with the
	// hash table "spill-over".
	private final int VC_SIZE = 1024;
	private char[] cArr = new char[VC_SIZE];

	public static void main(String [] args)
	{

		try {
            System.setProperty("derby.language.maxMemoryPerTable", "140");
            System.setProperty("derby.optimizer.noTimeout", "true");

            ij.getPropertyArg(args);
            Connection conn = ij.startJBMS();

            st_derby1939 test = new st_derby1939();
            test.doLoad(conn);
            test.doQuery(conn);
            conn.close();
		} catch (Throwable t) {
			System.out.println("OOPS, unexpected error:");
			t.printStackTrace();
		}
	}

	private void doLoad(Connection conn) throws Exception
	{
		conn.setAutoCommit(false);
		Statement st = conn.createStatement();
		try {
			st.execute("drop table d1939_t1");
		} catch (SQLException se) {}
		try {
			st.execute("drop table d1939_t2");
		} catch (SQLException se) {}

		System.out.println("Creating tables and index...");
		st.execute("create table d1939_t1 (i smallint, vc varchar(" + VC_SIZE + "))");
		st.execute("create table d1939_t2 (j smallint, val double, vc varchar(" + VC_SIZE + "))");
		st.execute("create index ix_d1939_t1 on d1939_t1 (i)");

		PreparedStatement pSt = conn.prepareStatement(
			"insert into d1939_t1(i, vc) values (?, ?)");

		PreparedStatement pSt2 = conn.prepareStatement(
			"insert into d1939_t2 values (?, ?, ?)");

		String str = null;
		System.out.println("Doing inserts...");
	
		// Number of rows and columns here is pretty much just "magic";
		// changing any of them can make it so that the problem doesn't
		// reproduce...
		for (int i = 0; i < 69; i++)
		{
			/* In order for the repro to work, the data in the tables
			 * has to be sequential w.r.t the smallint column.  I.e.
			 * instead of inserting "1, 2, 3, 1, 2, 3, ..." we have to
			 * insert "1, 1, 1, 2, 2, 2, ...".  So that's what the
			 * "i % 10" achieves in this code.
			 */
			for (int j = 0; j < 10; j++)
			{
				str = buildString(i + ":" + j);
				pSt.setInt(1, i % 10);
				pSt.setString(2, str);
				pSt.execute();
				pSt2.setInt(1, i % 10);
				pSt2.setDouble(2, j*2.0d);
				if (j % 2 == 1)
					pSt2.setString(3, "shorty-string");
				else
					pSt2.setString(3, str);
				pSt2.execute();
			}

			// Add some extra rows T2, just because.
			pSt2.setInt(1, i);
			pSt2.setDouble(2, i*2.0d);
			pSt2.setNull(3, Types.VARCHAR);
			pSt2.execute();
		}

		pSt2.setNull(1, Types.INTEGER);
		pSt2.setDouble(2, 48.0d);
		pSt.close();
		conn.commit();
	}

	private void doQuery(Connection conn) throws Exception
	{
		/* Set Derby properties to allow the optimizer to find the
		 * best plan (Hash Join with Index) and also to set a max
		 * memory size on the hash table, which makes it possible
		 * to "spill" to disk.
		 */


		conn.setAutoCommit(false);
		PreparedStatement pSt = conn.prepareStatement(
			"select * from d1939_t2 " +
			"  left outer join " +
			"    (select distinct d1939_t1.i, d1939_t2.j, d1939_t1.vc from d1939_t2 " + 
			"      left outer join d1939_t1 " +
			"        on d1939_t2.j = d1939_t1.i " +
			"        and d1939_t1.i = ? " + 
			"    ) x1 " + 
			"  on d1939_t2.j = x1.i");

		System.out.println("Done preparing, about to execute...");
		pSt.setShort(1, (short)8);
		int count = 0;
		try {

			// Will fail on next line without fix for DERBY-1939.
			ResultSet rs = pSt.executeQuery();

			// To iterate through the rows actually takes quite a long time,
			// so just get the first 10 rows as a sanity check.
			for (count = 0; rs.next() && count < 10; count++);
			rs.close();
			System.out.println("-=-> Ran without error, retrieved first "
				 + count + " rows.");

		} catch (SQLException se) {

			if (se.getSQLState().equals("XSDA7"))
			{
				System.out.println("-=-> Reproduced DERBY-1939:\n" +
					" -- " + se.getMessage());
			}
			else
				throw se;

		}

		pSt.close();
		conn.rollback();
	}

	private String buildString(String s) {

		char [] sArr = new char [] { s.charAt(0), s.charAt(1), s.charAt(2) };
		for (int i = 0; i < cArr.length; i++)
			cArr[i] = sArr[i % 3];

		return new String(cArr);
	}
}
