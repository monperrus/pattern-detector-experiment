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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.dht.Token;

/**
 * This Replication Strategy returns the nodes responsible for a given
 * key but respects rack awareness. It places one replica in a
 * different data center from the first (if there is any such data center),
 * the third replica in a different rack in the first datacenter, and
 * any remaining replicas on the first unused nodes on the ring.
 */
public class RackAwareStrategy extends AbstractReplicationStrategy
{
    public RackAwareStrategy(TokenMetadata tokenMetadata, IEndpointSnitch snitch)
    {
        super(tokenMetadata, snitch);
        if (!(snitch instanceof AbstractRackAwareSnitch))
            throw new IllegalArgumentException(("RackAwareStrategy requires AbstractRackAwareSnitch."));
    }

    public Set<InetAddress> calculateNaturalEndpoints(Token token, TokenMetadata metadata, String table)
    {
        int replicas = DatabaseDescriptor.getReplicationFactor(table);
        Set<InetAddress> endpoints = new HashSet<InetAddress>(replicas);
        ArrayList<Token> tokens = metadata.sortedTokens();

        if (tokens.isEmpty())
            return endpoints;

        Iterator<Token> iter = TokenMetadata.ringIterator(tokens, token);
        Token primaryToken = iter.next();
        endpoints.add(metadata.getEndpoint(primaryToken));

        boolean bDataCenter = false;
        boolean bOtherRack = false;
        while (endpoints.size() < replicas && iter.hasNext())
        {
            AbstractRackAwareSnitch snitch = (AbstractRackAwareSnitch) this.snitch;

            // First try to find one in a different data center
            Token t = iter.next();
            if (!snitch.getDatacenter(metadata.getEndpoint(primaryToken)).equals(snitch.getDatacenter(metadata.getEndpoint(t))))
            {
                // If we have already found something in a diff datacenter no need to find another
                if (!bDataCenter)
                {
                    endpoints.add(metadata.getEndpoint(t));
                    bDataCenter = true;
                }
                continue;
            }
            // Now  try to find one on a different rack
            if (!snitch.getRack(metadata.getEndpoint(primaryToken)).equals(snitch.getRack(metadata.getEndpoint(t))) &&
                snitch.getDatacenter(metadata.getEndpoint(primaryToken)).equals(snitch.getDatacenter(metadata.getEndpoint(t))))
            {
                // If we have already found something in a diff rack no need to find another
                if (!bOtherRack)
                {
                    endpoints.add(metadata.getEndpoint(t));
                    bOtherRack = true;
                }
            }

        }

        // If we found N number of nodes we are good. This loop wil just exit. Otherwise just
        // loop through the list and add until we have N nodes.
        if (endpoints.size() < replicas)
        {
            iter = TokenMetadata.ringIterator(tokens, token);
            while (endpoints.size() < replicas && iter.hasNext())
            {
                Token t = iter.next();
                if (!endpoints.contains(metadata.getEndpoint(t)))
                    endpoints.add(metadata.getEndpoint(t));
            }
        }

        return endpoints;
    }
}
