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
package org.apache.cassandra.locator;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;
import org.apache.cassandra.SchemaLoader;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.dht.*;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.service.StorageServiceAccessor;

public class RackUnawareStrategyTest extends SchemaLoader
{
    @Test
    public void tryBogusTable()
    {
        AbstractReplicationStrategy rs = StorageService.instance.getReplicationStrategy("Keyspace1");
        assertNotNull(rs);
        try
        {
            rs = StorageService.instance.getReplicationStrategy("SomeBogusTableThatDoesntExist");
            throw new AssertionError("SS.getReplicationStrategy() should have thrown a RuntimeException.");
        }
        catch (RuntimeException ex)
        {
            // This exception should be thrown.
        }
    }

    @Test
    public void testBigIntegerEndpoints() throws UnknownHostException
    {
        TokenMetadata tmd = new TokenMetadata();
        AbstractReplicationStrategy strategy = new RackUnawareStrategy(tmd, null);

        List<Token> endpointTokens = new ArrayList<Token>();
        List<Token> keyTokens = new ArrayList<Token>();
        for (int i = 0; i < 5; i++) {
            endpointTokens.add(new BigIntegerToken(String.valueOf(10 * i)));
            keyTokens.add(new BigIntegerToken(String.valueOf(10 * i + 5)));
        }
        verifyGetNaturalEndpoints(tmd, strategy, endpointTokens.toArray(new Token[0]), keyTokens.toArray(new Token[0]));
    }

    @Test
    public void testStringEndpoints() throws UnknownHostException
    {
        TokenMetadata tmd = new TokenMetadata();
        IPartitioner partitioner = new OrderPreservingPartitioner();
        AbstractReplicationStrategy strategy = new RackUnawareStrategy(tmd, null);

        List<Token> endpointTokens = new ArrayList<Token>();
        List<Token> keyTokens = new ArrayList<Token>();
        for (int i = 0; i < 5; i++) {
            endpointTokens.add(new StringToken(String.valueOf((char)('a' + i * 2))));
            keyTokens.add(partitioner.getToken(String.valueOf((char)('a' + i * 2 + 1)).getBytes()));
        }
        verifyGetNaturalEndpoints(tmd, strategy, endpointTokens.toArray(new Token[0]), keyTokens.toArray(new Token[0]));
    }

    // given a list of endpoint tokens, and a set of key tokens falling between the endpoint tokens,
    // make sure that the Strategy picks the right endpoints for the keys.
    private void verifyGetNaturalEndpoints(TokenMetadata tmd, AbstractReplicationStrategy strategy, Token[] endpointTokens, Token[] keyTokens) throws UnknownHostException
    {
        for (String table : DatabaseDescriptor.getNonSystemTables())
        {
            List<InetAddress> hosts = new ArrayList<InetAddress>();
            for (int i = 0; i < endpointTokens.length; i++)
            {
                InetAddress ep = InetAddress.getByName("127.0.0." + String.valueOf(i + 1));
                tmd.updateNormalToken(endpointTokens[i], ep);
                hosts.add(ep);
            }

            for (int i = 0; i < keyTokens.length; i++)
            {
                List<InetAddress> endpoints = strategy.getNaturalEndpoints(keyTokens[i], table);
                assertEquals(DatabaseDescriptor.getReplicationFactor(table), endpoints.size());
                List<InetAddress> correctEndpoints = new ArrayList<InetAddress>();
                for (int j = 0; j < endpoints.size(); j++)
                    correctEndpoints.add(hosts.get((i + j + 1) % hosts.size()));
                assertEquals(new HashSet<InetAddress>(correctEndpoints), new HashSet<InetAddress>(endpoints));
            }
        }
    }
    
    @Test
    public void testGetEndpointsDuringBootstrap() throws UnknownHostException
    {
        // the token difference will be RING_SIZE * 2.
        final int RING_SIZE = 10;
        TokenMetadata tmd = new TokenMetadata();
        TokenMetadata oldTmd = StorageServiceAccessor.setTokenMetadata(tmd);
        AbstractReplicationStrategy strategy = new RackUnawareStrategy(tmd, null);

        Token[] endpointTokens = new Token[RING_SIZE];
        Token[] keyTokens = new Token[RING_SIZE];
        
        for (int i = 0; i < RING_SIZE; i++)
        {
            endpointTokens[i] = new BigIntegerToken(String.valueOf(RING_SIZE * 2 * i));
            keyTokens[i] = new BigIntegerToken(String.valueOf(RING_SIZE * 2 * i + RING_SIZE));
        }
        
        List<InetAddress> hosts = new ArrayList<InetAddress>();
        for (int i = 0; i < endpointTokens.length; i++)
        {
            InetAddress ep = InetAddress.getByName("127.0.0." + String.valueOf(i + 1));
            tmd.updateNormalToken(endpointTokens[i], ep);
            hosts.add(ep);
        }

        // bootstrap at the end of the ring
        Token bsToken = new BigIntegerToken(String.valueOf(210));
        InetAddress bootstrapEndpoint = InetAddress.getByName("127.0.0.11");
        tmd.addBootstrapToken(bsToken, bootstrapEndpoint);

        for (String table : DatabaseDescriptor.getNonSystemTables())
        {
            StorageService.calculatePendingRanges(strategy, table);
            int replicationFactor = DatabaseDescriptor.getReplicationFactor(table);

            for (int i = 0; i < keyTokens.length; i++)
            {
                Collection<InetAddress> endpoints = strategy.getWriteEndpoints(keyTokens[i], table, strategy.getNaturalEndpoints(keyTokens[i], table));
                assertTrue(endpoints.size() >= replicationFactor);

                for (int j = 0; j < replicationFactor; j++)
                {
                    //Check that the old nodes are definitely included
                    assertTrue(endpoints.contains(hosts.get((i + j + 1) % hosts.size())));
                }

                // bootstrapEndpoint should be in the endpoints for i in MAX-RF to MAX, but not in any earlier ep.
                if (i < RING_SIZE - replicationFactor)
                    assertFalse(endpoints.contains(bootstrapEndpoint));
                else
                    assertTrue(endpoints.contains(bootstrapEndpoint));
            }
        }

        StorageServiceAccessor.setTokenMetadata(oldTmd);
    }
}
