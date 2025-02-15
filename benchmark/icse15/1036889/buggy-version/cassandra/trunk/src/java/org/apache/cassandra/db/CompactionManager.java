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

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.collections.PredicateUtils;
import org.apache.commons.collections.iterators.CollatingIterator;
import org.apache.commons.collections.iterators.FilterIterator;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.concurrent.DebuggableThreadPoolExecutor;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.io.AbstractCompactedRow;
import org.apache.cassandra.io.CompactionIterator;
import org.apache.cassandra.io.ICompactionInfo;
import org.apache.cassandra.io.sstable.*;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.service.AntiEntropyService;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.Pair;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

public class CompactionManager implements CompactionManagerMBean
{
    public static final String MBEAN_OBJECT_NAME = "org.apache.cassandra.db:type=CompactionManager";
    private static final Logger logger = LoggerFactory.getLogger(CompactionManager.class);
    public static final CompactionManager instance;
    private final ReentrantLock compactionLock = new ReentrantLock();
    // todo: should provide a way to unlock in mbean?

    static
    {
        instance = new CompactionManager();
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try
        {
            mbs.registerMBean(instance, new ObjectName(MBEAN_OBJECT_NAME));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private CompactionExecutor executor = new CompactionExecutor();
    private Map<ColumnFamilyStore, Integer> estimatedCompactions = new NonBlockingHashMap<ColumnFamilyStore, Integer>();
    
    public void lockCompactions()
    {
        compactionLock.lock();
    }
    
    public void unlockCompactions()
    {
        compactionLock.unlock();
    }

    /**
     * Call this whenever a compaction might be needed on the given columnfamily.
     * It's okay to over-call (within reason) since the compactions are single-threaded,
     * and if a call is unnecessary, it will just be no-oped in the bucketing phase.
     */
    public Future<Integer> submitMinorIfNeeded(final ColumnFamilyStore cfs)
    {
        Callable<Integer> callable = new Callable<Integer>()
        {
            public Integer call() throws IOException
            {
                compactionLock.lock();
                try
                {
                    Integer minThreshold = cfs.getMinimumCompactionThreshold();
                    Integer maxThreshold = cfs.getMaximumCompactionThreshold();
    
                    if (minThreshold == 0 || maxThreshold == 0)
                    {
                        logger.debug("Compaction is currently disabled.");
                        return 0;
                    }
                    logger.debug("Checking to see if compaction of " + cfs.columnFamily + " would be useful");
                    Set<List<SSTableReader>> buckets = getBuckets(convertSSTablesToPairs(cfs.getSSTables()), 50L * 1024L * 1024L);
                    updateEstimateFor(cfs, buckets);
                    
                    for (List<SSTableReader> sstables : buckets)
                    {
                        if (sstables.size() >= minThreshold)
                        {
                            // if we have too many to compact all at once, compact older ones first -- this avoids
                            // re-compacting files we just created.
                            Collections.sort(sstables);
                            return doCompaction(cfs, sstables.subList(0, Math.min(sstables.size(), maxThreshold)), (int) (System.currentTimeMillis() / 1000) - cfs.metadata.gcGraceSeconds);
                        }
                    }
                }
                finally 
                {
                    compactionLock.unlock();
                }
                return 0;
            }
        };
        return executor.submit(callable);
    }

    private void updateEstimateFor(ColumnFamilyStore cfs, Set<List<SSTableReader>> buckets)
    {
        Integer minThreshold = cfs.getMinimumCompactionThreshold();
        Integer maxThreshold = cfs.getMaximumCompactionThreshold();

        if (minThreshold > 0 && maxThreshold > 0)
        {
            int n = 0;
            for (List<SSTableReader> sstables : buckets)
            {
                if (sstables.size() >= minThreshold)
                {
                    n += Math.ceil((double)sstables.size() / maxThreshold);
                }
            }
            estimatedCompactions.put(cfs, n);
        }
        else
        {
            logger.debug("Compaction is currently disabled.");
        }
    }

    public void performCleanup(final ColumnFamilyStore cfStore) throws InterruptedException, ExecutionException
    {
        Callable<Object> runnable = new Callable<Object>()
        {
            public Object call() throws IOException
            {
                compactionLock.lock();
                try 
                {
                    doCleanupCompaction(cfStore);
                    return this;
                }
                finally 
                {
                    compactionLock.unlock();
                }
            }
        };
        executor.submit(runnable).get();
    }

    public void performMajor(final ColumnFamilyStore cfStore) throws InterruptedException, ExecutionException
    {
        submitMajor(cfStore, 0, (int) (System.currentTimeMillis() / 1000) - cfStore.metadata.gcGraceSeconds).get();
    }

    public Future<Object> submitMajor(final ColumnFamilyStore cfStore, final long skip, final int gcBefore)
    {
        Callable<Object> callable = new Callable<Object>()
        {
            public Object call() throws IOException
            {
                compactionLock.lock();
                try
                {
                    Collection<SSTableReader> sstables;
                    if (skip > 0)
                    {
                        sstables = new ArrayList<SSTableReader>();
                        for (SSTableReader sstable : cfStore.getSSTables())
                        {
                            if (sstable.length() < skip * 1024L * 1024L * 1024L)
                            {
                                sstables.add(sstable);
                            }
                        }
                    }
                    else
                    {
                        sstables = cfStore.getSSTables();
                    }
    
                    doCompaction(cfStore, sstables, gcBefore);
                    return this;
                }
                finally 
                {
                    compactionLock.unlock();
                }
            }
        };
        return executor.submit(callable);
    }

    public Future<Object> submitValidation(final ColumnFamilyStore cfStore, final AntiEntropyService.Validator validator)
    {
        Callable<Object> callable = new Callable<Object>()
        {
            public Object call() throws IOException
            {
                compactionLock.lock();
                try
                {
                    doValidationCompaction(cfStore, validator);
                    return this;
                }
                finally
                {
                    compactionLock.unlock();
                }
            }
        };
        return executor.submit(callable);
    }

    /* Used in tests. */
    public void disableAutoCompaction()
    {
        for (String ksname : DatabaseDescriptor.getNonSystemTables())
        {
            Table ks = Table.open(ksname);
            for (ColumnFamilyStore cfs : ks.columnFamilyStores.values())
                cfs.disableAutoCompaction();
        }
    }

    /**
     * For internal use and testing only.  The rest of the system should go through the submit* methods,
     * which are properly serialized.
     */
    int doCompaction(ColumnFamilyStore cfs, Collection<SSTableReader> sstables, int gcBefore) throws IOException
    {
        // The collection of sstables passed may be empty (but not null); even if
        // it is not empty, it may compact down to nothing if all rows are deleted.
        Table table = cfs.table;
        if (DatabaseDescriptor.isSnapshotBeforeCompaction())
            table.snapshot("compact-" + cfs.columnFamily);
        logger.info("Compacting [" + StringUtils.join(sstables, ",") + "]");
        String compactionFileLocation = table.getDataFileLocation(cfs.getExpectedCompactedFileSize(sstables));
        // If the compaction file path is null that means we have no space left for this compaction.
        // try again w/o the largest one.
        List<SSTableReader> smallerSSTables = new ArrayList<SSTableReader>(sstables);
        while (compactionFileLocation == null && smallerSSTables.size() > 1)
        {
            logger.warn("insufficient space to compact all requested files " + StringUtils.join(smallerSSTables, ", "));
            smallerSSTables.remove(cfs.getMaxSizeFile(smallerSSTables));
            compactionFileLocation = table.getDataFileLocation(cfs.getExpectedCompactedFileSize(smallerSSTables));
        }
        if (compactionFileLocation == null)
        {
            logger.error("insufficient space to compact even the two smallest files, aborting");
            return 0;
        }
        sstables = smallerSSTables;

        // new sstables from flush can be added during a compaction, but only the compaction can remove them,
        // so in our single-threaded compaction world this is a valid way of determining if we're compacting
        // all the sstables (that existed when we started)
        boolean major = cfs.isCompleteSSTables(sstables);

        long startTime = System.currentTimeMillis();
        long totalkeysWritten = 0;

        // TODO the int cast here is potentially buggy
        int expectedBloomFilterSize = Math.max(DatabaseDescriptor.getIndexInterval(), (int)SSTableReader.getApproximateKeyCount(sstables));
        if (logger.isDebugEnabled())
          logger.debug("Expected bloom filter size : " + expectedBloomFilterSize);

        SSTableWriter writer;
        CompactionIterator ci = new CompactionIterator(cfs, sstables, gcBefore, major); // retain a handle so we can call close()
        Iterator<AbstractCompactedRow> nni = new FilterIterator(ci, PredicateUtils.notNullPredicate());
        executor.beginCompaction(cfs, ci);

        try
        {
            if (!nni.hasNext())
            {
                // don't mark compacted in the finally block, since if there _is_ nondeleted data,
                // we need to sync it (via closeAndOpen) first, so there is no period during which
                // a crash could cause data loss.
                cfs.markCompacted(sstables);
                return 0;
            }

            String newFilename = new File(cfs.getTempSSTablePath(compactionFileLocation)).getAbsolutePath();
            writer = new SSTableWriter(newFilename, expectedBloomFilterSize, cfs.metadata, cfs.partitioner);
            while (nni.hasNext())
            {
                AbstractCompactedRow row = nni.next();
                writer.append(row);
                totalkeysWritten++;
            }
        }
        finally
        {
            ci.close();
        }

        SSTableReader ssTable = writer.closeAndOpenReader(getMaxDataAge(sstables));
        cfs.replaceCompactedSSTables(sstables, Arrays.asList(ssTable));
        submitMinorIfNeeded(cfs);

        String format = "Compacted to %s.  %,d to %,d (~%d%% of original) bytes for %,d keys.  Time: %,dms.";
        long dTime = System.currentTimeMillis() - startTime;
        long startsize = SSTable.getTotalBytes(sstables);
        long endsize = ssTable.length();
        double ratio = (double)endsize / (double)startsize;
        logger.info(String.format(format, writer.getFilename(), startsize, endsize, (int) (ratio * 100), totalkeysWritten, dTime));
        return sstables.size();
    }

    private static long getMaxDataAge(Collection<SSTableReader> sstables)
    {
        long max = 0;
        for (SSTableReader sstable : sstables)
        {
            if (sstable.maxDataAge > max)
                max = sstable.maxDataAge;
        }
        return max;
    }

    /**
     * This function is used to do the anti compaction process , it spits out the file which has keys that belong to a given range
     * If the target is not specified it spits out the file as a compacted file with the unecessary ranges wiped out.
     *
     * @param cfs
     * @param sstables
     * @param ranges
     * @param target
     * @return
     * @throws java.io.IOException
     */
    private List<SSTableReader> doAntiCompaction(ColumnFamilyStore cfs, Collection<SSTableReader> sstables, Collection<Range> ranges, InetAddress target)
            throws IOException
    {
        Table table = cfs.table;
        logger.info("AntiCompacting [" + StringUtils.join(sstables, ",") + "]");
        // Calculate the expected compacted filesize
        long expectedRangeFileSize = cfs.getExpectedCompactedFileSize(sstables) / 2;
        String compactionFileLocation = table.getDataFileLocation(expectedRangeFileSize);
        if (compactionFileLocation == null)
        {
            throw new UnsupportedOperationException("disk full");
        }

        List<SSTableReader> results = new ArrayList<SSTableReader>();
        long startTime = System.currentTimeMillis();
        long totalkeysWritten = 0;

        int expectedBloomFilterSize = Math.max(DatabaseDescriptor.getIndexInterval(), (int)(SSTableReader.getApproximateKeyCount(sstables) / 2));
        if (logger.isDebugEnabled())
          logger.debug("Expected bloom filter size : " + expectedBloomFilterSize);

        SSTableWriter writer = null;
        CompactionIterator ci = new AntiCompactionIterator(cfs, sstables, ranges, (int) (System.currentTimeMillis() / 1000) - cfs.metadata.gcGraceSeconds, cfs.isCompleteSSTables(sstables));
        Iterator<AbstractCompactedRow> nni = new FilterIterator(ci, PredicateUtils.notNullPredicate());
        executor.beginCompaction(cfs, ci);

        try
        {
            if (!nni.hasNext())
            {
                return results;
            }

            while (nni.hasNext())
            {
                AbstractCompactedRow row = nni.next();
                if (writer == null)
                {
                    FileUtils.createDirectory(compactionFileLocation);
                    String newFilename = new File(cfs.getTempSSTablePath(compactionFileLocation)).getAbsolutePath();
                    writer = new SSTableWriter(newFilename, expectedBloomFilterSize, cfs.metadata, cfs.partitioner);
                }
                writer.append(row);
                totalkeysWritten++;
            }
        }
        finally
        {
            ci.close();
        }

        if (writer != null)
        {
            results.add(writer.closeAndOpenReader(getMaxDataAge(sstables)));

            String format = "AntiCompacted to %s.  %,d to %,d (~%d%% of original) bytes for %,d keys.  Time: %,dms.";
            long dTime = System.currentTimeMillis() - startTime;
            long startsize = SSTable.getTotalBytes(sstables);
            long endsize = results.get(0).length();
            double ratio = (double)endsize / (double)startsize;
            logger.info(String.format(format, writer.getFilename(), startsize, endsize, (int)(ratio*100), totalkeysWritten, dTime));
        }

        return results;
    }

    /**
     * This function goes over each file and removes the keys that the node is not responsible for
     * and only keeps keys that this node is responsible for.
     *
     * @throws IOException
     */
    private void doCleanupCompaction(ColumnFamilyStore cfs) throws IOException
    {
        Collection<SSTableReader> originalSSTables = cfs.getSSTables();
        List<SSTableReader> sstables = doAntiCompaction(cfs, originalSSTables, StorageService.instance.getLocalRanges(cfs.table.name), null);
        if (!sstables.isEmpty())
        {
            cfs.replaceCompactedSSTables(originalSSTables, sstables);
        }
    }

    /**
     * Performs a readonly "compaction" of all sstables in order to validate complete rows,
     * but without writing the merge result
     */
    private void doValidationCompaction(ColumnFamilyStore cfs, AntiEntropyService.Validator validator) throws IOException
    {
        CompactionIterator ci = new ValidationCompactionIterator(cfs);
        executor.beginCompaction(cfs, ci);
        try
        {
            Iterator<AbstractCompactedRow> nni = new FilterIterator(ci, PredicateUtils.notNullPredicate());

            // validate the CF as we iterate over it
            validator.prepare(cfs);
            while (nni.hasNext())
            {
                AbstractCompactedRow row = nni.next();
                validator.add(row);
            }
            validator.complete();
        }
        finally
        {
            ci.close();
        }
    }

    /*
    * Group files of similar size into buckets.
    */
    static <T> Set<List<T>> getBuckets(Collection<Pair<T, Long>> files, long min)
    {
        // Sort the list in order to get deterministic results during the grouping below
        List<Pair<T, Long>> sortedFiles = new ArrayList<Pair<T, Long>>(files);
        Collections.sort(sortedFiles, new Comparator<Pair<T, Long>>()
        {
            public int compare(Pair<T, Long> p1, Pair<T, Long> p2)
            {
                return p1.right.compareTo(p2.right);
            }
        });

        Map<List<T>, Long> buckets = new HashMap<List<T>, Long>();

        for (Pair<T, Long> pair: sortedFiles)
        {
            long size = pair.right;

            boolean bFound = false;
            // look for a bucket containing similar-sized files:
            // group in the same bucket if it's w/in 50% of the average for this bucket,
            // or this file and the bucket are all considered "small" (less than `min`)
            for (Entry<List<T>, Long> entry : buckets.entrySet())
            {
                List<T> bucket = entry.getKey();
                long averageSize = entry.getValue();
                if ((size > (averageSize / 2) && size < (3 * averageSize) / 2)
                    || (size < min && averageSize < min))
                {
                    // remove and re-add because adding changes the hash
                    buckets.remove(bucket);
                    long totalSize = bucket.size() * averageSize;
                    averageSize = (totalSize + size) / (bucket.size() + 1);
                    bucket.add(pair.left);
                    buckets.put(bucket, averageSize);
                    bFound = true;
                    break;
                }
            }
            // no similar bucket found; put it in a new one
            if (!bFound)
            {
                ArrayList<T> bucket = new ArrayList<T>();
                bucket.add(pair.left);
                buckets.put(bucket, size);
            }
        }

        return buckets.keySet();
    }

    private static Collection<Pair<SSTableReader, Long>> convertSSTablesToPairs(Collection<SSTableReader> collection)
    {
        Collection<Pair<SSTableReader, Long>> tablePairs = new ArrayList<Pair<SSTableReader, Long>>();
        for(SSTableReader table: collection)
        {
            tablePairs.add(new Pair<SSTableReader, Long>(table, table.length()));
        }
        return tablePairs;
    }
    
    public Future submitIndexBuild(final ColumnFamilyStore cfs, final Table.IndexBuilder builder)
    {
        Runnable runnable = new Runnable()
        {
            public void run()
            {
                compactionLock.lock();
                try
                {
                    executor.beginCompaction(cfs, builder);
                    builder.build();
                }
                finally
                {
                    compactionLock.unlock();
                }
            }
        };
        
        // don't submit to the executor if the compaction lock is held by the current thread. Instead return a simple
        // future that will be immediately immediately get()ed and executed. Happens during a migration, which locks
        // the compaction thread and then reinitializes a ColumnFamilyStore. Under normal circumstances, CFS spawns
        // index jobs to the compaction manager (this) and blocks on them.
        if (compactionLock.isHeldByCurrentThread())
            return new SimpleFuture(runnable);
        else
            return executor.submit(runnable);
    }
    
    public Future<SSTableReader> submitSSTableBuild(Descriptor desc)
    {
        final SSTableWriter.Builder builder = SSTableWriter.createBuilder(desc);
        Callable<SSTableReader> callable = new Callable<SSTableReader>()
        {
            public SSTableReader call() throws IOException
            {
                compactionLock.lock();
                try
                {
                    executor.beginCompaction(builder.cfs, builder);
                    return builder.build();
                }
                finally
                {
                    compactionLock.unlock();
                }
            }
        };
        return executor.submit(callable);
    }

    private static class ValidationCompactionIterator extends CompactionIterator
    {
        public ValidationCompactionIterator(ColumnFamilyStore cfs) throws IOException
        {
            super(cfs, cfs.getSSTables(), (int) (System.currentTimeMillis() / 1000) - cfs.metadata.gcGraceSeconds, true);
        }

        @Override
        public String getTaskType()
        {
            return "Validation";
        }
    }

    private static class AntiCompactionIterator extends CompactionIterator
    {
        private Set<SSTableScanner> scanners;

        public AntiCompactionIterator(ColumnFamilyStore cfStore, Collection<SSTableReader> sstables, Collection<Range> ranges, int gcBefore, boolean isMajor)
                throws IOException
        {
            super(cfStore, getCollatedRangeIterator(sstables, ranges), gcBefore, isMajor);
        }

        private static Iterator getCollatedRangeIterator(Collection<SSTableReader> sstables, final Collection<Range> ranges)
                throws IOException
        {
            org.apache.commons.collections.Predicate rangesPredicate = new org.apache.commons.collections.Predicate()
            {
                public boolean evaluate(Object row)
                {
                    return Range.isTokenInRanges(((SSTableIdentityIterator)row).getKey().token, ranges);
                }
            };
            // TODO CollatingIterator iter = FBUtilities.<SSTableIdentityIterator>getCollatingIterator();
            CollatingIterator iter = FBUtilities.getCollatingIterator();
            for (SSTableReader sstable : sstables)
            {
                SSTableScanner scanner = sstable.getScanner(FILE_BUFFER_SIZE);
                iter.addIterator(new FilterIterator(scanner, rangesPredicate));
            }
            return iter;
        }

        public Iterable<SSTableScanner> getScanners()
        {
            if (scanners == null)
            {
                scanners = new HashSet<SSTableScanner>();
                for (Object o : ((CollatingIterator)source).getIterators())
                {
                    scanners.add((SSTableScanner)((FilterIterator)o).getIterator());
                }
            }
            return scanners;
        }

        public String getTaskType()
        {
            return "Cleanup";
        }
    }

    public void checkAllColumnFamilies() throws IOException
    {
        // perform estimates
        for (final ColumnFamilyStore cfs : ColumnFamilyStore.all())
        {
            Runnable runnable = new Runnable()
            {
                public void run ()
                {
                    logger.debug("Estimating compactions for " + cfs.columnFamily);
                    final Set<List<SSTableReader>> buckets = getBuckets(convertSSTablesToPairs(cfs.getSSTables()), 50L * 1024L * 1024L);
                    updateEstimateFor(cfs, buckets);
                }
            };
            executor.submit(runnable);
        }

        // actually schedule compactions.  done in a second pass so all the estimates occur before we
        // bog down the executor in actual compactions.
        for (ColumnFamilyStore cfs : ColumnFamilyStore.all())
        {
            submitMinorIfNeeded(cfs);
        }
    }

    private static class CompactionExecutor extends DebuggableThreadPoolExecutor
    {
        private volatile ColumnFamilyStore cfs;
        private volatile ICompactionInfo ci;

        public CompactionExecutor()
        {
            super("CompactionExecutor", DatabaseDescriptor.getCompactionThreadPriority());
        }

        @Override
        public void afterExecute(Runnable r, Throwable t)
        {
            super.afterExecute(r, t);
            cfs = null;
            ci = null;
        }

        void beginCompaction(ColumnFamilyStore cfs, ICompactionInfo ci)
        {
            this.cfs = cfs;
            this.ci = ci;
        }

        public String getColumnFamilyName()
        {
            return cfs == null ? null : cfs.getColumnFamilyName();
        }

        public Long getBytesTotal()
        {
            return ci == null ? null : ci.getTotalBytes();
        }

        public Long getBytesCompleted()
        {
            return ci == null ? null : ci.getBytesRead();
        }

        public String getType()
        {
            return ci == null ? null : ci.getTaskType();
        }
    }

    public String getColumnFamilyInProgress()
    {
        return executor.getColumnFamilyName();
    }

    public Long getBytesTotalInProgress()
    {
        return executor.getBytesTotal();
    }

    public Long getBytesCompacted()
    {
        return executor.getBytesCompleted();
    }

    public String getCompactionType()
    {
        return executor.getType();
    }

    public int getPendingTasks()
    {
        int n = 0;
        for (Integer i : estimatedCompactions.values())
            n += i;
        return (int) (executor.getTaskCount() - executor.getCompletedTaskCount()) + n;
    }

    public long getCompletedTasks()
    {
        return executor.getCompletedTaskCount();
    }
    
    private class SimpleFuture implements Future
    {
        private Runnable runnable;
        
        private SimpleFuture(Runnable r) 
        {
            runnable = r;
        }
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            throw new IllegalStateException("May not call SimpleFuture.cancel()");
        }

        @Override
        public boolean isCancelled()
        {
            return false;
        }

        @Override
        public boolean isDone()
        {
            return runnable == null;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException
        {
            runnable.run();
            runnable = null;
            return runnable;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            throw new IllegalStateException("May not call SimpleFuture.get(long, TimeUnit)");
        }
    }
}
