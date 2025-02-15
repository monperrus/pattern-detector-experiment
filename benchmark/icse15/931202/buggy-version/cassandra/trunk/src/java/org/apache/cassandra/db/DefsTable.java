/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.db;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.KSMetaData;
import org.apache.cassandra.db.commitlog.CommitLog;
import org.apache.cassandra.db.filter.QueryFilter;
import org.apache.cassandra.db.filter.QueryPath;
import org.apache.cassandra.db.filter.SliceQueryFilter;
import org.apache.cassandra.db.migration.Migration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class DefsTable
{
    /** dumps current keyspace definitions to storage */
    public static synchronized void dumpToStorage(UUID version) throws IOException
    {
        String versionKey = version.toString();
        long now = System.currentTimeMillis();
        RowMutation rm = new RowMutation(Table.DEFINITIONS, versionKey);
        for (String tableName : DatabaseDescriptor.getNonSystemTables())
        {
            KSMetaData ks = DatabaseDescriptor.getTableDefinition(tableName);
            rm.add(new QueryPath(Migration.SCHEMA_CF, null, ks.name.getBytes()), KSMetaData.serialize(ks), now);
        }
        rm.apply();
    }

    /** loads a version of keyspace definitions from storage */
    public static synchronized Collection<KSMetaData> loadFromStorage(UUID version) throws IOException
    {
        Table defs = Table.open(Table.DEFINITIONS);
        ColumnFamilyStore cfStore = defs.getColumnFamilyStore(Migration.SCHEMA_CF);
        QueryFilter filter = QueryFilter.getSliceFilter(version.toString(), new QueryPath(Migration.SCHEMA_CF), "".getBytes(), "".getBytes(), null, false, 1024);
        ColumnFamily cf = cfStore.getColumnFamily(filter);
        Collection<KSMetaData> tables = new ArrayList<KSMetaData>();
        for (IColumn col : cf.getSortedColumns())
        {
            KSMetaData ks = KSMetaData.deserialize(new ByteArrayInputStream(col.value()));
            tables.add(ks);
        }
        return tables;
    }
    
    /** gets all the files that belong to a given column family. */
    public static Collection<File> getFiles(String table, final String cf)
    {
        List<File> found = new ArrayList<File>();
        for (String path : DatabaseDescriptor.getAllDataFileLocationsForTable(table))
        {
            File[] dbFiles = new File(path).listFiles(new FileFilter()
            {
                public boolean accept(File pathname)
                {
                    return pathname.getName().startsWith(cf + "-") && pathname.getName().endsWith(".db") && pathname.exists();        
                }
            });
            found.addAll(Arrays.asList(dbFiles));
        }
        return found;
    }
    
}
