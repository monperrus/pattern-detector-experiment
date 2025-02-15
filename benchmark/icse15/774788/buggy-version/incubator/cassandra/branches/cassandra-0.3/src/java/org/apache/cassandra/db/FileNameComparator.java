package org.apache.cassandra.db;

import java.util.Comparator;

class FileNameComparator implements Comparator<String>
{
	private int order_ = 1 ;
	
	public static final int Ascending = 0 ;
	public static final int Descending = 1 ;
	
	FileNameComparator( int order )
	{
		order_ = order;
	}
	
    public int compare(String f, String f2)
    {
    	if( order_ == 1 )
    		return ColumnFamilyStore.getIndexFromFileName(f2) - ColumnFamilyStore.getIndexFromFileName(f);
    	else
    		return ColumnFamilyStore.getIndexFromFileName(f) - ColumnFamilyStore.getIndexFromFileName(f2);
    }
}
