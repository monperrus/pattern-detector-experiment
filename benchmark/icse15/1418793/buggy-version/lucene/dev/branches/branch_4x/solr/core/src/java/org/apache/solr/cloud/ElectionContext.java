  Merged /lucene/dev/trunk/lucene/suggest:r1418790
  Merged /lucene/dev/trunk/lucene/analysis/icu/src/java/org/apache/lucene/collation/ICUCollationKeyFilterFactory.java:r1418790
  Merged /lucene/dev/trunk/lucene/analysis:r1418790
  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1418790
  Merged /lucene/dev/trunk/lucene/grouping:r1418790
  Merged /lucene/dev/trunk/lucene/misc:r1418790
  Merged /lucene/dev/trunk/lucene/sandbox:r1418790
  Merged /lucene/dev/trunk/lucene/highlighter:r1418790
  Merged /lucene/dev/trunk/lucene/NOTICE.txt:r1418790
  Merged /lucene/dev/trunk/lucene/LICENSE.txt:r1418790
  Merged /lucene/dev/trunk/lucene/codecs:r1418790
  Merged /lucene/dev/trunk/lucene/ivy-settings.xml:r1418790
  Merged /lucene/dev/trunk/lucene/SYSTEM_REQUIREMENTS.txt:r1418790
  Merged /lucene/dev/trunk/lucene/MIGRATE.txt:r1418790
  Merged /lucene/dev/trunk/lucene/test-framework:r1418790
  Merged /lucene/dev/trunk/lucene/README.txt:r1418790
  Merged /lucene/dev/trunk/lucene/queries:r1418790
  Merged /lucene/dev/trunk/lucene/module-build.xml:r1418790
  Merged /lucene/dev/trunk/lucene/facet:r1418790
  Merged /lucene/dev/trunk/lucene/queryparser:r1418790
  Merged /lucene/dev/trunk/lucene/demo:r1418790
  Merged /lucene/dev/trunk/lucene/common-build.xml:r1418790
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.cfs.zip:r1418790
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/TestBackwardsCompatibility.java:r1418790
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.cfs.zip:r1418790
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.nocfs.zip:r1418790
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.nocfs.zip:r1418790
  Merged /lucene/dev/trunk/lucene/core:r1418790
  Merged /lucene/dev/trunk/lucene/benchmark:r1418790
  Merged /lucene/dev/trunk/lucene/spatial:r1418790
  Merged /lucene/dev/trunk/lucene/build.xml:r1418790
  Merged /lucene/dev/trunk/lucene/join:r1418790
  Merged /lucene/dev/trunk/lucene/tools:r1418790
  Merged /lucene/dev/trunk/lucene/backwards:r1418790
  Merged /lucene/dev/trunk/lucene/site:r1418790
  Merged /lucene/dev/trunk/lucene/licenses:r1418790
  Merged /lucene/dev/trunk/lucene/memory:r1418790
  Merged /lucene/dev/trunk/lucene/JRE_VERSION_MIGRATION.txt:r1418790
  Merged /lucene/dev/trunk/lucene/BUILD.txt:r1418790
  Merged /lucene/dev/trunk/lucene:r1418790
  Merged /lucene/dev/trunk/dev-tools:r1418790
  Merged /lucene/dev/trunk/solr/test-framework:r1418790
  Merged /lucene/dev/trunk/solr/README.txt:r1418790
  Merged /lucene/dev/trunk/solr/webapp:r1418790
  Merged /lucene/dev/trunk/solr/testlogging.properties:r1418790
  Merged /lucene/dev/trunk/solr/cloud-dev:r1418790
  Merged /lucene/dev/trunk/solr/common-build.xml:r1418790
  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1418790
  Merged /lucene/dev/trunk/solr/scripts:r1418790
package org.apache.solr.cloud;

import java.io.IOException;
import java.util.Map;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.update.UpdateLog;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public abstract class ElectionContext {
  private static Logger log = LoggerFactory.getLogger(ElectionContext.class);
  final String electionPath;
  final ZkNodeProps leaderProps;
  final String id;
  final String leaderPath;
  String leaderSeqPath;
  private SolrZkClient zkClient;
  
  public ElectionContext(final String shardZkNodeName,
      final String electionPath, final String leaderPath, final ZkNodeProps leaderProps, final SolrZkClient zkClient) {
    this.id = shardZkNodeName;
    this.electionPath = electionPath;
    this.leaderPath = leaderPath;
    this.leaderProps = leaderProps;
    this.zkClient = zkClient;
  }
  
  public void close() {}
  
  public void cancelElection() throws InterruptedException, KeeperException {
    try {
      zkClient.delete(leaderSeqPath, -1, true);
    } catch (NoNodeException e) {
      // fine
      log.warn("cancelElection did not find election node to remove");
    }
  }

  abstract void runLeaderProcess(boolean weAreReplacement) throws KeeperException, InterruptedException, IOException;
}

class ShardLeaderElectionContextBase extends ElectionContext {
  private static Logger log = LoggerFactory.getLogger(ShardLeaderElectionContextBase.class);
  protected final SolrZkClient zkClient;
  protected String shardId;
  protected String collection;
  protected LeaderElector leaderElector;

  public ShardLeaderElectionContextBase(LeaderElector leaderElector, final String shardId,
      final String collection, final String shardZkNodeName, ZkNodeProps props, ZkStateReader zkStateReader) {
    super(shardZkNodeName, ZkStateReader.COLLECTIONS_ZKNODE + "/" + collection + "/leader_elect/"
        + shardId, ZkStateReader.getShardLeadersPath(collection, shardId),
        props, zkStateReader.getZkClient());
    this.leaderElector = leaderElector;
    this.zkClient = zkStateReader.getZkClient();
    this.shardId = shardId;
    this.collection = collection;
  }

  @Override
  void runLeaderProcess(boolean weAreReplacement) throws KeeperException,
      InterruptedException, IOException {
    
    zkClient.makePath(leaderPath, ZkStateReader.toJSON(leaderProps),
        CreateMode.EPHEMERAL, true);
    
    ZkNodeProps m = ZkNodeProps.fromKeyVals(Overseer.QUEUE_OPERATION, "leader",
        ZkStateReader.SHARD_ID_PROP, shardId, ZkStateReader.COLLECTION_PROP,
        collection, ZkStateReader.BASE_URL_PROP, leaderProps.getProperties()
            .get(ZkStateReader.BASE_URL_PROP), ZkStateReader.CORE_NAME_PROP,
        leaderProps.getProperties().get(ZkStateReader.CORE_NAME_PROP),
        ZkStateReader.STATE_PROP, ZkStateReader.ACTIVE);
    Overseer.getInQueue(zkClient).offer(ZkStateReader.toJSON(m));
    
  }

}

// add core container and stop passing core around...
final class ShardLeaderElectionContext extends ShardLeaderElectionContextBase {
  private static Logger log = LoggerFactory.getLogger(ShardLeaderElectionContext.class);
  
  private ZkController zkController;
  private CoreContainer cc;
  private SyncStrategy syncStrategy = new SyncStrategy();

  private volatile boolean isClosed = false;
  
  public ShardLeaderElectionContext(LeaderElector leaderElector, 
      final String shardId, final String collection,
      final String shardZkNodeName, ZkNodeProps props, ZkController zkController, CoreContainer cc) {
    super(leaderElector, shardId, collection, shardZkNodeName, props,
        zkController.getZkStateReader());
    this.zkController = zkController;
    this.cc = cc;
  }
  
  @Override
  public void close() {
    this.isClosed  = true;
    syncStrategy.close();
  }
  
  @Override
  void runLeaderProcess(boolean weAreReplacement) throws KeeperException,
      InterruptedException, IOException {
    log.info("Running the leader process.");
    
    String coreName = leaderProps.getStr(ZkStateReader.CORE_NAME_PROP);
    
    // clear the leader in clusterstate
    ZkNodeProps m = new ZkNodeProps(Overseer.QUEUE_OPERATION, "leader",
        ZkStateReader.SHARD_ID_PROP, shardId, ZkStateReader.COLLECTION_PROP,
        collection);
    Overseer.getInQueue(zkClient).offer(ZkStateReader.toJSON(m));
    
    String leaderVoteWait = cc.getZkController().getLeaderVoteWait();
    if (!weAreReplacement && leaderVoteWait != null) {
      waitForReplicasToComeUp(weAreReplacement, leaderVoteWait);
    }
    
    SolrCore core = null;
    try {
      
      core = cc.getCore(coreName);
      
      if (core == null) {
        cancelElection();
        throw new SolrException(ErrorCode.SERVER_ERROR,
            "Fatal Error, SolrCore not found:" + coreName + " in "
                + cc.getCoreNames());
      }
      
      // should I be leader?
      if (weAreReplacement && !shouldIBeLeader(leaderProps, core)) {
        rejoinLeaderElection(leaderSeqPath, core);
        return;
      }
      
      log.info("I may be the new leader - try and sync");
      
      UpdateLog ulog = core.getUpdateHandler().getUpdateLog();
 
      
      // we are going to attempt to be the leader
      // first cancel any current recovery
      core.getUpdateHandler().getSolrCoreState().cancelRecovery();
      boolean success = false;
      try {
        success = syncStrategy.sync(zkController, core, leaderProps);
      } catch (Throwable t) {
        SolrException.log(log, "Exception while trying to sync", t);
        success = false;
      }
      
      if (!success && ulog.getRecentUpdates().getVersions(1).isEmpty()) {
        // we failed sync, but we have no versions - we can't sync in that case
        // - we were active
        // before, so become leader anyway
        log.info("We failed sync, but we have no versions - we can't sync in that case - we were active before, so become leader anyway");
        success = true;
      }
      
      // if !success but no one else is in active mode,
      // we are the leader anyway
      // TODO: should we also be leader if there is only one other active?
      // if we couldn't sync with it, it shouldn't be able to sync with us
      // TODO: this needs to be moved to the election context - the logic does
      // not belong here.
      if (!success
          && !areAnyOtherReplicasActive(zkController, leaderProps, collection,
              shardId)) {
        log.info("Sync was not a success but no one else is active! I am the leader");
        success = true;
      }
      
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
      if (!success) {
        rejoinLeaderElection(leaderSeqPath, core);
        return;
      }

      log.info("I am the new leader: "
          + ZkCoreNodeProps.getCoreUrl(leaderProps));
      core.getCoreDescriptor().getCloudDescriptor().isLeader = true;
    } finally {
      if (core != null) {
        core.close();
      }
    }
    
    try {
      super.runLeaderProcess(weAreReplacement);
    } catch (Throwable t) {
      try {
        core = cc.getCore(coreName);
        if (core == null) {
          cancelElection();
          throw new SolrException(ErrorCode.SERVER_ERROR,
              "Fatal Error, SolrCore not found:" + coreName + " in "
                  + cc.getCoreNames());
        }
        
        core.getCoreDescriptor().getCloudDescriptor().isLeader = false;
        
        // we could not publish ourselves as leader - rejoin election
        rejoinLeaderElection(leaderSeqPath, core);
      } finally {
        if (core != null) {
          core.close();
        }
      }
    }
    
  }
  
  private boolean areAnyOtherReplicasActive(ZkController zkController,
      ZkNodeProps leaderProps, String collection, String shardId) {
    ClusterState clusterState = zkController.getZkStateReader()
        .getClusterState();
    Map<String,Slice> slices = clusterState.getSlices(collection);
    Slice slice = slices.get(shardId);
    Map<String,Replica> replicasMap = slice.getReplicasMap();
    for (Map.Entry<String,Replica> shard : replicasMap.entrySet()) {
      String state = shard.getValue().getStr(ZkStateReader.STATE_PROP);
      // System.out.println("state:"
      // + state
      // + shard.getValue().get(ZkStateReader.NODE_NAME_PROP)
      // + " live: "
      // + clusterState.liveNodesContain(shard.getValue().get(
      // ZkStateReader.NODE_NAME_PROP)));
      if (state.equals(ZkStateReader.ACTIVE)
          && clusterState.liveNodesContain(shard.getValue().getStr(
              ZkStateReader.NODE_NAME_PROP))
          && !new ZkCoreNodeProps(shard.getValue()).getCoreUrl().equals(
              new ZkCoreNodeProps(leaderProps).getCoreUrl())) {
        return true;
      }
    }
    
    return false;
  }

  private void waitForReplicasToComeUp(boolean weAreReplacement,
      String leaderVoteWait) throws InterruptedException {
    int timeout = Integer.parseInt(leaderVoteWait);
    long timeoutAt = System.currentTimeMillis() + timeout;
    final String shardsElectZkPath = electionPath + LeaderElector.ELECTION_NODE;
    
    Slice slices = zkController.getClusterState().getSlice(collection, shardId);
    int cnt = 0;
    while (true && !isClosed) {
      // wait for everyone to be up
      if (slices != null) {
        int found = 0;
        try {
          found = zkClient.getChildren(shardsElectZkPath, null, true).size();
        } catch (KeeperException e) {
          SolrException.log(log,
              "Errir checking for the number of election participants", e);
        }
        
        // on startup and after connection timeout, wait for all known shards
        if (found >= slices.getReplicasMap().size()) {
          log.info("Enough replicas found to continue.");
          return;
        } else {
          if (cnt % 40 == 0) {
            log.info("Waiting until we see more replicas up: total="
              + slices.getReplicasMap().size() + " found=" + found
              + " timeoutin=" + (timeoutAt - System.currentTimeMillis()));
          }
        }
        
        if (System.currentTimeMillis() > timeoutAt) {
          log.info("Was waiting for replicas to come up, but they are taking too long - assuming they won't come back till later");
          return;
        }
      }
      
      Thread.sleep(500);
      slices = zkController.getClusterState().getSlice(collection, shardId);
      cnt++;
    }
  }

  private void rejoinLeaderElection(String leaderSeqPath, SolrCore core)
      throws InterruptedException, KeeperException, IOException {
    // remove our ephemeral and re join the election
    if (cc.isShutDown()) {
      log.info("Not rejoining election because CoreContainer is shutdown");
      return;
    }
    
    log.info("There may be a better leader candidate than us - going back into recovery");
    
    cancelElection();
    
    try {
      core.getUpdateHandler().getSolrCoreState().doRecovery(cc, core.getName());
    } catch (Throwable t) {
      SolrException.log(log, "Error trying to start recovery", t);
    }
    
    leaderElector.joinElection(this, true);
  }

  private boolean shouldIBeLeader(ZkNodeProps leaderProps, SolrCore core) {
    log.info("Checking if I should try and be the leader.");
    
    if (isClosed) {
      log.info("Bailing on leader process because we have been closed");
      return false;
    }
    
    if (core.getCoreDescriptor().getCloudDescriptor().getLastPublished()
        .equals(ZkStateReader.ACTIVE)) {
      log.info("My last published State was Active, it's okay to be the leader.");
      return true;
    }
    log.info("My last published State was "
        + core.getCoreDescriptor().getCloudDescriptor().getLastPublished()
        + ", I won't be the leader.");
    // TODO: and if no one is a good candidate?
    
    return false;
  }
  
}

final class OverseerElectionContext extends ElectionContext {
  
  private final SolrZkClient zkClient;
  private Overseer overseer;


  public OverseerElectionContext(SolrZkClient zkClient, Overseer overseer, final String zkNodeName) {
    super(zkNodeName, "/overseer_elect", "/overseer_elect/leader", null, zkClient);
    this.overseer = overseer;
    this.zkClient = zkClient;
  }

  @Override
  void runLeaderProcess(boolean weAreReplacement) throws KeeperException,
      InterruptedException {
    
    final String id = leaderSeqPath
        .substring(leaderSeqPath.lastIndexOf("/") + 1);
    ZkNodeProps myProps = new ZkNodeProps("id", id);
    
    zkClient.makePath(leaderPath, ZkStateReader.toJSON(myProps),
        CreateMode.EPHEMERAL, true);
    
    overseer.start(id);
  }
  
}
