package org.apache.cassandra.hadoop;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.IColumn;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.thrift.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.*;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

/**
 * Hadoop InputFormat allowing map/reduce against Cassandra rows within one ColumnFamily.
 *
 * At minimum, you need to set the CF and predicate (description of columns to extract from each row)
 * in your Hadoop job Configuration.  The ConfigHelper class is provided to make this
 * simple:
 *   ConfigHelper.setColumnFamily
 *   ConfigHelper.setSlicePredicate
 *
 * You can also configure the number of rows per InputSplit with
 *   ConfigHelper.setInputSplitSize
 * This should be "as big as possible, but no bigger."  Each InputSplit is read from Cassandra
 * with multiple get_slice_range queries, and the per-call overhead of get_slice_range is high,
 * so larger split sizes are better -- but if it is too large, you will run out of memory.
 *
 * The default split size is 64k rows.
 */
public class ColumnFamilyInputFormat extends InputFormat<byte[], SortedMap<byte[], IColumn>>
{
    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);
    
    private int splitsize;
    private String keyspace;
    private String cfName;

    private void validateConfiguration(Configuration conf)
    {
        if (ConfigHelper.getKeyspace(conf) == null || ConfigHelper.getColumnFamily(conf) == null)
        {
            throw new UnsupportedOperationException("you must set the keyspace and columnfamily with setColumnFamily()");
        }
        if (ConfigHelper.getSlicePredicate(conf) == null)
        {
            throw new UnsupportedOperationException("you must set the predicate with setPredicate");
        }
    }

    public List<InputSplit> getSplits(JobContext context) throws IOException
    {
        Configuration conf = context.getConfiguration();

        validateConfiguration(conf);

        // cannonical ranges and nodes holding replicas
        List<TokenRange> masterRangeNodes = getRangeMap(ConfigHelper.getKeyspace(conf));

        splitsize = ConfigHelper.getInputSplitSize(context.getConfiguration());
        keyspace = ConfigHelper.getKeyspace(context.getConfiguration());
        cfName = ConfigHelper.getColumnFamily(context.getConfiguration());
        
        // cannonical ranges, split into pieces, fetching the splits in parallel 
        ExecutorService executor = Executors.newCachedThreadPool();
        List<InputSplit> splits = new ArrayList<InputSplit>();

        try
        {
            List<Future<List<InputSplit>>> splitfutures = new ArrayList<Future<List<InputSplit>>>();
            for (TokenRange range : masterRangeNodes)
            {
                // for each range, pick a live owner and ask it to compute bite-sized splits
                splitfutures.add(executor.submit(new SplitCallable(range)));
            }
    
            // wait until we have all the results back
            for (Future<List<InputSplit>> futureInputSplits : splitfutures)
            {
                try
                {
                    splits.addAll(futureInputSplits.get());
                } 
                catch (Exception e)
                {
                    throw new IOException("Could not get input splits", e);
                } 
            }
        } 
        finally
        {
            executor.shutdownNow();
        }

        assert splits.size() > 0;
        Collections.shuffle(splits, new Random(System.nanoTime()));
        return splits;
    }

    /**
     * Gets a token range and splits it up according to the suggested
     * size into input splits that Hadoop can use. 
     */
    class SplitCallable implements Callable<List<InputSplit>>
    {

        private TokenRange range;
        
        public SplitCallable(TokenRange tr)
        {
            this.range = tr;
        }

        @Override
        public List<InputSplit> call() throws Exception
        {
            ArrayList<InputSplit> splits = new ArrayList<InputSplit>();
            List<String> tokens = getSubSplits(keyspace, cfName, range, splitsize);

            // turn the sub-ranges into InputSplits
            String[] endpoints = range.endpoints.toArray(new String[range.endpoints.size()]);
            // hadoop needs hostname, not ip
            for (int i = 0; i < endpoints.length; i++)
            {
                endpoints[i] = InetAddress.getByName(endpoints[i]).getHostName();
            }
            
            for (int i = 1; i < tokens.size(); i++)
            {
                ColumnFamilySplit split = new ColumnFamilySplit(tokens.get(i - 1), tokens.get(i), endpoints);
                logger.debug("adding " + split);
                splits.add(split);
            }
            return splits;
        }
    }

    private List<String> getSubSplits(String keyspace, String cfName, TokenRange range, int splitsize) throws IOException
    {
        // TODO handle failure of range replicas & retry
        TSocket socket = new TSocket(range.endpoints.get(0),
                                     DatabaseDescriptor.getRpcPort());
        TBinaryProtocol binaryProtocol = new TBinaryProtocol(socket, false, false);
        Cassandra.Client client = new Cassandra.Client(binaryProtocol);
        try
        {
            socket.open();
        }
        catch (TTransportException e)
        {
            throw new IOException(e);
        }
        List<String> splits;
        try
        {
            splits = client.describe_splits(keyspace, cfName, range.start_token, range.end_token, splitsize);
        }
        catch (TException e)
        {
            throw new RuntimeException(e);
        }
        return splits;
    }

    private List<TokenRange> getRangeMap(String keyspace) throws IOException
    {
        TSocket socket = new TSocket(DatabaseDescriptor.getSeeds().iterator().next().getHostAddress(),
                                     DatabaseDescriptor.getRpcPort());
        TBinaryProtocol binaryProtocol = new TBinaryProtocol(socket, false, false);
        Cassandra.Client client = new Cassandra.Client(binaryProtocol);
        try
        {
            socket.open();
        }
        catch (TTransportException e)
        {
            throw new IOException(e);
        }
        List<TokenRange> map;
        try
        {
            map = client.describe_ring(keyspace);
        }
        catch (TException e)
        {
            throw new RuntimeException(e);
        }
        catch (InvalidRequestException e)
        {
            throw new RuntimeException(e);
        }
        return map;
    }

    @Override
    public RecordReader<byte[], SortedMap<byte[], IColumn>> createRecordReader(InputSplit inputSplit, TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException
    {
        return new ColumnFamilyRecordReader();
    }
}
