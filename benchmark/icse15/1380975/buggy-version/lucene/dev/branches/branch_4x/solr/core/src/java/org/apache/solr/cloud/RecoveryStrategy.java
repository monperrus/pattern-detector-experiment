package org.apache.solr.cloud;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest.WaitForState;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.cloud.ClosableThread;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.cloud.ZooKeeperException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.RequestHandlers.LazyRequestHandlerWrapper;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.ReplicationHandler;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.PeerSync;
import org.apache.solr.update.UpdateLog;
import org.apache.solr.update.UpdateLog.RecoveryInfo;
import org.apache.solr.update.processor.DistributedUpdateProcessor;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecoveryStrategy extends Thread implements ClosableThread {
  private static final int MAX_RETRIES = 500;
  private static final int INTERRUPTED = MAX_RETRIES + 1;
  private static final int STARTING_RECOVERY_DELAY = 1000;
  
  private static final String REPLICATION_HANDLER = "/replication";

  private static Logger log = LoggerFactory.getLogger(RecoveryStrategy.class);

  public static interface RecoveryListener {
    public void recovered();
    public void failed();
  }
  
  private volatile boolean close = false;

  private RecoveryListener recoveryListener;
  private ZkController zkController;
  private String baseUrl;
  private String coreZkNodeName;
  private ZkStateReader zkStateReader;
  private volatile String coreName;
  private int retries;
  private boolean recoveringAfterStartup;
  private CoreContainer cc;
  
  public RecoveryStrategy(CoreContainer cc, String name, RecoveryListener recoveryListener) {
    this.cc = cc;
    this.coreName = name;
    this.recoveryListener = recoveryListener;
    setName("RecoveryThread");
    zkController = cc.getZkController();
    zkStateReader = zkController.getZkStateReader();
    baseUrl = zkController.getBaseUrl();
    coreZkNodeName = zkController.getNodeName() + "_" + coreName;
  }

  public void setRecoveringAfterStartup(boolean recoveringAfterStartup) {
    this.recoveringAfterStartup = recoveringAfterStartup;
  }

  // make sure any threads stop retrying
  public void close() {
    close = true;
    log.warn("Stopping recovery for zkNodeName=" + coreZkNodeName + "core=" + coreName );
  }

  
  private void recoveryFailed(final SolrCore core,
      final ZkController zkController, final String baseUrl,
      final String shardZkNodeName, final CoreDescriptor cd) throws KeeperException, InterruptedException {
    SolrException.log(log, "Recovery failed - I give up. core=" + coreName);
    try {
      zkController.publish(cd, ZkStateReader.RECOVERY_FAILED);
    } finally {
      close();
      recoveryListener.failed();
    }
  }
  
  private void replicate(String nodeName, SolrCore core, ZkNodeProps leaderprops, String baseUrl)
      throws SolrServerException, IOException {
   
    String leaderBaseUrl = leaderprops.get(ZkStateReader.BASE_URL_PROP);
    ZkCoreNodeProps leaderCNodeProps = new ZkCoreNodeProps(leaderprops);
    String leaderUrl = leaderCNodeProps.getCoreUrl();
    
    log.info("Attempting to replicate from " + leaderUrl + ". core=" + coreName);
    
    // if we are the leader, either we are trying to recover faster
    // then our ephemeral timed out or we are the only node
    if (!leaderBaseUrl.equals(baseUrl)) {
      // send commit
      commitOnLeader(leaderUrl);
      
      // use rep handler directly, so we can do this sync rather than async
      SolrRequestHandler handler = core.getRequestHandler(REPLICATION_HANDLER);
      if (handler instanceof LazyRequestHandlerWrapper) {
        handler = ((LazyRequestHandlerWrapper)handler).getWrappedHandler();
      }
      ReplicationHandler replicationHandler = (ReplicationHandler) handler;
      
      if (replicationHandler == null) {
        throw new SolrException(ErrorCode.SERVICE_UNAVAILABLE,
            "Skipping recovery, no " + REPLICATION_HANDLER + " handler found");
      }
      
      ModifiableSolrParams solrParams = new ModifiableSolrParams();
      solrParams.set(ReplicationHandler.MASTER_URL, leaderUrl);
      
      if (isClosed()) retries = INTERRUPTED;
      boolean success = replicationHandler.doFetch(solrParams, true); // TODO: look into making force=true not download files we already have?

      if (!success) {
        throw new SolrException(ErrorCode.SERVER_ERROR, "Replication for recovery failed.");
      }
      
      // solrcloud_debug
//      try {
//        RefCounted<SolrIndexSearcher> searchHolder = core.getNewestSearcher(false);
//        SolrIndexSearcher searcher = searchHolder.get();
//        try {
//          System.out.println(core.getCoreDescriptor().getCoreContainer().getZkController().getNodeName() + " replicated "
//              + searcher.search(new MatchAllDocsQuery(), 1).totalHits + " from " + leaderUrl + " gen:" + core.getDeletionPolicy().getLatestCommit().getGeneration() + " data:" + core.getDataDir());
//        } finally {
//          searchHolder.decref();
//        }
//      } catch (Exception e) {
//        
//      }
    }
  }

  private void commitOnLeader(String leaderUrl) throws SolrServerException, IOException {
    HttpSolrServer server = new HttpSolrServer(leaderUrl);
    server.setConnectionTimeout(30000);
    server.setSoTimeout(30000);
    UpdateRequest ureq = new UpdateRequest();
    ureq.setParams(new ModifiableSolrParams());
    ureq.getParams().set(DistributedUpdateProcessor.COMMIT_END_POINT, true);
    ureq.setAction(AbstractUpdateRequest.ACTION.COMMIT, false, true).process(
        server);
    server.shutdown();
  }

  private void sendPrepRecoveryCmd(String leaderBaseUrl,
      String leaderCoreName) throws SolrServerException,
      IOException {
    HttpSolrServer server = new HttpSolrServer(leaderBaseUrl);
    server.setConnectionTimeout(45000);
    server.setSoTimeout(45000);
    WaitForState prepCmd = new WaitForState();
    prepCmd.setCoreName(leaderCoreName);
    prepCmd.setNodeName(zkController.getNodeName());
    prepCmd.setCoreNodeName(coreZkNodeName);
    prepCmd.setState(ZkStateReader.RECOVERING);
    prepCmd.setCheckLive(true);
    prepCmd.setOnlyIfLeader(true);
    prepCmd.setPauseFor(6000);
    
    server.request(prepCmd);
    server.shutdown();
  }

  @Override
  public void run() {
    SolrCore core = cc.getCore(coreName);
    if (core == null) {
      SolrException.log(log, "SolrCore not found - cannot recover:" + coreName);
      return;
    }

    // set request info for logging
    try {
      SolrQueryRequest req = new LocalSolrQueryRequest(core, new ModifiableSolrParams());
      SolrQueryResponse rsp = new SolrQueryResponse();
      SolrRequestInfo.setRequestInfo(new SolrRequestInfo(req, rsp));

      log.info("Starting recovery process.  core=" + coreName + " recoveringAfterStartup=" + recoveringAfterStartup);

      try {
        doRecovery(core);
      }  catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        SolrException.log(log, "", e);
        throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR, "",
            e);
      } catch (Throwable t) {
        log.error("", t);
        throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
            "", t);
      }
    } finally {
      if (core != null) core.close();
      SolrRequestInfo.clearRequestInfo();
    }
  }

  // TODO: perhaps make this grab a new core each time through the loop to handle core reloads?
  public void doRecovery(SolrCore core) throws KeeperException, InterruptedException {
    boolean replayed = false;
    boolean successfulRecovery = false;

    UpdateLog ulog;
    ulog = core.getUpdateHandler().getUpdateLog();
    if (ulog == null) {
      SolrException.log(log, "No UpdateLog found - cannot recover. core=" + coreName);
      recoveryFailed(core, zkController, baseUrl, coreZkNodeName,
          core.getCoreDescriptor());
      return;
    }

    boolean firstTime = true;

    List<Long> recentVersions;
    UpdateLog.RecentUpdates recentUpdates = null;
    try {
      recentUpdates = ulog.getRecentUpdates();
      recentVersions = recentUpdates.getVersions(ulog.numRecordsToKeep);
    } catch (Throwable t) {
      SolrException.log(log, "Corrupt tlog - ignoring. core=" + coreName, t);
      recentVersions = new ArrayList<Long>(0);
    } finally {
      if (recentUpdates != null) {
        recentUpdates.close();
      }
    }

    List<Long> startingVersions = ulog.getStartingVersions();

    if (startingVersions != null && recoveringAfterStartup) {
      try {
        int oldIdx = 0; // index of the start of the old list in the current
                        // list
        long firstStartingVersion = startingVersions.size() > 0 ? startingVersions
            .get(0) : 0;
        
        for (; oldIdx < recentVersions.size(); oldIdx++) {
          if (recentVersions.get(oldIdx) == firstStartingVersion) break;
        }
        
        if (oldIdx > 0) {
          log.info("####### Found new versions added after startup: num="
              + oldIdx);
          log.info("###### currentVersions=" + recentVersions);
        }
        
        log.info("###### startupVersions=" + startingVersions);
      } catch (Throwable t) {
        SolrException.log(log, "Error getting recent versions. core=" + coreName, t);
        recentVersions = new ArrayList<Long>(0);
      }
    }

    if (recoveringAfterStartup) {
      // if we're recovering after startup (i.e. we have been down), then we need to know what the last versions were
      // when we went down.  We may have received updates since then.
      recentVersions = startingVersions;
      try {
        if ((ulog.getStartingOperation() & UpdateLog.FLAG_GAP) != 0) {
          // last operation at the time of startup had the GAP flag set...
          // this means we were previously doing a full index replication
          // that probably didn't complete and buffering updates in the
          // meantime.
          log.info("Looks like a previous replication recovery did not complete - skipping peer sync. core="
              + coreName);
          firstTime = false; // skip peersync
        }
      } catch (Throwable t) {
        SolrException.log(log, "Error trying to get ulog starting operation. core="
            + coreName, t);
        firstTime = false; // skip peersync
      }
    }

    while (!successfulRecovery && !isInterrupted()) { // don't use interruption or it will close channels though
      try {
        CloudDescriptor cloudDesc = core.getCoreDescriptor()
            .getCloudDescriptor();
        ZkNodeProps leaderprops = zkStateReader.getLeaderProps(
            cloudDesc.getCollectionName(), cloudDesc.getShardId());

        String leaderBaseUrl = leaderprops.get(ZkStateReader.BASE_URL_PROP);
        String leaderCoreName = leaderprops.get(ZkStateReader.CORE_NAME_PROP);

        String leaderUrl = ZkCoreNodeProps.getCoreUrl(leaderBaseUrl, leaderCoreName);

        String ourUrl = ZkCoreNodeProps.getCoreUrl(baseUrl, coreName);

        boolean isLeader = leaderUrl.equals(ourUrl);
        if (isLeader && !cloudDesc.isLeader) {
          throw new SolrException(ErrorCode.SERVER_ERROR, "Cloud state still says we are leader.");
        }
        if (cloudDesc.isLeader) {
          // we are now the leader - no one else must have been suitable
          log.warn("We have not yet recovered - but we are now the leader! core=" + coreName);
          log.info("Finished recovery process. core=" + coreName);
          zkController.publish(core.getCoreDescriptor(), ZkStateReader.ACTIVE);
          return;
        }
        
        zkController.publish(core.getCoreDescriptor(), ZkStateReader.RECOVERING);
        
        sendPrepRecoveryCmd(leaderBaseUrl, leaderCoreName);


        // first thing we just try to sync
        if (firstTime) {
          firstTime = false; // only try sync the first time through the loop
          log.info("Attempting to PeerSync from " + leaderUrl + " core=" + coreName + " - recoveringAfterStartup="+recoveringAfterStartup);
          // System.out.println("Attempting to PeerSync from " + leaderUrl
          // + " i am:" + zkController.getNodeName());
          PeerSync peerSync = new PeerSync(core,
              Collections.singletonList(leaderUrl), ulog.numRecordsToKeep);
          peerSync.setStartingVersions(recentVersions);
          boolean syncSuccess = peerSync.sync();
          if (syncSuccess) {
            SolrQueryRequest req = new LocalSolrQueryRequest(core,
                new ModifiableSolrParams());
            core.getUpdateHandler().commit(new CommitUpdateCommand(req, false));
            log.info("PeerSync Recovery was successful - registering as Active. core=" + coreName);

            // solrcloud_debug
            // try {
            // RefCounted<SolrIndexSearcher> searchHolder =
            // core.getNewestSearcher(false);
            // SolrIndexSearcher searcher = searchHolder.get();
            // try {
            // System.out.println(core.getCoreDescriptor().getCoreContainer().getZkController().getNodeName()
            // + " synched "
            // + searcher.search(new MatchAllDocsQuery(), 1).totalHits);
            // } finally {
            // searchHolder.decref();
            // }
            // } catch (Exception e) {
            //
            // }

            // sync success - register as active and return
            zkController.publish(core.getCoreDescriptor(),
                ZkStateReader.ACTIVE);
            successfulRecovery = true;
            close = true;
            return;
          }

          log.info("PeerSync Recovery was not successful - trying replication. core=" + coreName);
        }
        //System.out.println("Sync Recovery was not successful - trying replication");
        log.info("Starting Replication Recovery. core=" + coreName);
        log.info("Begin buffering updates. core=" + coreName);
        ulog.bufferUpdates();
        replayed = false;
        
//        // open a new IndexWriter - we don't want any background merges ongoing
//        // also ensures something like NRTCachingDirectory is flushed
//        boolean forceNewIndexDir = false;
//        try {
//          core.getUpdateHandler().newIndexWriter(false);
//        } catch (Throwable t) {
//          SolrException.log(log, "Could not read the current index - replicating to a new directory", t);
//          // something is wrong with the index
//          // we need to force using a new index directory
//          forceNewIndexDir = true;
//        }
//        
        try {

          replicate(zkController.getNodeName(), core,
              leaderprops, leaderUrl);

          replay(ulog);
          replayed = true;

          log.info("Replication Recovery was successful - registering as Active. core=" + coreName);
          // if there are pending recovery requests, don't advert as active
          zkController.publish(core.getCoreDescriptor(), ZkStateReader.ACTIVE);
          close = true;
          successfulRecovery = true;
          recoveryListener.recovered();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Recovery was interrupted", e);
          retries = INTERRUPTED;
        } catch (Throwable t) {
          SolrException.log(log, "Error while trying to recover", t);
        } finally {
          if (!replayed) {
            try {
              ulog.dropBufferedUpdates();
            } catch (Throwable t) {
              SolrException.log(log, "", t);
            }
          }

        }

      } catch (Throwable t) {
        SolrException.log(log, "Error while trying to recover. core=" + coreName, t);
      }

      if (!successfulRecovery) {
        // lets pause for a moment and we need to try again...
        // TODO: we don't want to retry for some problems?
        // Or do a fall off retry...
        try {

          log.error("Recovery failed - trying again... core=" + coreName);
          
          if (isClosed()) {
            retries = INTERRUPTED;
          }
          
          retries++;
          if (retries >= MAX_RETRIES) {
            if (retries == INTERRUPTED) {
              SolrException.log(log, "Recovery failed - interrupted. core=" + coreName);
              recoveryFailed(core, zkController, baseUrl, coreZkNodeName,
                  core.getCoreDescriptor());
            } else {
              SolrException.log(log, "Recovery failed - max retries exceeded. core=" + coreName);
              recoveryFailed(core, zkController, baseUrl, coreZkNodeName,
                  core.getCoreDescriptor());
            }
            break;
          }

        } catch (Throwable e) {
          SolrException.log(log, "core=" + coreName, e);
        }

        try {
          // start at 1 sec and work up to a couple min
          double loopCount = Math.min(Math.pow(2, retries), 600); 
          for (int i = 0; i < loopCount; i++) {
            if (isClosed()) break; // check if someone closed us
            Thread.sleep(STARTING_RECOVERY_DELAY);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Recovery was interrupted. core=" + coreName, e);
          retries = INTERRUPTED;
        }
      }

    }
    log.info("Finished recovery process. core=" + coreName);

  }

  private Future<RecoveryInfo> replay(UpdateLog ulog)
      throws InterruptedException, ExecutionException {
    Future<RecoveryInfo> future = ulog.applyBufferedUpdates();
    if (future == null) {
      // no replay needed\
      log.info("No replay needed. core=" + coreName);
    } else {
      log.info("Replaying buffered documents. core=" + coreName);
      // wait for replay
      future.get();
    }
    
    // solrcloud_debug
//    try {
//      RefCounted<SolrIndexSearcher> searchHolder = core.getNewestSearcher(false);
//      SolrIndexSearcher searcher = searchHolder.get();
//      try {
//        System.out.println(core.getCoreDescriptor().getCoreContainer().getZkController().getNodeName() + " replayed "
//            + searcher.search(new MatchAllDocsQuery(), 1).totalHits);
//      } finally {
//        searchHolder.decref();
//      }
//    } catch (Exception e) {
//      
//    }
    
    return future;
  }

  public boolean isClosed() {
    return close;
  }

}
