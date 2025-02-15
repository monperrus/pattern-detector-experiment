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

package org.apache.cassandra.thrift;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.concurrent.Stage;
import org.apache.cassandra.concurrent.StageManager;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.KSMetaData;
import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.ColumnFamilyType;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.ExpiringColumn;
import org.apache.cassandra.db.IColumn;
import org.apache.cassandra.db.RangeSliceCommand;
import org.apache.cassandra.db.ReadCommand;
import org.apache.cassandra.db.Row;
import org.apache.cassandra.db.RowMutation;
import org.apache.cassandra.db.SliceByNamesReadCommand;
import org.apache.cassandra.db.SliceFromReadCommand;
import org.apache.cassandra.db.filter.QueryPath;
import org.apache.cassandra.db.marshal.MarshalException;
import org.apache.cassandra.db.migration.AddColumnFamily;
import org.apache.cassandra.db.migration.AddKeyspace;
import org.apache.cassandra.db.migration.DropColumnFamily;
import org.apache.cassandra.db.migration.DropKeyspace;
import org.apache.cassandra.db.migration.Migration;
import org.apache.cassandra.db.migration.RenameColumnFamily;
import org.apache.cassandra.db.migration.RenameKeyspace;
import org.apache.cassandra.db.migration.UpdateColumnFamily;
import org.apache.cassandra.db.migration.UpdateKeyspace;
import org.apache.cassandra.dht.AbstractBounds;
import org.apache.cassandra.dht.Bounds;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.locator.AbstractReplicationStrategy;
import org.apache.cassandra.locator.DynamicEndpointSnitch;
import org.apache.cassandra.scheduler.IRequestScheduler;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.StorageProxy;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraServer implements Cassandra.Iface
{
    private static Logger logger = LoggerFactory.getLogger(CassandraServer.class);

    private final static List<ColumnOrSuperColumn> EMPTY_COLUMNS = Collections.emptyList();
    private final static List<Column> EMPTY_SUBCOLUMNS = Collections.emptyList();

    // thread local state containing session information
    public final ThreadLocal<ClientState> clientState = new ThreadLocal<ClientState>()
    {
        @Override
        public ClientState initialValue()
        {
            return new ClientState();
        }
    };

    /*
     * RequestScheduler to perform the scheduling of incoming requests
     */
    private final IRequestScheduler requestScheduler;

    public CassandraServer()
    {
        requestScheduler = DatabaseDescriptor.getRequestScheduler();
    }
    
    public ClientState state()
    {
        return clientState.get();
    }

    protected Map<DecoratedKey, ColumnFamily> readColumnFamily(List<ReadCommand> commands, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        // TODO - Support multiple column families per row, right now row only contains 1 column family
        Map<DecoratedKey, ColumnFamily> columnFamilyKeyMap = new HashMap<DecoratedKey, ColumnFamily>();

        if (consistency_level == ConsistencyLevel.ANY)
        {
            throw new InvalidRequestException("Consistency level any may not be applied to read operations");
        }

        List<Row> rows;
        try
        {
            try
            {
                schedule();
                rows = StorageProxy.readProtocol(commands, consistency_level);
            }
            finally
            {
                release();
            }
        }
        catch (TimeoutException e) 
        {
        	throw new TimedOutException();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        for (Row row: rows)
        {
            columnFamilyKeyMap.put(row.key, row.cf);
        }
        return columnFamilyKeyMap;
    }

    public List<Column> thriftifySubColumns(Collection<IColumn> columns)
    {
        if (columns == null || columns.isEmpty())
        {
            return EMPTY_SUBCOLUMNS;
        }

        ArrayList<Column> thriftColumns = new ArrayList<Column>(columns.size());
        for (IColumn column : columns)
        {
            if (column.isMarkedForDelete())
            {
                continue;
            }
            Column thrift_column = new Column(column.name(), column.value(), column.timestamp());
            if (column instanceof ExpiringColumn)
            {
                thrift_column.setTtl(((ExpiringColumn) column).getTimeToLive());
            }
            thriftColumns.add(thrift_column);
        }

        return thriftColumns;
    }

    public List<ColumnOrSuperColumn> thriftifyColumns(Collection<IColumn> columns, boolean reverseOrder)
    {
        ArrayList<ColumnOrSuperColumn> thriftColumns = new ArrayList<ColumnOrSuperColumn>(columns.size());
        for (IColumn column : columns)
        {
            if (column.isMarkedForDelete())
            {
                continue;
            }
            Column thrift_column = new Column(column.name(), column.value(), column.timestamp());
            if (column instanceof ExpiringColumn)
            {
                thrift_column.setTtl(((ExpiringColumn) column).getTimeToLive());
            }
            thriftColumns.add(new ColumnOrSuperColumn().setColumn(thrift_column));
        }

        // we have to do the reversing here, since internally we pass results around in ColumnFamily
        // objects, which always sort their columns in the "natural" order
        // TODO this is inconvenient for direct users of StorageProxy
        if (reverseOrder)
            Collections.reverse(thriftColumns);
        return thriftColumns;
    }

    private List<ColumnOrSuperColumn> thriftifySuperColumns(Collection<IColumn> columns, boolean reverseOrder)
    {
        ArrayList<ColumnOrSuperColumn> thriftSuperColumns = new ArrayList<ColumnOrSuperColumn>(columns.size());
        for (IColumn column : columns)
        {
            List<Column> subcolumns = thriftifySubColumns(column.getSubColumns());
            if (subcolumns.isEmpty())
            {
                continue;
            }
            SuperColumn superColumn = new SuperColumn(column.name(), subcolumns);
            thriftSuperColumns.add(new ColumnOrSuperColumn().setSuper_column(superColumn));
        }

        if (reverseOrder)
            Collections.reverse(thriftSuperColumns);

        return thriftSuperColumns;
    }

    private Map<ByteBuffer, List<ColumnOrSuperColumn>> getSlice(List<ReadCommand> commands, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        Map<DecoratedKey, ColumnFamily> columnFamilies = readColumnFamily(commands, consistency_level);
        Map<ByteBuffer, List<ColumnOrSuperColumn>> columnFamiliesMap = new HashMap<ByteBuffer, List<ColumnOrSuperColumn>>();
        for (ReadCommand command: commands)
        {
            ColumnFamily cf = columnFamilies.get(StorageService.getPartitioner().decorateKey(command.key));
            boolean reverseOrder = command instanceof SliceFromReadCommand && ((SliceFromReadCommand)command).reversed;
            List<ColumnOrSuperColumn> thriftifiedColumns = thriftifyColumnFamily(cf, command.queryPath.superColumnName != null, reverseOrder);
            columnFamiliesMap.put(command.key, thriftifiedColumns);
        }

        return columnFamiliesMap;
    }

    private List<ColumnOrSuperColumn> thriftifyColumnFamily(ColumnFamily cf, boolean subcolumnsOnly, boolean reverseOrder)
    {
        if (cf == null || cf.getColumnsMap().size() == 0)
            return EMPTY_COLUMNS;
        if (subcolumnsOnly)
        {
            IColumn column = cf.getColumnsMap().values().iterator().next();
            Collection<IColumn> subcolumns = column.getSubColumns();
            if (subcolumns == null || subcolumns.isEmpty())
                return EMPTY_COLUMNS;
            else
                return thriftifyColumns(subcolumns, reverseOrder);
        }
        if (cf.isSuper())
            return thriftifySuperColumns(cf.getSortedColumns(), reverseOrder);        
        else
            return thriftifyColumns(cf.getSortedColumns(), reverseOrder);
    }

    public List<ColumnOrSuperColumn> get_slice(ByteBuffer key, ColumnParent column_parent, SlicePredicate predicate, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("get_slice");
        
        state().hasColumnFamilyAccess(column_parent.column_family, Permission.READ);
        return multigetSliceInternal(state().getKeyspace(), Collections.singletonList(key), column_parent, predicate, consistency_level).get(key);
    }
    
    public Map<ByteBuffer, List<ColumnOrSuperColumn>> multiget_slice(List<ByteBuffer> keys, ColumnParent column_parent, SlicePredicate predicate, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("multiget_slice");

        state().hasColumnFamilyAccess(column_parent.column_family, Permission.READ);

        return multigetSliceInternal(state().getKeyspace(), keys, column_parent, predicate, consistency_level);
    }

    private Map<ByteBuffer, List<ColumnOrSuperColumn>> multigetSliceInternal(String keyspace, List<ByteBuffer> keys, ColumnParent column_parent, SlicePredicate predicate, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        ThriftValidation.validateColumnParent(keyspace, column_parent);
        ThriftValidation.validatePredicate(keyspace, column_parent, predicate);

        List<ReadCommand> commands = new ArrayList<ReadCommand>();
        if (predicate.column_names != null)
        {
            for (ByteBuffer key: keys)
            {
                ThriftValidation.validateKey(key);
                commands.add(new SliceByNamesReadCommand(keyspace, key, column_parent, predicate.column_names));
            }
        }
        else
        {
            SliceRange range = predicate.slice_range;
            for (ByteBuffer key: keys)
            {
                ThriftValidation.validateKey(key);
                commands.add(new SliceFromReadCommand(keyspace, key, column_parent, range.start, range.finish, range.reversed, range.count));
            }
        }

        return getSlice(commands, consistency_level);
    }

    public ColumnOrSuperColumn get(ByteBuffer key, ColumnPath column_path, ConsistencyLevel consistency_level)
    throws InvalidRequestException, NotFoundException, UnavailableException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("get");
        
        state().hasColumnFamilyAccess(column_path.column_family, Permission.READ);
        String keyspace = state().getKeyspace();

        ThriftValidation.validateColumnPath(keyspace, column_path);

        QueryPath path = new QueryPath(column_path.column_family, column_path.column == null ? null : column_path.super_column);
        List<ByteBuffer> nameAsList = Arrays.asList(column_path.column == null ? column_path.super_column : column_path.column);
        ThriftValidation.validateKey(key);
        ReadCommand command = new SliceByNamesReadCommand(keyspace, key, path, nameAsList);

        Map<DecoratedKey, ColumnFamily> cfamilies = readColumnFamily(Arrays.asList(command), consistency_level);

        ColumnFamily cf = cfamilies.get(StorageService.getPartitioner().decorateKey(command.key));

        if (cf == null)
            throw new NotFoundException();
        List<ColumnOrSuperColumn> tcolumns = thriftifyColumnFamily(cf, command.queryPath.superColumnName != null, false);
        if (tcolumns.isEmpty())
            throw new NotFoundException();
        assert tcolumns.size() == 1;
        return tcolumns.get(0);
    }

    public int get_count(ByteBuffer key, ColumnParent column_parent, SlicePredicate predicate, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("get_count");

        state().hasColumnFamilyAccess(column_parent.column_family, Permission.READ);

        return get_slice(key, column_parent, predicate, consistency_level).size();
    }

    public Map<ByteBuffer, Integer> multiget_count(List<ByteBuffer> keys, ColumnParent column_parent, SlicePredicate predicate, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("multiget_count");

        state().hasColumnFamilyAccess(column_parent.column_family, Permission.READ);
        String keyspace = state().getKeyspace();

        Map<ByteBuffer, Integer> counts = new HashMap<ByteBuffer, Integer>();
        Map<ByteBuffer, List<ColumnOrSuperColumn>> columnFamiliesMap = multigetSliceInternal(keyspace, keys, column_parent, predicate, consistency_level);

        for (Map.Entry<ByteBuffer, List<ColumnOrSuperColumn>> cf : columnFamiliesMap.entrySet()) {
          counts.put(cf.getKey(), cf.getValue().size());
        }
        return counts;
    }

    public void insert(ByteBuffer key, ColumnParent column_parent, Column column, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("insert");

        state().hasColumnFamilyAccess(column_parent.column_family, Permission.WRITE);

        ThriftValidation.validateKey(key);
        ThriftValidation.validateColumnParent(state().getKeyspace(), column_parent);
        ThriftValidation.validateColumn(state().getKeyspace(), column_parent, column);

        RowMutation rm = new RowMutation(state().getKeyspace(), key);
        try
        {
            rm.add(new QueryPath(column_parent.column_family, column_parent.super_column, column.name), column.value, column.timestamp, column.ttl);
        }
        catch (MarshalException e)
        {
            throw new InvalidRequestException(e.getMessage());
        }
        doInsert(consistency_level, Arrays.asList(rm));
    }

    public void batch_mutate(Map<ByteBuffer,Map<String,List<Mutation>>> mutation_map, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("batch_mutate");
        
        List<String> cfamsSeen = new ArrayList<String>();

        List<RowMutation> rowMutations = new ArrayList<RowMutation>();
        for (Map.Entry<ByteBuffer, Map<String, List<Mutation>>> mutationEntry: mutation_map.entrySet())
        {
            ByteBuffer key = mutationEntry.getKey();

            ThriftValidation.validateKey(key);
            Map<String, List<Mutation>> columnFamilyToMutations = mutationEntry.getValue();
            for (Map.Entry<String, List<Mutation>> columnFamilyMutations : columnFamilyToMutations.entrySet())
            {
                String cfName = columnFamilyMutations.getKey();
                
                // Avoid unneeded authorizations
                if (!(cfamsSeen.contains(cfName)))
                {
                    state().hasColumnFamilyAccess(cfName, Permission.WRITE);
                    cfamsSeen.add(cfName);
                }

                for (Mutation mutation : columnFamilyMutations.getValue())
                {
                    ThriftValidation.validateMutation(state().getKeyspace(), cfName, mutation);
                }
            }
            rowMutations.add(RowMutation.getRowMutationFromMutations(state().getKeyspace(), key, columnFamilyToMutations));
        }

        doInsert(consistency_level, rowMutations);
    }

    public void remove(ByteBuffer key, ColumnPath column_path, long timestamp, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("remove");

        state().hasColumnFamilyAccess(column_path.column_family, Permission.WRITE);

        ThriftValidation.validateKey(key);
        ThriftValidation.validateColumnPathOrParent(state().getKeyspace(), column_path);

        RowMutation rm = new RowMutation(state().getKeyspace(), key);
        rm.delete(new QueryPath(column_path), timestamp); 

        doInsert(consistency_level, Arrays.asList(rm));
    }

    private void doInsert(ConsistencyLevel consistency_level, List<RowMutation> mutations) throws UnavailableException, TimedOutException
    {
        try
        {
            schedule();

            try
            {
              StorageProxy.mutate(mutations, consistency_level);
            }
            catch (TimeoutException e)
            {
              throw new TimedOutException();
            }
        }
        finally
        {
            release();
        }
    }

    public KsDef describe_keyspace(String table) throws NotFoundException, InvalidRequestException
    {
        state().hasKeyspaceListAccess(Permission.READ);
        
        KSMetaData ksm = DatabaseDescriptor.getTableDefinition(table);
        if (ksm == null)
            throw new NotFoundException();

        List<CfDef> cfDefs = new ArrayList<CfDef>();
        for (CFMetaData cfm : ksm.cfMetaData().values())
            cfDefs.add(CFMetaData.convertToThrift(cfm));
        KsDef ksdef = new KsDef(ksm.name, ksm.strategyClass.getName(), ksm.replicationFactor, cfDefs);
        ksdef.setStrategy_options(ksm.strategyOptions);
        return ksdef;
    }

    public List<KeySlice> get_range_slices(ColumnParent column_parent, SlicePredicate predicate, KeyRange range, ConsistencyLevel consistency_level)
    throws InvalidRequestException, UnavailableException, TException, TimedOutException
    {
        if (logger.isDebugEnabled())
            logger.debug("range_slice");

        String keyspace = state().getKeyspace();
        state().hasColumnFamilyAccess(column_parent.column_family, Permission.READ);

        ThriftValidation.validateColumnParent(keyspace, column_parent);
        ThriftValidation.validatePredicate(keyspace, column_parent, predicate);
        ThriftValidation.validateKeyRange(range);

        List<Row> rows;
        try
        {
            IPartitioner p = StorageService.getPartitioner();
            AbstractBounds bounds;
            if (range.start_key == null)
            {
                Token.TokenFactory tokenFactory = p.getTokenFactory();
                Token left = tokenFactory.fromString(range.start_token);
                Token right = tokenFactory.fromString(range.end_token);
                bounds = new Range(left, right);
            }
            else
            {
                bounds = new Bounds(p.getToken(range.start_key), p.getToken(range.end_key));
            }
            try
            {
                schedule();
                rows = StorageProxy.getRangeSlice(new RangeSliceCommand(keyspace, column_parent, predicate, bounds, range.count), consistency_level);
            }
            finally
            {
                release();
            }
            assert rows != null;
        }
        catch (TimeoutException e)
        {
        	throw new TimedOutException();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return thriftifyKeySlices(rows, column_parent, predicate);
    }

    private List<KeySlice> thriftifyKeySlices(List<Row> rows, ColumnParent column_parent, SlicePredicate predicate)
    {
        List<KeySlice> keySlices = new ArrayList<KeySlice>(rows.size());
        boolean reversed = predicate.slice_range != null && predicate.slice_range.reversed;
        for (Row row : rows)
        {
            List<ColumnOrSuperColumn> thriftifiedColumns = thriftifyColumnFamily(row.cf, column_parent.super_column != null, reversed);
            keySlices.add(new KeySlice(row.key.key, thriftifiedColumns));
        }

        return keySlices;
    }

    public List<KeySlice> get_indexed_slices(ColumnParent column_parent, IndexClause index_clause, SlicePredicate column_predicate, ConsistencyLevel consistency_level) throws InvalidRequestException, UnavailableException, TimedOutException, TException
    {
        if (logger.isDebugEnabled())
            logger.debug("scan");

        state().hasColumnFamilyAccess(column_parent.column_family, Permission.READ);
        String keyspace = state().getKeyspace();
        ThriftValidation.validateColumnParent(keyspace, column_parent);
        ThriftValidation.validatePredicate(keyspace, column_parent, column_predicate);
        ThriftValidation.validateIndexClauses(keyspace, column_parent.column_family, index_clause);

        List<Row> rows;
        try
        {
            rows = StorageProxy.scan(keyspace, column_parent.column_family, index_clause, column_predicate, consistency_level);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (TimeoutException e)
        {
            throw new TimedOutException();
        }
        return thriftifyKeySlices(rows, column_parent, column_predicate);
    }

    public List<KsDef> describe_keyspaces() throws TException, InvalidRequestException
    {
        state().hasKeyspaceListAccess(Permission.READ);
        
        Set<String> keyspaces = DatabaseDescriptor.getTables();
        List<KsDef> ksset = new ArrayList<KsDef>();
        for (String ks : keyspaces)
        {
            try
            {
                ksset.add(describe_keyspace(ks));
            }
            catch (NotFoundException nfe)
            {
                logger.info("Failed to find metadata for keyspace '" + ks + "'. Continuing... ");
            }
        }
        return ksset;
    }

    public String describe_cluster_name() throws TException
    {
        return DatabaseDescriptor.getClusterName();
    }

    public String describe_version() throws TException
    {
        return Constants.VERSION;
    }

    public List<TokenRange> describe_ring(String keyspace)throws InvalidRequestException
    {
        if (keyspace == null || !DatabaseDescriptor.getNonSystemTables().contains(keyspace))
            throw new InvalidRequestException("There is no ring for the keyspace: " + keyspace);
        List<TokenRange> ranges = new ArrayList<TokenRange>();
        Token.TokenFactory tf = StorageService.getPartitioner().getTokenFactory();
        for (Map.Entry<Range, List<String>> entry : StorageService.instance.getRangeToEndpointMap(keyspace).entrySet())
        {
            Range range = entry.getKey();
            List<String> endpoints = entry.getValue();
            ranges.add(new TokenRange(tf.toString(range.left), tf.toString(range.right), endpoints));
        }
        return ranges;
    }

    public String describe_partitioner() throws TException
    {
        return StorageService.getPartitioner().getClass().getName();
    }

    public String describe_snitch() throws TException
    {
        if (DatabaseDescriptor.getEndpointSnitch() instanceof DynamicEndpointSnitch)
            return ((DynamicEndpointSnitch)DatabaseDescriptor.getEndpointSnitch()).subsnitch.getClass().getName();
        return DatabaseDescriptor.getEndpointSnitch().getClass().getName();
    }

    public List<String> describe_splits(String cfName, String start_token, String end_token, int keys_per_split) throws TException
    {
        // TODO: add keyspace authorization call post CASSANDRA-1425
        Token.TokenFactory tf = StorageService.getPartitioner().getTokenFactory();
        List<Token> tokens = StorageService.instance.getSplits(state().getKeyspace(), cfName, new Range(tf.fromString(start_token), tf.fromString(end_token)), keys_per_split);
        List<String> splits = new ArrayList<String>(tokens.size());
        for (Token token : tokens)
        {
            splits.add(tf.toString(token));
        }
        return splits;
    }

    public void login(AuthenticationRequest auth_request) throws AuthenticationException, AuthorizationException, TException
    {
         state().login(auth_request.getCredentials());
    }

    /**
     * Schedule the current thread for access to the required services
     */
    private void schedule()
    {
        requestScheduler.queue(Thread.currentThread(), state().getSchedulingValue());
    }

    /**
     * Release count for the used up resources
     */
    private void release()
    {
        requestScheduler.release();
    }
    
    // helper method to apply migration on the migration stage. typical migration failures will throw an 
    // InvalidRequestException. atypical failures will throw a RuntimeException.
    private static void applyMigrationOnStage(final Migration m) throws InvalidRequestException
    {
        Future f = StageManager.getStage(Stage.MIGRATION).submit(new Callable()
        {
            public Object call() throws Exception
            {
                m.apply();
                m.announce();
                return null;
            }
        });
        try
        {
            f.get();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        catch (ExecutionException e)
        {
            // this means call() threw an exception. deal with it directly.
            if (e.getCause() != null)
            {
                InvalidRequestException ex = new InvalidRequestException(e.getCause().getMessage());
                ex.initCause(e.getCause());
                throw ex;
            }
            else
            {
                InvalidRequestException ex = new InvalidRequestException(e.getMessage());
                ex.initCause(e);
                throw ex;
            }
        }
    }

    public String system_add_column_family(CfDef cf_def) throws InvalidRequestException, TException
    {
        state().hasColumnFamilyListAccess(Permission.WRITE);
        try
        {
            applyMigrationOnStage(new AddColumnFamily(convertToCFMetaData(cf_def)));
            return DatabaseDescriptor.getDefsVersion().toString();
        }
        catch (ConfigurationException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
        catch (IOException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }

    public String system_drop_column_family(String column_family) throws InvalidRequestException, TException
    {
        state().hasColumnFamilyListAccess(Permission.WRITE);
        
        try
        {
            applyMigrationOnStage(new DropColumnFamily(state().getKeyspace(), column_family, true));
            return DatabaseDescriptor.getDefsVersion().toString();
        }
        catch (ConfigurationException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
        catch (IOException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }

    public String system_add_keyspace(KsDef ks_def) throws InvalidRequestException, TException
    {
        state().hasKeyspaceListAccess(Permission.WRITE);
        
        // generate a meaningful error if the user setup keyspace and/or column definition incorrectly
        for (CfDef cf : ks_def.cf_defs) 
        {
            if (!cf.getKeyspace().equals(ks_def.getName()))
            {
                throw new InvalidRequestException("CsDef (" + cf.getName() +") had a keyspace definition that did not match KsDef");
            }
        }

        try
        {
            Collection<CFMetaData> cfDefs = new ArrayList<CFMetaData>(ks_def.cf_defs.size());
            for (CfDef cfDef : ks_def.cf_defs)
            {
                cfDefs.add(convertToCFMetaData(cfDef));
            }

            KSMetaData ksm = new KSMetaData(ks_def.name,
                                            FBUtilities.<AbstractReplicationStrategy>classForName(ks_def.strategy_class, "keyspace replication strategy"),
                                            ks_def.strategy_options,
                                            ks_def.replication_factor,
                                            cfDefs.toArray(new CFMetaData[cfDefs.size()]));
            applyMigrationOnStage(new AddKeyspace(ksm));
            return DatabaseDescriptor.getDefsVersion().toString();
        }
        catch (ConfigurationException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
        catch (IOException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }
    
    public String system_drop_keyspace(String keyspace) throws InvalidRequestException, TException
    {
        state().hasKeyspaceListAccess(Permission.WRITE);
        
        try
        {
            applyMigrationOnStage(new DropKeyspace(keyspace, true));
            return DatabaseDescriptor.getDefsVersion().toString();
        }
        catch (ConfigurationException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
        catch (IOException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }

    /** update an existing keyspace, but do not allow column family modifications. */
    public String system_update_keyspace(KsDef ks_def) throws InvalidRequestException, TException
    {
        state().hasKeyspaceListAccess(Permission.WRITE);

        ThriftValidation.validateTable(ks_def.name);
        if (ks_def.getCf_defs() != null && ks_def.getCf_defs().size() > 0)
            throw new InvalidRequestException("Keyspace update must not contain any column family definitions.");
        
        try
        {
            KSMetaData ksm = new KSMetaData(
                    ks_def.name, 
                    FBUtilities.<AbstractReplicationStrategy>classForName(ks_def.strategy_class, "keyspace replication strategy"),
                    ks_def.strategy_options,
                    ks_def.replication_factor);
            applyMigrationOnStage(new UpdateKeyspace(ksm));
            return DatabaseDescriptor.getDefsVersion().toString();
        }
        catch (ConfigurationException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
        catch (IOException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }

    public String system_update_column_family(CfDef cf_def) throws InvalidRequestException, TException
    {
        state().hasColumnFamilyListAccess(Permission.WRITE);
        
        if (cf_def.keyspace == null || cf_def.name == null)
            throw new InvalidRequestException("Keyspace and CF name must be set.");
        
        CFMetaData oldCfm = DatabaseDescriptor.getCFMetaData(CFMetaData.getId(cf_def.keyspace, cf_def.name));
        if (oldCfm == null) 
            throw new InvalidRequestException("Could not find column family definition to modify.");
        
        try
        {
            CFMetaData newCfm = oldCfm.apply(cf_def);
            UpdateColumnFamily update = new UpdateColumnFamily(oldCfm, newCfm);
            applyMigrationOnStage(update);
            return DatabaseDescriptor.getDefsVersion().toString();
        }
        catch (ConfigurationException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
        catch (IOException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }

    private CFMetaData convertToCFMetaData(CfDef cf_def) throws InvalidRequestException, ConfigurationException
    {
        ColumnFamilyType cfType = ColumnFamilyType.create(cf_def.column_type);
        if (cfType == null)
        {
          throw new InvalidRequestException("Invalid column type " + cf_def.column_type);
        }

        CFMetaData.validateMinMaxCompactionThresholds(cf_def);
        CFMetaData.validateMemtableSettings(cf_def);

        return new CFMetaData(cf_def.keyspace,
                              cf_def.name,
                              cfType,
                              DatabaseDescriptor.getComparator(cf_def.comparator_type),
                              cf_def.subcomparator_type == null ? null : DatabaseDescriptor.getComparator(cf_def.subcomparator_type),
                              cf_def.comment,
                              cf_def.row_cache_size,
                              cf_def.key_cache_size,
                              cf_def.read_repair_chance,
                              cf_def.isSetGc_grace_seconds() ? cf_def.gc_grace_seconds : CFMetaData.DEFAULT_GC_GRACE_SECONDS,
                              DatabaseDescriptor.getComparator(cf_def.default_validation_class),
                              cf_def.isSetMin_compaction_threshold() ? cf_def.min_compaction_threshold : CFMetaData.DEFAULT_MIN_COMPACTION_THRESHOLD,
                              cf_def.isSetMax_compaction_threshold() ? cf_def.max_compaction_threshold : CFMetaData.DEFAULT_MAX_COMPACTION_THRESHOLD,
                              cf_def.isSetRow_cache_save_period_in_seconds() ? cf_def.row_cache_save_period_in_seconds : CFMetaData.DEFAULT_ROW_CACHE_SAVE_PERIOD_IN_SECONDS,
                              cf_def.isSetKey_cache_save_period_in_seconds() ? cf_def.key_cache_save_period_in_seconds : CFMetaData.DEFAULT_KEY_CACHE_SAVE_PERIOD_IN_SECONDS,
                              cf_def.isSetMemtable_flush_after_mins() ? cf_def.memtable_flush_after_mins : CFMetaData.DEFAULT_MEMTABLE_LIFETIME_IN_MINS,
                              cf_def.isSetMemtable_throughput_in_mb() ? cf_def.memtable_throughput_in_mb : CFMetaData.DEFAULT_MEMTABLE_THROUGHPUT_IN_MB,
                              cf_def.isSetMemtable_operations_in_millions() ? cf_def.memtable_operations_in_millions : CFMetaData.DEFAULT_MEMTABLE_OPERATIONS_IN_MILLIONS,
                              ColumnDefinition.fromColumnDef(cf_def.column_metadata));
    }

    public void truncate(String cfname) throws InvalidRequestException, UnavailableException, TException
    {
        logger.debug("truncating {} in {}", cfname, state().getKeyspace());
        state().hasColumnFamilyAccess(cfname, Permission.WRITE);
        try
        {
            schedule();
            StorageProxy.truncateBlocking(state().getKeyspace(), cfname);
        }
        catch (TimeoutException e)
        {
            throw (UnavailableException) new UnavailableException().initCause(e);
        }
        catch (IOException e)
        {
            throw (UnavailableException) new UnavailableException().initCause(e);
        }
        finally
        {
            release();
        }
    }

    public void set_keyspace(String keyspace) throws InvalidRequestException, TException
    {
        ThriftValidation.validateTable(keyspace);

        state().setKeyspace(keyspace);
    }

    public Map<String, List<String>> describe_schema_versions() throws TException, InvalidRequestException
    {
        logger.debug("checking schema agreement");      
        return StorageProxy.describeSchemaVersions();
    }

    // main method moved to CassandraDaemon
}
