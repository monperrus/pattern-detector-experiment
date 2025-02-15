/*

   Derby - Class org.apache.derby.impl.sql.catalog.SYSCOLPERMSRowFactory

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

package org.apache.derby.impl.sql.catalog;

import org.apache.derby.iapi.sql.dictionary.PermissionsCatalogRowFactory;
import org.apache.derby.iapi.sql.dictionary.ColPermsDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDescriptorGenerator;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.StatisticsDescriptor;
import org.apache.derby.iapi.sql.dictionary.SystemColumn;
import org.apache.derby.iapi.sql.dictionary.TupleDescriptor;
import org.apache.derby.iapi.sql.dictionary.PermissionsDescriptor;

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.services.sanity.SanityManager;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.ExecIndexRow;
import org.apache.derby.iapi.sql.execute.ExecutionFactory;
import org.apache.derby.iapi.types.TypeId;
import org.apache.derby.iapi.types.DataValueFactory;
import org.apache.derby.iapi.types.RowLocation;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.NumberDataValue;
import org.apache.derby.iapi.types.StringDataValue;
import org.apache.derby.iapi.services.uuid.UUIDFactory;
import org.apache.derby.catalog.UUID;
import org.apache.derby.catalog.Statistics;
import org.apache.derby.iapi.services.io.FormatableBitSet;

import java.sql.Timestamp;

/**
 * Factory for creating a SYSCOLPERMS row.
 *
 */

public class SYSCOLPERMSRowFactory extends PermissionsCatalogRowFactory
{
	static final String TABLENAME_STRING = "SYSCOLPERMS";

    // Column numbers for the SYSCOLPERMS table. 1 based
	private static final int COLPERMSID_COL_NUM = 1;
    private static final int GRANTEE_COL_NUM = 2;
    private static final int GRANTOR_COL_NUM = 3;
    private static final int TABLEID_COL_NUM = 4;
    private static final int TYPE_COL_NUM = 5;
    private static final int COLUMNS_COL_NUM = 6;
    private static final int COLUMN_COUNT = 6;

    public static final int GRANTEE_TABLE_TYPE_GRANTOR_INDEX_NUM = 0;
    public static final int COLPERMSID_INDEX_NUM = 1;
    public static final int TABLEID_INDEX_NUM = 2;
	private static final int[][] indexColumnPositions = 
	{ 
		{ GRANTEE_COL_NUM, TABLEID_COL_NUM, TYPE_COL_NUM, GRANTOR_COL_NUM},
		{ COLPERMSID_COL_NUM },
		{ TABLEID_COL_NUM }
	};
	private static final String[][] indexColumnNames =
	{
		{"GRANTEE", "TABLEID", "TYPE", "GRANTOR"},
		{"COLPERMSID"},
		{"TABLEID"}
	};
    private static final boolean[] indexUniqueness = { true, true, false};

    private	static final String[] uuids =
    {
        "286cc01e-0103-0e39-b8e7-00000010f010" // catalog UUID
		,"6074401f-0103-0e39-b8e7-00000010f010"	// heap UUID
		,"787c0020-0103-0e39-b8e7-00000010f010"	// index1
		,"c9a3808d-010c-42a2-ae15-0000000f67f8" //index2
		,"80220011-010c-bc85-060d-000000109ab8" //index3
    };

    private SystemColumn[] columnList;

    public SYSCOLPERMSRowFactory(UUIDFactory uuidf, ExecutionFactory ef, DataValueFactory dvf,
                                 boolean convertIdToLower)
	{
		super(uuidf,ef,dvf,convertIdToLower);
		initInfo(COLUMN_COUNT, TABLENAME_STRING, indexColumnPositions, indexColumnNames, indexUniqueness, uuids);
	}

	public ExecRow makeRow(TupleDescriptor td, TupleDescriptor parent) throws StandardException
	{
        UUID						oid;
        String colPermID = null;
        DataValueDescriptor grantee = null;
        DataValueDescriptor grantor = null;
        String tableID = null;
        String type = null;
        FormatableBitSet columns = null;

        if( td == null)
        {
            grantee = getNullAuthorizationID();
            grantor = getNullAuthorizationID();
        }
        else
        {
            ColPermsDescriptor cpd = (ColPermsDescriptor) td;
            oid = cpd.getUUID();
            if ( oid == null )
            {
            	oid = getUUIDFactory().createUUID();
            	cpd.setUUID(oid);           
            }
            colPermID = oid.toString();
            grantee = getAuthorizationID( cpd.getGrantee());
            grantor = getAuthorizationID( cpd.getGrantor());
            tableID = cpd.getTableUUID().toString();
            type = cpd.getType();
            columns = cpd.getColumns();
        }
        ExecRow row = getExecutionFactory().getValueRow( COLUMN_COUNT);
        row.setColumn( COLPERMSID_COL_NUM, dvf.getCharDataValue(colPermID));
        row.setColumn( GRANTEE_COL_NUM, grantee);
        row.setColumn( GRANTOR_COL_NUM, grantor);
        row.setColumn( TABLEID_COL_NUM, dvf.getCharDataValue( tableID));
        row.setColumn( TYPE_COL_NUM, dvf.getCharDataValue( type));
        row.setColumn( COLUMNS_COL_NUM, dvf.getDataValue( (Object) columns));
        return row;
    } // end of makeRow

	/** builds a tuple descriptor from a row */
	public TupleDescriptor buildDescriptor(ExecRow row,
                                           TupleDescriptor parentTuple,
                                           DataDictionary	dataDictionary)
		throws StandardException
    {
        if( SanityManager.DEBUG)
            SanityManager.ASSERT( row.nColumns() == COLUMN_COUNT,
                                  "Wrong size row passed to SYSCOLPERMSRowFactory.buildDescriptor");

        String colPermsUUIDString = row.getColumn( COLPERMSID_COL_NUM).getString();
        UUID colPermsUUID = getUUIDFactory().recreateUUID(colPermsUUIDString);
        String tableUUIDString = row.getColumn( TABLEID_COL_NUM).getString();
        UUID tableUUID = getUUIDFactory().recreateUUID(tableUUIDString);
        String type = row.getColumn( TYPE_COL_NUM).getString();
        FormatableBitSet columns = (FormatableBitSet) row.getColumn( COLUMNS_COL_NUM).getObject();
        if( SanityManager.DEBUG)
            SanityManager.ASSERT( "s".equals( type) || "S".equals( type) ||
                                  "u".equals( type) || "U".equals( type) ||
                                  "r".equals( type) || "R".equals( type),
                                  "Invalid type passed to SYSCOLPERMSRowFactory.buildDescriptor");

        ColPermsDescriptor colPermsDesc =
	        new ColPermsDescriptor( dataDictionary, 
                    getAuthorizationID( row, GRANTEE_COL_NUM),
                    getAuthorizationID( row, GRANTOR_COL_NUM),
                    tableUUID, type, columns);
        colPermsDesc.setUUID(colPermsUUID);
        return colPermsDesc;
    } // end of buildDescriptor

	/** builds a column list for the catalog */
	public SystemColumn[] buildColumnList()
    {
		if (columnList == null)
        {
            columnList = new SystemColumn[ COLUMN_COUNT];

            columnList[ COLPERMSID_COL_NUM - 1] =
                new SystemColumnImpl( convertIdCase( "COLPERMSID"),
                                      COLPERMSID_COL_NUM,
                                      0, // precision
                                      0, // scale
                                      false, // nullability
                                      "CHAR",
                                      true,
                                      36);
            columnList[ GRANTEE_COL_NUM - 1] =
              new SystemColumnImpl( convertIdCase( "GRANTEE"),
                                    GRANTEE_COL_NUM,
                                    0, // precision
                                    0, // scale
                                    false, // nullability
                                    AUTHORIZATION_ID_TYPE,
                                    AUTHORIZATION_ID_IS_BUILTIN_TYPE,
                                    AUTHORIZATION_ID_LENGTH);
            columnList[ GRANTOR_COL_NUM - 1] =
              new SystemColumnImpl( convertIdCase( "GRANTOR"),
                                    GRANTOR_COL_NUM,
                                    0, // precision
                                    0, // scale
                                    false, // nullability
                                    AUTHORIZATION_ID_TYPE,
                                    AUTHORIZATION_ID_IS_BUILTIN_TYPE,
                                    AUTHORIZATION_ID_LENGTH);
            columnList[ TABLEID_COL_NUM - 1] =
              new SystemColumnImpl( convertIdCase( "TABLEID"),
                                    TABLEID_COL_NUM,
                                    0, // precision
                                    0, // scale
                                    false, // nullability
                                    "CHAR", // dataType
                                    true, // built-in type
                                    36);
            columnList[ TYPE_COL_NUM - 1] =
              new SystemColumnImpl( convertIdCase( "TYPE"),
                                    TYPE_COL_NUM,
                                    0, // precision
                                    0, // scale
                                    false, // nullability
                                    "CHAR", // dataType
                                    true, // built-in type
                                    1);
            columnList[ COLUMNS_COL_NUM - 1] =
              new SystemColumnImpl( convertIdCase( "COLUMNS"),
                                    COLUMNS_COL_NUM,
                                    0, // precision
                                    0, // scale
                                    false, // nullability
                                    "org.apache.derby.iapi.services.io.FormatableBitSet", // datatype
                                    false,							// built-in type
                                    DataTypeDescriptor.MAXIMUM_WIDTH_UNKNOWN // maxLength
                  );
        }
		return columnList;
    } // end of buildColumnList

	/**
	 * builds an empty row given for a given index number.
	 */
  	public ExecIndexRow buildEmptyIndexRow(int indexNumber,
                                           RowLocation rowLocation) 
  		throws StandardException
    {
        ExecIndexRow row = getExecutionFactory().getIndexableRow( indexColumnPositions[indexNumber].length + 1);
        row.setColumn( row.nColumns(), rowLocation);
        
        switch( indexNumber)
        {
        case GRANTEE_TABLE_TYPE_GRANTOR_INDEX_NUM:
            row.setColumn(1, getNullAuthorizationID()); // grantee
            row.setColumn(2, getDataValueFactory().getNullChar( (StringDataValue) null)); // table UUID
            row.setColumn(3, getDataValueFactory().getNullChar( (StringDataValue) null)); // type
            row.setColumn(4, getNullAuthorizationID()); // grantor
            break;
        case COLPERMSID_INDEX_NUM:
            row.setColumn(1, getDataValueFactory().getNullChar( (StringDataValue) null)); // COLPERMSID
            break;
        case TABLEID_INDEX_NUM:
            row.setColumn(1, getDataValueFactory().getNullChar( (StringDataValue) null)); // TABLEID
            break;
        }
        return row;
    } // end of buildEmptyIndexRow

	/**
	 * builds an index key row for a given index number.
	 */
  	public ExecIndexRow buildIndexKeyRow( int indexNumber,
                                          PermissionsDescriptor perm) 
  		throws StandardException
    {
        ExecIndexRow row = null;
        
        switch( indexNumber)
        {
        case GRANTEE_TABLE_TYPE_GRANTOR_INDEX_NUM:
            // RESOLVE We do not support the FOR GRANT OPTION, so column permission rows are unique on the
            // grantee, table UUID, and type columns. The grantor column will always have the name of the owner of the
            // table. So the index key, used for searching the index, only has grantee, table UUID, and type columns.
            // It does not have a grantor column.
            //
            // If we support FOR GRANT OPTION then there may be multiple table permissions rows for a
            // (grantee, tableID, type) combination. We must either handle the multiple rows, which is necessary for
            // checking permissions, or add a grantor column to the key, which is necessary for granting or revoking
            // permissions.
            row = getExecutionFactory().getIndexableRow( 3);
            row.setColumn(1, getAuthorizationID( perm.getGrantee()));
            ColPermsDescriptor colPerms = (ColPermsDescriptor) perm;
            String tableUUIDStr = colPerms.getTableUUID().toString();
            row.setColumn(2, getDataValueFactory().getCharDataValue( tableUUIDStr));
            row.setColumn(3, getDataValueFactory().getCharDataValue( colPerms.getType()));
            break;
        case COLPERMSID_INDEX_NUM:
            row = getExecutionFactory().getIndexableRow( 1);
            String colPermsUUIDStr = perm.getObjectID().toString();
            row.setColumn(1, getDataValueFactory().getCharDataValue( colPermsUUIDStr));
            break;
        case TABLEID_INDEX_NUM:
            row = getExecutionFactory().getIndexableRow( 1);
            colPerms = (ColPermsDescriptor) perm;
            tableUUIDStr = colPerms.getTableUUID().toString();
            row.setColumn(1, getDataValueFactory().getCharDataValue( tableUUIDStr));
            break;
        }
        return row;
    } // end of buildIndexKeyRow
    
    public int getPrimaryIndexNumber()
    {
        return GRANTEE_TABLE_TYPE_GRANTOR_INDEX_NUM;
    }

    /**
     * Or a set of permissions in with a row from this catalog table
     *
     * @param row an existing row
     * @param perm a permission descriptor of the appropriate class for this PermissionsCatalogRowFactory class.
     * @param colsChanged An array with one element for each column in row. It is updated to
     *                    indicate which columns in row were changed
     *
     * @return The number of columns that were changed.
     *
     * @exception StandardException standard error policy
     */
    public int orPermissions( ExecRow row, PermissionsDescriptor perm, boolean[] colsChanged)
        throws StandardException
    {
        ColPermsDescriptor colPerms = (ColPermsDescriptor) perm;
        FormatableBitSet existingColSet = (FormatableBitSet) row.getColumn( COLUMNS_COL_NUM).getObject();
        FormatableBitSet newColSet = colPerms.getColumns();

        boolean changed = false;
        for( int i = newColSet.anySetBit(); i >= 0; i = newColSet.anySetBit(i))
        {
            if( ! existingColSet.get(i))
            {
                existingColSet.set( i);
                changed = true;
            }
        }
        if( changed)
        {
            colsChanged[ COLUMNS_COL_NUM - 1] = true;
            return 1;
        }
        return 0;
    } // end of orPermissions

    /**
     * Remove a set of permissions from a row from this catalog table
     *
     * @param row an existing row
     * @param perm a permission descriptor of the appropriate class for this PermissionsCatalogRowFactory class.
     * @param colsChanged An array with one element for each column in row. It is updated to
     *                    indicate which columns in row were changed
     *
     * @return -1 if there are no permissions left in the row, otherwise the number of columns that were changed.
     *
     * @exception StandardException standard error policy
     */
    public int removePermissions( ExecRow row, PermissionsDescriptor perm, boolean[] colsChanged)
        throws StandardException
    {
        ColPermsDescriptor colPerms = (ColPermsDescriptor) perm;
        FormatableBitSet removeColSet = colPerms.getColumns();
        if( removeColSet == null)
            // remove all of them
            return -1;
        
        FormatableBitSet existingColSet = (FormatableBitSet) row.getColumn( COLUMNS_COL_NUM).getObject();

        boolean changed = false;
        for( int i = removeColSet.anySetBit(); i >= 0; i = removeColSet.anySetBit(i))
        {
            if( existingColSet.get(i))
            {
                existingColSet.clear( i);
                changed = true;
            }
        }
        if( changed)
        {
            colsChanged[ COLUMNS_COL_NUM - 1] = true;
            if( existingColSet.anySetBit() < 0)
                return -1; // No column privileges left
            return 1; // A change, but there are some privileges left
        }
        return 0; // no change
    } // end of removePermissions
    
	/** 
	 * @see PermissionsCatalogRowFactory#setUUIDOfThePassedDescriptor
	 */
    public void setUUIDOfThePassedDescriptor(ExecRow row, PermissionsDescriptor perm)
    throws StandardException
    {
        DataValueDescriptor existingPermDVD = row.getColumn(COLPERMSID_COL_NUM);
        perm.setUUID(getUUIDFactory().recreateUUID(existingPermDVD.getString()));
    }
}
