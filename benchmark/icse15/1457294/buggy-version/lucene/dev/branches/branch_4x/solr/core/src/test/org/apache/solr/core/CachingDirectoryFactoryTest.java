  Merged /lucene/dev/trunk/lucene/BUILD.txt:r1457292
  Merged /lucene/dev/trunk/lucene/suggest:r1457292
  Merged /lucene/dev/trunk/lucene/analysis/icu/src/java/org/apache/lucene/collation/ICUCollationKeyFilterFactory.java:r1457292
  Merged /lucene/dev/trunk/lucene/analysis:r1457292
  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1457292
  Merged /lucene/dev/trunk/lucene/grouping:r1457292
  Merged /lucene/dev/trunk/lucene/misc:r1457292
  Merged /lucene/dev/trunk/lucene/classification/ivy.xml:r1457292
  Merged /lucene/dev/trunk/lucene/classification/src:r1457292
  Merged /lucene/dev/trunk/lucene/classification/build.xml:r1457292
  Merged /lucene/dev/trunk/lucene/classification:r1457292
  Merged /lucene/dev/trunk/lucene/highlighter:r1457292
  Merged /lucene/dev/trunk/lucene/sandbox:r1457292
  Merged /lucene/dev/trunk/lucene/NOTICE.txt:r1457292
  Merged /lucene/dev/trunk/lucene/LICENSE.txt:r1457292
  Merged /lucene/dev/trunk/lucene/codecs:r1457292
  Merged /lucene/dev/trunk/lucene/ivy-settings.xml:r1457292
  Merged /lucene/dev/trunk/lucene/SYSTEM_REQUIREMENTS.txt:r1457292
  Merged /lucene/dev/trunk/lucene/MIGRATE.txt:r1457292
  Merged /lucene/dev/trunk/lucene/test-framework:r1457292
  Merged /lucene/dev/trunk/lucene/README.txt:r1457292
  Merged /lucene/dev/trunk/lucene/queries:r1457292
  Merged /lucene/dev/trunk/lucene/module-build.xml:r1457292
  Merged /lucene/dev/trunk/lucene/queryparser:r1457292
  Merged /lucene/dev/trunk/lucene/facet:r1457292
  Merged /lucene/dev/trunk/lucene/common-build.xml:r1457292
  Merged /lucene/dev/trunk/lucene/demo:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestSortDocValues.java:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestSortRandom.java:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestSort.java:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestTopFieldCollector.java:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/search/TestTotalHitCountCollector.java:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.nocfs.zip:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.cfs.zip:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.nocfs.zip:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.cfs.zip:r1457292
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/TestBackwardsCompatibility.java:r1457292
  Merged /lucene/dev/trunk/lucene/core:r1457292
  Merged /lucene/dev/trunk/lucene/benchmark:r1457292
  Merged /lucene/dev/trunk/lucene/spatial:r1457292
  Merged /lucene/dev/trunk/lucene/build.xml:r1457292
  Merged /lucene/dev/trunk/lucene/join:r1457292
  Merged /lucene/dev/trunk/lucene/tools:r1457292
  Merged /lucene/dev/trunk/lucene/backwards:r1457292
  Merged /lucene/dev/trunk/lucene/site:r1457292
  Merged /lucene/dev/trunk/lucene/licenses:r1457292
  Merged /lucene/dev/trunk/lucene/memory:r1457292
  Merged /lucene/dev/trunk/lucene/JRE_VERSION_MIGRATION.txt:r1457292
  Merged /lucene/dev/trunk/lucene:r1457292
  Merged /lucene/dev/trunk/dev-tools:r1457292
  Merged /lucene/dev/trunk/solr/scripts:r1457292
  Merged /lucene/dev/trunk/solr/core/src/test/org/apache/solr/core/TestConfig.java:r1457292
package org.apache.solr.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.store.Directory;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.core.DirectoryFactory.DirContext;
import org.junit.Test;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class CachingDirectoryFactoryTest extends SolrTestCaseJ4 {
  private Map<String,Tracker> dirs = new HashMap<String,Tracker>();
  private List<Tracker> oldDirs = new ArrayList<Tracker>();
  private volatile boolean stop = false;
  
  private class Tracker {
    String path;
    AtomicInteger refCnt = new AtomicInteger(0);
    Directory dir;
  }
  
  @Test
  public void stressTest() throws Exception {
    final CachingDirectoryFactory df = new RAMDirectoryFactory();
    
    List<Thread> threads = new ArrayList<Thread>();
    int threadCount = 3;
    for (int i = 0; i < threadCount; i++) {
      Thread getDirThread = new GetDirThread(df);
      threads.add(getDirThread);
      getDirThread.start();
    }
    
    for (int i = 0; i < 2; i++) {
      Thread releaseDirThread = new ReleaseDirThread(df);
      threads.add(releaseDirThread);
      releaseDirThread.start();
    }
    
    for (int i = 0; i < 1; i++) {
      Thread incRefThread = new IncRefThread(df);
      threads.add(incRefThread);
      incRefThread.start();
    }

    Thread.sleep(TEST_NIGHTLY ? 30000 : 8000);
    
    stop = true;
    
    for (Thread thread : threads) {
      thread.join();
    }
    
    // do any remaining releases
    synchronized (dirs) {
      int sz = dirs.size();
      if (sz > 0) {
        for (Tracker tracker : dirs.values()) {
          int cnt = tracker.refCnt.get();
          for (int i = 0; i < cnt; i++) {
            tracker.refCnt.decrementAndGet();
            df.release(tracker.dir);
          }
        }
      }
      sz = oldDirs.size();
      if (sz > 0) {
        for (Tracker tracker : oldDirs) {
          int cnt = tracker.refCnt.get();
          for (int i = 0; i < cnt; i++) {
            tracker.refCnt.decrementAndGet();
            df.release(tracker.dir);
          }
        }
      }
      
    }
    
    df.close();
  }
  
  private class ReleaseDirThread extends Thread {
    Random random;
    private CachingDirectoryFactory df;
    
    public ReleaseDirThread(CachingDirectoryFactory df) {
      this.df = df;
    }
    
    @Override
    public void run() {
      random = random();
      while (!stop) {
        try {
          Thread.sleep(random.nextInt(50) + 1);
        } catch (InterruptedException e1) {
          throw new RuntimeException(e1);
        }
        try {
          synchronized (dirs) {
            int sz = dirs.size();
            List<Tracker> dirsList = new ArrayList<Tracker>();
            dirsList.addAll(dirs.values());
            if (sz > 0) {
              Tracker tracker = dirsList.get(Math.min(dirsList.size() - 1,
                  random.nextInt(sz + 1)));
              if (tracker.refCnt.get() > 0) {
                if (random.nextBoolean()) {
                  df.doneWithDirectory(tracker.dir);
                }
                tracker.refCnt.decrementAndGet();
                df.release(tracker.dir);
              }
            }
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
  
  private class GetDirThread extends Thread {
    Random random;
    private CachingDirectoryFactory df;
    
    public GetDirThread(CachingDirectoryFactory df) {
      this.df = df;
    }
    
    @Override
    public void run() {
      random = random();
      while (!stop) {
        try {
          Thread.sleep(random.nextInt(50) + 1);
        } catch (InterruptedException e1) {
          throw new RuntimeException(e1);
        }
        try {
          String path = "path" + random.nextInt(20);
          synchronized (dirs) {
            Tracker tracker = dirs.get(path);
            if (tracker == null) {
              tracker = new Tracker();
              tracker.path = path;
              tracker.dir = df.get(path, DirContext.DEFAULT, null);
              dirs.put(path, tracker);
            } else {
              if (random.nextInt(10) > 6) {
                Tracker oldTracker = new Tracker();
                oldTracker.refCnt = new AtomicInteger(tracker.refCnt.get());
                oldTracker.path = tracker.path;
                oldTracker.dir = tracker.dir;
                oldDirs.add(oldTracker);
                
                tracker.dir = df.get(path, DirContext.DEFAULT, null, true);
                tracker.refCnt = new AtomicInteger(0);
              } else {
                tracker.dir = df.get(path, DirContext.DEFAULT, null);
              }
            }
            tracker.refCnt.incrementAndGet();
          }
          
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
  
  private class IncRefThread extends Thread {
    Random random;
    private CachingDirectoryFactory df;
    
    public IncRefThread(CachingDirectoryFactory df) {
      this.df = df;
    }
    
    @Override
    public void run() {
      random = random();
      while (!stop) {
        try {
          Thread.sleep(random.nextInt(300) + 1);
        } catch (InterruptedException e1) {
          throw new RuntimeException(e1);
        }
        
        String path = "path" + random.nextInt(20);
        synchronized (dirs) {
          Tracker tracker = dirs.get(path);
          
          if (tracker != null && tracker.refCnt.get() > 0) {
            tracker.refCnt.incrementAndGet();
            df.incRef(tracker.dir);
          }
        }
        
      }
    }
  }
  
}
