/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.cassandra.config;

import static org.junit.Assert.assertNotNull;

import org.apache.avro.specific.SpecificRecord;

import org.apache.cassandra.CleanupHelper;
import org.apache.cassandra.db.migration.AddKeyspace;
import org.apache.cassandra.locator.RackUnawareStrategy;
import org.apache.cassandra.io.SerDeUtils;
import org.apache.cassandra.io.util.OutputBuffer;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

public class DatabaseDescriptorTest
{
    protected <D extends SpecificRecord> D serDe(D record) throws IOException
    {
        D actual = SerDeUtils.<D>deserialize(record.getSchema(), SerDeUtils.serialize(record));
        assert actual.equals(record) : actual + " != " + record;
        return actual;
    }

    @Test
    public void testShouldHaveConfigFileNameAvailable()
    {
        assertNotNull(DatabaseDescriptor.getConfigFileName(), "DatabaseDescriptor should always be able to return the file name of the config file");
    }

    @Test
    public void testCFMetaDataSerialization() throws IOException, ConfigurationException
    {
        // test serialization of all defined test CFs.
        for (String table : DatabaseDescriptor.getNonSystemTables())
        {
            for (CFMetaData cfm : DatabaseDescriptor.getTableMetaData(table).values())
            {
                CFMetaData cfmDupe = CFMetaData.inflate(serDe(cfm.deflate()));
                assert cfmDupe != null;
                assert cfmDupe.equals(cfm);
            }
        }
    }

    @Test
    public void testKSMetaDataSerialization() throws IOException, ConfigurationException
    {
        for (KSMetaData ksm : DatabaseDescriptor.tables.values())
        {
            KSMetaData ksmDupe = KSMetaData.inflate(serDe(ksm.deflate()));
            assert ksmDupe != null;
            assert ksmDupe.equals(ksm);
        }
    }
    
    // this came as a result of CASSANDRA-995
    @Test
    public void testTransKsMigration() throws IOException, ConfigurationException
    {
        CleanupHelper.cleanupAndLeaveDirs();
        DatabaseDescriptor.loadSchemas();
        assert DatabaseDescriptor.getNonSystemTables().size() == 0;
        
        // add a few.
        AddKeyspace ks0 = new AddKeyspace(new KSMetaData("ks0", RackUnawareStrategy.class, null, 3));
        ks0.apply();
        AddKeyspace ks1 = new AddKeyspace(new KSMetaData("ks1", RackUnawareStrategy.class, null, 3));
        ks1.apply();
        
        assert DatabaseDescriptor.getTableDefinition("ks0") != null;
        assert DatabaseDescriptor.getTableDefinition("ks1") != null;
        
        DatabaseDescriptor.clearTableDefinition(DatabaseDescriptor.getTableDefinition("ks0"), new UUID(4096, 0));
        DatabaseDescriptor.clearTableDefinition(DatabaseDescriptor.getTableDefinition("ks1"), new UUID(4096, 0));
        
        assert DatabaseDescriptor.getTableDefinition("ks0") == null;
        assert DatabaseDescriptor.getTableDefinition("ks1") == null;
        
        DatabaseDescriptor.loadSchemas();
        
        assert DatabaseDescriptor.getTableDefinition("ks0") != null;
        assert DatabaseDescriptor.getTableDefinition("ks1") != null;
    }
}
