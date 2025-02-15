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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.cassandra.concurrent.DebuggableScheduledThreadPoolExecutor;
import org.apache.cassandra.concurrent.ThreadFactoryImpl;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.net.EndPoint;
import org.apache.cassandra.io.SSTableReader;

import org.apache.log4j.Logger;

class MinorCompactionManager
{
    private static MinorCompactionManager instance_;
    private static Lock lock_ = new ReentrantLock();
    private static Logger logger_ = Logger.getLogger(MinorCompactionManager.class);
    private static final long intervalInMins_ = 5;
    static final int MINCOMPACTION_THRESHOLD = 4; // compact this many sstables min at a time
    static final int MAXCOMPACTION_THRESHOLD = 32; // compact this many sstables max at a time

    public static MinorCompactionManager instance()
    {
        if ( instance_ == null )
        {
            lock_.lock();
            try
            {
                if ( instance_ == null )
                    instance_ = new MinorCompactionManager();
            }
            finally
            {
                lock_.unlock();
            }
        }
        return instance_;
    }

    static class FileCompactor2 implements Callable<List<SSTableReader>>
    {
        private ColumnFamilyStore columnFamilyStore_;
        private List<Range> ranges_;
        private EndPoint target_;

        FileCompactor2(ColumnFamilyStore columnFamilyStore, List<Range> ranges, EndPoint target)
        {
            columnFamilyStore_ = columnFamilyStore;
            ranges_ = ranges;
            target_ = target;
        }

        public List<SSTableReader> call()
        {
        	List<SSTableReader> results;
            if (logger_.isDebugEnabled())
              logger_.debug("Started  compaction ..."+columnFamilyStore_.columnFamily_);
            try
            {
                results = columnFamilyStore_.doAntiCompaction(ranges_, target_);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            if (logger_.isDebugEnabled())
              logger_.debug("Finished compaction ..."+columnFamilyStore_.columnFamily_);
            return results;
        }
    }

    static class OnDemandCompactor implements Runnable
    {
        private ColumnFamilyStore columnFamilyStore_;
        private long skip_ = 0L;

        OnDemandCompactor(ColumnFamilyStore columnFamilyStore, long skip)
        {
            columnFamilyStore_ = columnFamilyStore;
            skip_ = skip;
        }

        public void run()
        {
            if (logger_.isDebugEnabled())
              logger_.debug("Started  Major compaction for " + columnFamilyStore_.columnFamily_);
            try
            {
                columnFamilyStore_.doMajorCompaction(skip_);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            if (logger_.isDebugEnabled())
              logger_.debug("Finished Major compaction for " + columnFamilyStore_.columnFamily_);
        }
    }

    static class CleanupCompactor implements Runnable
    {
        private ColumnFamilyStore columnFamilyStore_;

        CleanupCompactor(ColumnFamilyStore columnFamilyStore)
        {
        	columnFamilyStore_ = columnFamilyStore;
        }

        public void run()
        {
            if (logger_.isDebugEnabled())
              logger_.debug("Started  compaction ..."+columnFamilyStore_.columnFamily_);
            try
            {
                columnFamilyStore_.doCleanupCompaction();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            if (logger_.isDebugEnabled())
              logger_.debug("Finished compaction ..."+columnFamilyStore_.columnFamily_);
        }
    }
    
    
    private ScheduledExecutorService compactor_ = new DebuggableScheduledThreadPoolExecutor(1, new ThreadFactoryImpl("MINOR-COMPACTION-POOL"));

    /**
     * Call this whenever a compaction might be needed on the given columnfamily.
     * It's okay to over-call (within reason) since the compactions are single-threaded,
     * and if a call is unnecessary, it will just be no-oped in the bucketing phase.
     */
    public Future<Integer> submit(final ColumnFamilyStore columnFamilyStore)
    {
        return submit(columnFamilyStore, MINCOMPACTION_THRESHOLD, MAXCOMPACTION_THRESHOLD);
    }

    Future<Integer> submit(final ColumnFamilyStore columnFamilyStore, final int minThreshold, final int maxThreshold)
    {
        Callable<Integer> callable = new Callable<Integer>()
        {
            public Integer call() throws IOException
            {
                return columnFamilyStore.doCompaction(minThreshold, maxThreshold);
            }
        };
        return compactor_.submit(callable);
    }

    public void submitCleanup(ColumnFamilyStore columnFamilyStore)
    {
        compactor_.submit(new CleanupCompactor(columnFamilyStore));
    }

    public Future<List<SSTableReader>> submit(ColumnFamilyStore columnFamilyStore, List<Range> ranges, EndPoint target)
    {
        return compactor_.submit( new FileCompactor2(columnFamilyStore, ranges, target) );
    }

    public void  submitMajor(ColumnFamilyStore columnFamilyStore, long skip)
    {
        compactor_.submit( new OnDemandCompactor(columnFamilyStore, skip) );
    }
}
