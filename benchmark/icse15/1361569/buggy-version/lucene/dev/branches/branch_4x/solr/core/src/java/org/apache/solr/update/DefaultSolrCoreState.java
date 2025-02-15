package org.apache.solr.update;

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

import java.io.IOException;

import org.apache.lucene.index.IndexWriter;
import org.apache.solr.cloud.RecoveryStrategy;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.DirectoryFactory;
import org.apache.solr.core.SolrCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultSolrCoreState extends SolrCoreState {
  public static Logger log = LoggerFactory.getLogger(DefaultSolrCoreState.class);
  
  private final boolean SKIP_AUTO_RECOVERY = Boolean.getBoolean("solrcloud.skip.autorecovery");
  
  private final Object recoveryLock = new Object();
  private int refCnt = 1;
  private SolrIndexWriter indexWriter = null;
  private DirectoryFactory directoryFactory;

  private boolean recoveryRunning;
  private RecoveryStrategy recoveryStrat;
  private boolean closed = false;
  
  public DefaultSolrCoreState(DirectoryFactory directoryFactory) {
    this.directoryFactory = directoryFactory;
  }
  
  @Override
  public synchronized IndexWriter getIndexWriter(SolrCore core) throws IOException {
    if (indexWriter == null) {
      indexWriter = createMainIndexWriter(core, "DirectUpdateHandler2", false, false);
    }
    return indexWriter;
  }

  @Override
  public synchronized void newIndexWriter(SolrCore core) throws IOException {
    if (indexWriter != null) {
      indexWriter.close();
    }
    indexWriter = createMainIndexWriter(core, "DirectUpdateHandler2",
        false, true);
  }

  @Override
  public  void decref(IndexWriterCloser closer) {
    synchronized (this) {
      refCnt--;
      if (refCnt == 0) {
        try {
          if (closer != null) {
            closer.closeWriter(indexWriter);
          } else if (indexWriter != null) {
            indexWriter.close();
          }
        } catch (Throwable t) {          
          log.error("Error during shutdown of writer.", t);
        }
        try {
          directoryFactory.close();
        } catch (Throwable t) {
          log.error("Error during shutdown of directory factory.", t);
        }
        
        // TODO: we cannot cancel recovery here if its a CoreContainer shutdown
        // it can cause deadlock - but perhaps we want to if we are stopping early
        // and CoreContainer is not being shutdown?

        closed = true;
      }
    }
  }

  @Override
  public synchronized void incref() {
    if (refCnt == 0) {
      throw new IllegalStateException("IndexWriter has been closed");
    }
    refCnt++;
  }

  @Override
  public synchronized void rollbackIndexWriter(SolrCore core) throws IOException {
    indexWriter.rollback();
    newIndexWriter(core);
  }
  
  protected SolrIndexWriter createMainIndexWriter(SolrCore core, String name,
      boolean removeAllExisting, boolean forceNewDirectory) throws IOException {
    return new SolrIndexWriter(name, core.getNewIndexDir(),
        core.getDirectoryFactory(), removeAllExisting, core.getSchema(),
        core.getSolrConfig().indexConfig, core.getDeletionPolicy(), core.getCodec(), forceNewDirectory);
  }

  @Override
  public DirectoryFactory getDirectoryFactory() {
    return directoryFactory;
  }

  @Override
  public void doRecovery(CoreContainer cc, String name) {
    if (SKIP_AUTO_RECOVERY) {
      log.warn("Skipping recovery according to sys prop solrcloud.skip.autorecovery");
      return;
    }
    
    if (cc.isShutDown()) {
      log.warn("Skipping recovery because Solr is shutdown");
      return;
    }
    
    cancelRecovery();
    synchronized (recoveryLock) {
      while (recoveryRunning) {
        try {
          recoveryLock.wait(1000);
        } catch (InterruptedException e) {

        }
        if (closed) return;
      }

      // if true, we are recovering after startup and shouldn't have (or be receiving) additional updates (except for local tlog recovery)
      boolean recoveringAfterStartup = recoveryStrat == null;

      recoveryStrat = new RecoveryStrategy(cc, name);
      recoveryStrat.setRecoveringAfterStartup(recoveringAfterStartup);
      recoveryStrat.start();
      recoveryRunning = true;
    }
    
  }
  
  @Override
  public void cancelRecovery() {
    synchronized (recoveryLock) {
      if (recoveryStrat != null) {
        recoveryStrat.close();
        try {
          recoveryStrat.join();
        } catch (InterruptedException e) {
          
        }
        
        recoveryRunning = false;
        recoveryLock.notifyAll();
      }
    }
  }
  
}
