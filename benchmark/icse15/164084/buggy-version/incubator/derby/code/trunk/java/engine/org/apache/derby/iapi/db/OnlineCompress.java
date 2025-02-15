/*

   Derby - Class org.apache.derby.iapi.db.OnlineCompress

   Copyright 2005 The Apache Software Foundation or its licensors, as applicable.

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

package org.apache.derby.iapi.db;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.error.PublicAPI;

import org.apache.derby.iapi.sql.dictionary.DataDictionaryContext;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptorList;
import org.apache.derby.iapi.sql.dictionary.ConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.ConstraintDescriptorList;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;

import org.apache.derby.iapi.sql.depend.DependencyManager;

import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.ExecutionContext;

import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.DataValueFactory;


import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.conn.ConnectionUtil;

import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.iapi.types.RowLocation;
import org.apache.derby.iapi.store.access.ScanController;
import org.apache.derby.iapi.store.access.ConglomerateController;
import org.apache.derby.iapi.store.access.GroupFetchScanController;
import org.apache.derby.iapi.store.access.RowUtil;
import org.apache.derby.iapi.store.access.Qualifier;

import org.apache.derby.iapi.services.sanity.SanityManager;

import org.apache.derby.iapi.reference.SQLState;

import org.apache.derby.iapi.services.io.FormatableBitSet;

import java.sql.SQLException;

public class OnlineCompress
{

	/** no requirement for a constructor */
	private OnlineCompress() {
	}
	public static void compressTable(
    String  schemaName, 
    String  tableName,
    boolean purgeRows,
    boolean defragmentRows,
    boolean truncateEnd)
        throws SQLException
	{
		LanguageConnectionContext lcc       = ConnectionUtil.getCurrentLCC();
		TransactionController     tc        = lcc.getTransactionExecute();

		try 
        {
            DataDictionary data_dictionary = lcc.getDataDictionary();

            // Each of the following may give up locks allowing ddl on the
            // table, so each phase needs to do the data dictionary lookup.

            if (purgeRows)
                purgeRows(schemaName, tableName, data_dictionary, tc);

            if (defragmentRows)
                defragmentRows(schemaName, tableName, data_dictionary, tc);

            if (truncateEnd)
                truncateEnd(schemaName, tableName, data_dictionary, tc);
        }
		catch (StandardException se)
		{
			throw PublicAPI.wrapStandardException(se);
		}

	}

	private static void defragmentRows(
    String                  schemaName, 
    String                  tableName,
    DataDictionary          data_dictionary,
    TransactionController   tc)
        throws SQLException
	{
        GroupFetchScanController base_group_fetch_cc = null;
        int                      num_indexes         = 0;

        int[][]                  index_col_map       =  null;
        ScanController[]         index_scan          =  null;
        ConglomerateController[] index_cc            =  null;
        DataValueDescriptor[][]  index_row           =  null;

		LanguageConnectionContext lcc       = ConnectionUtil.getCurrentLCC();
		TransactionController     nested_tc = null;

		try {

            SchemaDescriptor sd = 
                data_dictionary.getSchemaDescriptor(
                    schemaName, nested_tc, true);
            TableDescriptor td = 
                data_dictionary.getTableDescriptor(tableName, sd);
            nested_tc = 
                tc.startNestedUserTransaction(false);

            if (td == null)
            {
                throw StandardException.newException(
                    SQLState.LANG_TABLE_NOT_FOUND, 
                    schemaName + "." + tableName);
            }

            /* Skip views */
            if (td.getTableType() == TableDescriptor.VIEW_TYPE)
            {
                return;
            }


			ConglomerateDescriptor heapCD = 
                td.getConglomerateDescriptor(td.getHeapConglomerateId());

			/* Get a row template for the base table */
			ExecRow baseRow = 
                lcc.getExecutionContext().getExecutionFactory().getValueRow(
                    td.getNumberOfColumns());


			/* Fill the row with nulls of the correct type */
			ColumnDescriptorList cdl = td.getColumnDescriptorList();
			int					 cdlSize = cdl.size();

			for (int index = 0; index < cdlSize; index++)
			{
				ColumnDescriptor cd = (ColumnDescriptor) cdl.elementAt(index);
				baseRow.setColumn(cd.getPosition(), cd.getType().getNull());
			}

            DataValueDescriptor[][] row_array = new DataValueDescriptor[100][];
            row_array[0] = baseRow.getRowArray();
            RowLocation[] old_row_location_array = new RowLocation[100];
            RowLocation[] new_row_location_array = new RowLocation[100];

            // Create the following 3 arrays which will be used to update
            // each index as the scan moves rows about the heap as part of
            // the compress:
            //     index_col_map - map location of index cols in the base row, 
            //                     ie. index_col_map[0] is column offset of 1st
            //                     key collumn in base row.  All offsets are 0 
            //                     based.
            //     index_scan - open ScanController used to delete old index row
            //     index_cc   - open ConglomerateController used to insert new 
            //                  row

            ConglomerateDescriptor[] conglom_descriptors = 
                td.getConglomerateDescriptors();

            // conglom_descriptors has an entry for the conglomerate and each 
            // one of it's indexes.
            num_indexes = conglom_descriptors.length - 1;

            // if indexes exist, set up data structures to update them
            if (num_indexes > 0)
            {
                // allocate arrays
                index_col_map   = new int[num_indexes][];
                index_scan      = new ScanController[num_indexes];
                index_cc        = new ConglomerateController[num_indexes];
                index_row       = new DataValueDescriptor[num_indexes][];

                setup_indexes(
                    nested_tc,
                    td,
                    index_col_map,
                    index_scan,
                    index_cc,
                    index_row);

            }

			/* Open the heap for reading */
			base_group_fetch_cc = 
                nested_tc.defragmentConglomerate(
                    td.getHeapConglomerateId(), 
                    false,
                    true, 
                    TransactionController.OPENMODE_FORUPDATE, 
				    TransactionController.MODE_TABLE,
					TransactionController.ISOLATION_SERIALIZABLE);

            int num_rows_fetched = 0;
            while ((num_rows_fetched = 
                        base_group_fetch_cc.fetchNextGroup(
                            row_array, 
                            old_row_location_array, 
                            new_row_location_array)) != 0)
            {
                if (num_indexes > 0)
                {
                    for (int row = 0; row < num_rows_fetched; row++)
                    {
                        for (int index = 0; index < num_indexes; index++)
                        {
                            fixIndex(
                                row_array[row],
                                index_row[index],
                                old_row_location_array[row],
                                new_row_location_array[row],
                                index_cc[index],
                                index_scan[index],
                                index_col_map[index]);
                        }
                    }
                }
            }
			
		}
		catch (StandardException se)
		{
			throw PublicAPI.wrapStandardException(se);
		}
		finally
		{
            try
            {
                /* Clean up before we leave */
                if (base_group_fetch_cc != null)
                {
                    base_group_fetch_cc.close();
                    base_group_fetch_cc = null;
                }

                if (num_indexes > 0)
                {
                    for (int i = 0; i < num_indexes; i++)
                    {
                        if (index_scan != null && index_scan[i] != null)
                        {
                            index_scan[i].close();
                            index_scan[i] = null;
                        }
                        if (index_cc != null && index_cc[i] != null)
                        {
                            index_cc[i].close();
                            index_cc[i] = null;
                        }
                    }
                }

                if (nested_tc != null)
                {
                    nested_tc.destroy();
                }

            }
            catch (StandardException se)
            {
                throw PublicAPI.wrapStandardException(se);
            }
		}

		return;
	}

	private static void purgeRows(
    String                  schemaName, 
    String                  tableName,
    DataDictionary          data_dictionary,
    TransactionController   tc)
        throws StandardException
	{
        SchemaDescriptor sd = 
            data_dictionary.getSchemaDescriptor(schemaName, tc, true);
        TableDescriptor  td = 
            data_dictionary.getTableDescriptor(tableName, sd);

        if (td == null)
        {
            throw StandardException.newException(
                SQLState.LANG_TABLE_NOT_FOUND, 
                schemaName + "." + tableName);
        }

        /* Skip views */
        if (td.getTableType() != TableDescriptor.VIEW_TYPE)
        {

            ConglomerateDescriptor[] conglom_descriptors = 
                td.getConglomerateDescriptors();

            for (int cd_idx = 0; cd_idx < conglom_descriptors.length; cd_idx++)
            {
                ConglomerateDescriptor cd = conglom_descriptors[cd_idx];

                tc.purgeConglomerate(cd.getConglomerateNumber());
            }
        }

        return;
    }

	private static void truncateEnd(
    String                  schemaName, 
    String                  tableName,
    DataDictionary          data_dictionary,
    TransactionController   tc)
        throws StandardException
	{
        SchemaDescriptor sd = 
            data_dictionary.getSchemaDescriptor(schemaName, tc, true);
        TableDescriptor  td = 
            data_dictionary.getTableDescriptor(tableName, sd);

        if (td == null)
        {
            throw StandardException.newException(
                SQLState.LANG_TABLE_NOT_FOUND, 
                schemaName + "." + tableName);
        }

        /* Skip views */
        if (td.getTableType() != TableDescriptor.VIEW_TYPE)
        {
            ConglomerateDescriptor[] conglom_descriptors = 
                td.getConglomerateDescriptors();

            for (int cd_idx = 0; cd_idx < conglom_descriptors.length; cd_idx++)
            {
                ConglomerateDescriptor cd = conglom_descriptors[cd_idx];

                tc.compressConglomerate(cd.getConglomerateNumber());
            }
        }

        return;
    }

    private static void setup_indexes(
    TransactionController       tc,
    TableDescriptor             td,
    int[][]                     index_col_map,
    ScanController[]            index_scan,
    ConglomerateController[]    index_cc,
    DataValueDescriptor[][]     index_row)
		throws StandardException
    {

        // Initialize the following 3 arrays which will be used to update
        // each index as the scan moves rows about the heap as part of
        // the compress:
        //     index_col_map - map location of index cols in the base row, ie.
        //                     index_col_map[0] is column offset of 1st key
        //                     collumn in base row.  All offsets are 0 based.
        //     index_scan - open ScanController used to delete old index row
        //     index_cc   - open ConglomerateController used to insert new row

        ConglomerateDescriptor[] conglom_descriptors =
                td.getConglomerateDescriptors();


        int index_idx = 0;
        for (int cd_idx = 0; cd_idx < conglom_descriptors.length; cd_idx++)
        {
            ConglomerateDescriptor index_cd = conglom_descriptors[cd_idx];

            if (!index_cd.isIndex())
            {
                // skip the heap descriptor entry
                continue;
            }

            // ScanControllers are used to delete old index row
            index_scan[index_idx] = 
                tc.openScan(
                    index_cd.getConglomerateNumber(),
                    true,	// hold
                    TransactionController.OPENMODE_FORUPDATE,
                    TransactionController.MODE_TABLE,
                    TransactionController.ISOLATION_SERIALIZABLE,
                    null,   // full row is retrieved, 
                            // so that full row can be used for start/stop keys
                    null,	// startKeyValue - will be reset with reopenScan()
                    0,		// 
                    null,	// qualifier
                    null,	// stopKeyValue  - will be reset with reopenScan()
                    0);		// 

            // ConglomerateControllers are used to insert new index row
            index_cc[index_idx] = 
                tc.openConglomerate(
                    index_cd.getConglomerateNumber(),
                    true,  // hold
                    TransactionController.OPENMODE_FORUPDATE,
                    TransactionController.MODE_TABLE,
                    TransactionController.ISOLATION_SERIALIZABLE);

            // build column map to allow index row to be built from base row
            int[] baseColumnPositions   = 
                index_cd.getIndexDescriptor().baseColumnPositions();
            int[] zero_based_map        = 
                new int[baseColumnPositions.length];

            for (int i = 0; i < baseColumnPositions.length; i++)
            {
                zero_based_map[i] = baseColumnPositions[i] - 1; 
            }

            index_col_map[index_idx] = zero_based_map;

            // build row array to delete from index and insert into index
            //     length is length of column map + 1 for RowLocation.
            index_row[index_idx] = 
                new DataValueDescriptor[baseColumnPositions.length + 1];

            index_idx++;
        }

        return;
    }


    /**
     * Delete old index row and insert new index row in input index.
     * <p>
     *
	 * @return The identifier to be used to open the conglomerate later.
     *
     * @param param1 param1 does this.
     * @param param2 param2 does this.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    private static void fixIndex(
    DataValueDescriptor[]   base_row,
    DataValueDescriptor[]   index_row,
    RowLocation             old_row_loc,
    RowLocation             new_row_loc,
    ConglomerateController  index_cc,
    ScanController          index_scan,
	int[]					index_col_map)
        throws StandardException
    {
        if (SanityManager.DEBUG)
        {
            // baseColumnPositions should describe all columns in index row
            // except for the final column, which is the RowLocation.
            SanityManager.ASSERT(index_col_map != null);
            SanityManager.ASSERT(index_row != null);
            SanityManager.ASSERT(
                (index_col_map.length == (index_row.length - 1)));
        }

        // create the index row to delete from from the base row, using map
        for (int index = 0; index < index_col_map.length; index++)
        {
            index_row[index] = base_row[index_col_map[index]];
        }
        // last column in index in the RowLocation
        index_row[index_row.length - 1] = old_row_loc;

        SanityManager.DEBUG_PRINT("OnlineCompress", "row before delete = " +
                RowUtil.toString(index_row));

        // position the scan for the delete, the scan should already be open.
        // This is done by setting start scan to full key, GE and stop scan
        // to full key, GT.
        index_scan.reopenScan(
            index_row,
            ScanController.GE,
            (Qualifier[][]) null,
            index_row,
            ScanController.GT);

        // position the scan, serious problem if scan does not find the row.
        if (index_scan.next())
        {
            index_scan.delete();
        }
        else
        {
            // Didn't find the row we wanted to delete.
            if (SanityManager.DEBUG)
            {
                SanityManager.THROWASSERT(
                    "Did not find row to delete." +
                    "base_row = " + RowUtil.toString(base_row) +
                    "index_row = " + RowUtil.toString(index_row));
            }
        }

        // insert the new index row into the conglomerate
        index_row[index_row.length - 1] = new_row_loc;

        SanityManager.DEBUG_PRINT("OnlineCompress", "row before insert = " +
                RowUtil.toString(index_row));
        index_cc.insert(index_row);

        return;
    }
}
