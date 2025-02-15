package org.apache.solr.update.processor;

/**
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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest.RequestRecovery;
import org.apache.solr.cloud.CloudDescriptor;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.cloud.CloudState;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.cloud.ZooKeeperException;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.Hash;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.SolrCmdDistributor;
import org.apache.solr.update.SolrCmdDistributor.Response;
import org.apache.solr.update.SolrCmdDistributor.Node;
import org.apache.solr.update.SolrCmdDistributor.StdNode;
import org.apache.solr.update.UpdateCommand;
import org.apache.solr.update.UpdateHandler;
import org.apache.solr.update.UpdateLog;
import org.apache.solr.update.VersionBucket;
import org.apache.solr.update.VersionInfo;

// NOT mt-safe... create a new processor for each add thread
// TODO: we really should not wait for distrib after local? unless a certain replication factor is asked for
public class DistributedUpdateProcessor extends UpdateRequestProcessor {
  public static final String SEEN_LEADER = "leader";
  public static final String COMMIT_END_POINT = "commit_end_point";
  
  private final SolrQueryRequest req;
  private final SolrQueryResponse rsp;
  private final UpdateRequestProcessor next;

  private static final String VERSION_FIELD = "_version_";

  private final UpdateHandler updateHandler;
  private final UpdateLog ulog;
  private final VersionInfo vinfo;
  private final boolean versionsStored;
  private boolean returnVersions = true; // todo: default to false and make configurable

  private NamedList addsResponse = null;
  private NamedList deleteResponse = null;
  private CharsRef scratch;
  
  private final SchemaField idField;
  
  private final SolrCmdDistributor cmdDistrib;

  private boolean zkEnabled = false;

  private String collection;
  private ZkController zkController;
  
  // these are setup at the start of each request processing
  // method in this update processor
  private boolean isLeader = true;
  private boolean forwardToLeader = false;
  private List<Node> nodes;

  
  public DistributedUpdateProcessor(SolrQueryRequest req,
      SolrQueryResponse rsp, UpdateRequestProcessor next) {
    super(next);
    this.rsp = rsp;
    this.next = next;
    this.idField = req.getSchema().getUniqueKeyField();
    // version init

    this.updateHandler = req.getCore().getUpdateHandler();
    this.ulog = updateHandler.getUpdateLog();
    this.vinfo = ulog == null ? null : ulog.getVersionInfo();
    versionsStored = this.vinfo != null && this.vinfo.getVersionField() != null;
    returnVersions = versionsStored;

    // TODO: better way to get the response, or pass back info to it?
    SolrRequestInfo reqInfo = returnVersions ? SolrRequestInfo.getRequestInfo() : null;

    this.req = req;
    
    CoreDescriptor coreDesc = req.getCore().getCoreDescriptor();
    
    this.zkEnabled  = coreDesc.getCoreContainer().isZooKeeperAware();
    //this.rsp = reqInfo != null ? reqInfo.getRsp() : null;

    
    zkController = req.getCore().getCoreDescriptor().getCoreContainer().getZkController();
    
    CloudDescriptor cloudDesc = coreDesc.getCloudDescriptor();
    
    if (cloudDesc != null) {
      collection = cloudDesc.getCollectionName();
    }
    
    cmdDistrib = new SolrCmdDistributor();
  }

  private List<Node> setupRequest(int hash) {
    List<Node> nodes = null;

    // if we are in zk mode...
    if (zkEnabled) {
      // the leader is...
      // TODO: if there is no leader, wait and look again
      // TODO: we are reading the leader from zk every time - we should cache
      // this and watch for changes?? Just pull it from ZkController cluster state probably?
      String shardId = getShard(hash, collection, zkController.getCloudState()); // get the right shard based on the hash...

      try {
        // TODO: if we find out we cannot talk to zk anymore, we should probably realize we are not
        // a leader anymore - we shouldn't accept updates at all??
        ZkCoreNodeProps leaderProps = new ZkCoreNodeProps(zkController.getZkStateReader().getLeaderProps(
            collection, shardId));
        
        String leaderNodeName = leaderProps.getNodeName();
        
        String nodeName = zkController.getNodeName();
        
        isLeader = nodeName.equals(leaderNodeName);
        
        if (req.getParams().getBool(SEEN_LEADER, false)) {
          // we are coming from the leader, just go local - add no urls
          forwardToLeader = false;
        } else if (isLeader) {
          // that means I want to forward onto my replicas...
          // so get the replicas...
          forwardToLeader = false;
          nodes = getReplicaNodes(req, collection, shardId, nodeName);
        } else {
          // I need to forward onto the leader...
          nodes = new ArrayList<Node>(1);
          nodes.add(new RetryNode(leaderProps, zkController.getZkStateReader(), collection, shardId));
          forwardToLeader = true;
        }
        
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR, "",
            e);
      }
    }

    return nodes;
  }
  
  private String getShard(int hash, String collection, CloudState cloudState) {
    // ranges should be part of the cloud state and eventually gotten from zk

    // get the shard names
    return cloudState.getShard(hash, collection);
  }

  @Override
  public void processAdd(AddUpdateCommand cmd) throws IOException {
    // TODO: check for id field?
    int hash = 0;
    if (zkEnabled) {
      hash = hash(cmd);
      nodes = setupRequest(hash);
    } else {
      // even in non zk mode, tests simulate updates from a leader
      isLeader = !req.getParams().getBool(SEEN_LEADER, false);
    }
    
    boolean dropCmd = false;
    if (!forwardToLeader) {
      dropCmd = versionAdd(cmd);
    }

    if (dropCmd) {
      // TODO: do we need to add anything to the response?
      return;
    }
    
    ModifiableSolrParams params = null;
    if (nodes != null) {
      params = new ModifiableSolrParams(req.getParams());
      if (isLeader) {
        params.set(SEEN_LEADER, true);
      }
      cmdDistrib.distribAdd(cmd, nodes, params);
    }
    
    // TODO: what to do when no idField?
    if (returnVersions && rsp != null && idField != null) {
      if (addsResponse == null) {
        addsResponse = new NamedList<String>();
        rsp.add("adds",addsResponse);
      }
      if (scratch == null) scratch = new CharsRef();
      idField.getType().indexedToReadable(cmd.getIndexedId(), scratch);
      addsResponse.add(scratch.toString(), cmd.getVersion());
    }
    
    // TODO: keep track of errors?  needs to be done at a higher level though since
    // an id may fail before it gets to this processor.
    // Given that, it may also make sense to move the version reporting out of this
    // processor too.
  }
 
  // TODO: optionally fail if n replicas are not reached...
  // nocommit: what the hell - doesnt seem to fail when cannot forward - need to check that...
  private void doFinish() {
    // TODO: if not a forward and replication req is not specified, we could
    // send in a background thread

    cmdDistrib.finish();
    Response response = cmdDistrib.getResponse();
    // TODO - we may need to tell about more than one error...
    
    // if its a forward, any fail is a problem - 
    // otherwise we assume things are fine if we got it locally
    // until we start allowing min replication param
    if (response.errors.size() > 0) {
      // for now we don't error - we assume if it was added locally, we
      // succeeded - nocommit: forwards should error
      //rsp.setException(response.errors.get(0).e);
    }
   
    
    // if it is not a forward request, for each fail, try to tell them to
    // recover nocommit: we would really like to only do this on connection problems

    for (SolrCmdDistributor.Error error : response.errors) {
      if (error.node instanceof RetryNode) {
        // we don't try to force a leader to recover
        // when we cannot forward to it
        continue;
      }
      // TODO: we should force their state to recovering ??
      
      // TODO: do retries??
      // TODO: what if its is already recovering? Right now recoveries queue up -
      // should they?
      CommonsHttpSolrServer server;
      try {
        server = new CommonsHttpSolrServer(error.node.getBaseUrl());
        server.setSoTimeout(5000);
        server.setConnectionTimeout(5000);
        
        RequestRecovery recoverRequestCmd = new RequestRecovery();
        recoverRequestCmd.setAction(CoreAdminAction.REQUESTRECOVERY);
        recoverRequestCmd.setCoreName(error.node.getCoreName());
        
        server.request(recoverRequestCmd);
      } catch (Exception e) {
        log.warn("Problem trying to tell a replica to recover", e);
      }
      
    }
  }

 
  // must be synchronized by bucket
  private void doLocalAdd(AddUpdateCommand cmd) throws IOException {
    super.processAdd(cmd);
  }

  // must be synchronized by bucket
  private void doLocalDelete(DeleteUpdateCommand cmd) throws IOException {
    super.processDelete(cmd);
  }

  /**
   * @param cmd
   * @return whether or not to drop this cmd
   * @throws IOException
   */
  private boolean versionAdd(AddUpdateCommand cmd) throws IOException {
    BytesRef idBytes = cmd.getIndexedId();

    if (vinfo == null || idBytes == null) {
      super.processAdd(cmd);
      return false;
    }

    // This is only the hash for the bucket, and must be based only on the uniqueKey (i.e. do not use a pluggable hash here)
    int bucketHash = Hash.murmurhash3_x86_32(idBytes.bytes, idBytes.offset, idBytes.length, 0);

    // at this point, there is an update we need to try and apply.
    // we may or may not be the leader.

    // Find any existing version in the document
    // TODO: don't reuse update commands any more!
    long versionOnUpdate = cmd.getVersion();

    if (versionOnUpdate == 0) {
      SolrInputField versionField = cmd.getSolrInputDocument().getField(VersionInfo.VERSION_FIELD);
      if (versionField != null) {
        Object o = versionField.getValue();
        versionOnUpdate = o instanceof Number ? ((Number) o).longValue() : Long.parseLong(o.toString());
      } else {
        // Find the version
        String versionOnUpdateS = req.getParams().get(VERSION_FIELD);
        versionOnUpdate = versionOnUpdateS == null ? 0 : Long.parseLong(versionOnUpdateS);
      }
    }

    boolean isReplay = (cmd.getFlags() & UpdateCommand.REPLAY) != 0;
    boolean leaderLogic = isLeader && !isReplay;


    VersionBucket bucket = vinfo.bucket(bucketHash);

    vinfo.lockForUpdate();
    try {
      synchronized (bucket) {
        // we obtain the version when synchronized and then do the add so we can ensure that
        // if version1 < version2 then version1 is actually added before version2.

        // even if we don't store the version field, synchronizing on the bucket
        // will enable us to know what version happened first, and thus enable
        // realtime-get to work reliably.
        // TODO: if versions aren't stored, do we need to set on the cmd anyway for some reason?
        // there may be other reasons in the future for a version on the commands
        if (versionsStored) {

          long bucketVersion = bucket.highest;

          if (leaderLogic) {
            long version = vinfo.getNewClock();
            cmd.setVersion(version);
            cmd.getSolrInputDocument().setField(VersionInfo.VERSION_FIELD, version);
            bucket.updateHighest(version);
          } else {
            // The leader forwarded us this update.
            cmd.setVersion(versionOnUpdate);

            if (ulog.getState() != UpdateLog.State.ACTIVE && (cmd.getFlags() & UpdateCommand.REPLAY) == 0) {
              // we're not in an active state, and this update isn't from a replay, so buffer it.
              cmd.setFlags(cmd.getFlags() | UpdateCommand.BUFFERING);
              ulog.add(cmd);
              return true;
            }

            // if we aren't the leader, then we need to check that updates were not re-ordered
            if (bucketVersion != 0 && bucketVersion < versionOnUpdate) {
              // we're OK... this update has a version higher than anything we've seen
              // in this bucket so far, so we know that no reordering has yet occured.
              bucket.updateHighest(versionOnUpdate);
            } else {
              // there have been updates higher than the current update.  we need to check
              // the specific version for this id.
              Long lastVersion = vinfo.lookupVersion(cmd.getIndexedId());
              if (lastVersion != null && Math.abs(lastVersion) >= versionOnUpdate) {
                // This update is a repeat, or was reordered.  We need to drop this update.
                return true;
              }
            }
          }
        }

        doLocalAdd(cmd);
      }  // end synchronized (bucket)
    } finally {
      vinfo.unlockForUpdate();
    }
    return false;
  }
  
  @Override
  public void processDelete(DeleteUpdateCommand cmd) throws IOException {
    if (!cmd.isDeleteById()) {
      // delete by query...
      // TODO: handle versioned and distributed deleteByQuery

      // even in non zk mode, tests simulate updates from a leader
      if(!zkEnabled) {
        isLeader = !req.getParams().getBool(SEEN_LEADER, false);
      }
      
      processDeleteByQuery(cmd);
      return;
    }

    int hash = 0;
    if (zkEnabled) {
      hash = hash(cmd);
      nodes = setupRequest(hash);
    } else {
      // even in non zk mode, tests simulate updates from a leader
      isLeader = !req.getParams().getBool(SEEN_LEADER, false);
    }
    
    boolean dropCmd = false;
    if (!forwardToLeader) {
      dropCmd  = versionDelete(cmd);
    }
    
    if (dropCmd) {
      // TODO: do we need to add anything to the response?
      return;
    }

    ModifiableSolrParams params = null;
    if (nodes != null) {
      params = new ModifiableSolrParams(req.getParams());
      if (isLeader) {
        params.set(SEEN_LEADER, true);
      }
      cmdDistrib.distribDelete(cmd, nodes, params);
    }

    // cmd.getIndexId == null when delete by query
    // TODO: what to do when no idField?
    if (returnVersions && rsp != null && cmd.getIndexedId() != null && idField != null) {
      if (deleteResponse == null) {
        deleteResponse = new NamedList<String>();
        rsp.add("deletes",deleteResponse);
      }
      if (scratch == null) scratch = new CharsRef();
      idField.getType().indexedToReadable(cmd.getIndexedId(), scratch);
      deleteResponse.add(scratch.toString(), cmd.getVersion());  // we're returning the version of the delete.. not the version of the doc we deleted.
    }
  }

  private boolean versionDelete(DeleteUpdateCommand cmd) throws IOException {

    BytesRef idBytes = cmd.getIndexedId();

    if (vinfo == null || idBytes == null) {
      super.processDelete(cmd);
      return false;
    }

    // This is only the hash for the bucket, and must be based only on the uniqueKey (i.e. do not use a pluggable hash here)
    int bucketHash = Hash.murmurhash3_x86_32(idBytes.bytes, idBytes.offset, idBytes.length, 0);

    // at this point, there is an update we need to try and apply.
    // we may or may not be the leader.

    // Find the version
    long versionOnUpdate = cmd.getVersion();
    if (versionOnUpdate == 0) {
      String versionOnUpdateS = req.getParams().get(VERSION_FIELD);
      versionOnUpdate = versionOnUpdateS == null ? 0 : Long.parseLong(versionOnUpdateS);
    }
    versionOnUpdate = Math.abs(versionOnUpdate);  // normalize to positive version

    boolean isReplay = (cmd.getFlags() & UpdateCommand.REPLAY) != 0;
    boolean leaderLogic = isLeader && !isReplay;

    if (!leaderLogic && versionOnUpdate==0) {
      throw new SolrException(ErrorCode.BAD_REQUEST, "missing _version_ on update from leader");
    }

    VersionBucket bucket = vinfo.bucket(bucketHash);

    vinfo.lockForUpdate();
    try {

      synchronized (bucket) {
        if (versionsStored) {
          long bucketVersion = bucket.highest;

          if (leaderLogic) {
            long version = vinfo.getNewClock();
            cmd.setVersion(-version);
            bucket.updateHighest(version);
          } else {
            cmd.setVersion(-versionOnUpdate);

            if (ulog.getState() != UpdateLog.State.ACTIVE && (cmd.getFlags() & UpdateCommand.REPLAY) == 0) {
              // we're not in an active state, and this update isn't from a replay, so buffer it.
              cmd.setFlags(cmd.getFlags() | UpdateCommand.BUFFERING);
              ulog.delete(cmd);
              return true;
            }

            // if we aren't the leader, then we need to check that updates were not re-ordered
            if (bucketVersion != 0 && bucketVersion < versionOnUpdate) {
              // we're OK... this update has a version higher than anything we've seen
              // in this bucket so far, so we know that no reordering has yet occured.
              bucket.updateHighest(versionOnUpdate);
            } else {
              // there have been updates higher than the current update.  we need to check
              // the specific version for this id.
              Long lastVersion = vinfo.lookupVersion(cmd.getIndexedId());
              if (lastVersion != null && Math.abs(lastVersion) >= versionOnUpdate) {
                // This update is a repeat, or was reordered.  We need to drop this update.
                return true;
              }
            }
          }
        }

        doLocalDelete(cmd);
        return false;
      }  // end synchronized (bucket)

    } finally {
      vinfo.unlockForUpdate();
    }
  }

  private void processDeleteByQuery(DeleteUpdateCommand cmd) throws IOException {
    if (vinfo == null) {
      super.processDelete(cmd);
      return;
    }

    // at this point, there is an update we need to try and apply.
    // we may or may not be the leader.

    // Find the version
    long versionOnUpdate = cmd.getVersion();
    if (versionOnUpdate == 0) {
      String versionOnUpdateS = req.getParams().get(VERSION_FIELD);
      versionOnUpdate = versionOnUpdateS == null ? 0 : Long.parseLong(versionOnUpdateS);
    }
    versionOnUpdate = Math.abs(versionOnUpdate);  // normalize to positive version

    boolean isReplay = (cmd.getFlags() & UpdateCommand.REPLAY) != 0;
    boolean leaderLogic = isLeader && !isReplay;

    if (!leaderLogic && versionOnUpdate==0) {
      throw new SolrException(ErrorCode.BAD_REQUEST, "missing _version_ on update from leader");
    }

    vinfo.blockUpdates();
    try {

      if (versionsStored) {
        if (leaderLogic) {
          long version = vinfo.getNewClock();
          cmd.setVersion(-version);
          // TODO update versions in all buckets
        } else {
          cmd.setVersion(-versionOnUpdate);

          if (ulog.getState() != UpdateLog.State.ACTIVE && (cmd.getFlags() & UpdateCommand.REPLAY) == 0) {
            // we're not in an active state, and this update isn't from a replay, so buffer it.
            cmd.setFlags(cmd.getFlags() | UpdateCommand.BUFFERING);
            ulog.deleteByQuery(cmd);
            return;
          }
        }
      }

      doLocalDelete(cmd);

    } finally {
      vinfo.unblockUpdates();
    }

  }

  @Override
  public void processCommit(CommitUpdateCommand cmd) throws IOException {
    if (vinfo != null) {
      vinfo.lockForUpdate();
    }
    try {

      if (ulog == null || ulog.getState() == UpdateLog.State.ACTIVE || (cmd.getFlags() & UpdateCommand.REPLAY) != 0) {
        super.processCommit(cmd);
      } else {
        log.info("Ignoring commit while not ACTIVE - state: " + ulog.getState() + " replay:" + (cmd.getFlags() & UpdateCommand.REPLAY));
      }

    } finally {
      if (vinfo != null) {
        vinfo.unlockForUpdate();
      }
    }
    // TODO: we should consider this? commit everyone in the current collection

    if (zkEnabled) {
      ModifiableSolrParams params = new ModifiableSolrParams(req.getParams());
      if (!params.getBool(COMMIT_END_POINT, false)) {
        params.set(COMMIT_END_POINT, true);

        String nodeName = req.getCore().getCoreDescriptor().getCoreContainer()
            .getZkController().getNodeName();
        String shardZkNodeName = nodeName + "_" + req.getCore().getName();
        List<Node> nodes = getReplicaUrls(req, req.getCore().getCoreDescriptor()
            .getCloudDescriptor().getCollectionName(), shardZkNodeName);

        if (nodes != null) {
          cmdDistrib.distribCommit(cmd, nodes, params);
          finish();
        }
      }
    }
  }
  
  @Override
  public void finish() throws IOException {
    doFinish();
    
    if (next != null && nodes == null) next.finish();
  }
 
  private List<Node> getReplicaNodes(SolrQueryRequest req, String collection,
      String shardId, String thisNodeName) {
    CloudState cloudState = req.getCore().getCoreDescriptor()
        .getCoreContainer().getZkController().getCloudState();

    Map<String,Slice> slices = cloudState.getSlices(collection);
    if (slices == null) {
      throw new ZooKeeperException(ErrorCode.BAD_REQUEST, "Could not find collection in zk: " + cloudState);
    }
    
    Slice replicas = slices.get(shardId);
    if (replicas == null) {
      throw new ZooKeeperException(ErrorCode.BAD_REQUEST, "Could not find shardId in zk: " + shardId);
    }
    
    Map<String,ZkNodeProps> shardMap = replicas.getShards();
    List<Node> nodes = new ArrayList<Node>(shardMap.size());

    for (Entry<String,ZkNodeProps> entry : shardMap.entrySet()) {
      ZkCoreNodeProps nodeProps = new ZkCoreNodeProps(entry.getValue());
      String nodeName = nodeProps.getNodeName();
      if (cloudState.liveNodesContain(nodeName) && !nodeName.equals(thisNodeName)) {
        nodes.add(new StdNode(nodeProps));
      }
    }
    if (nodes.size() == 0) {
      // no replicas - go local
      return null;
    }
    return nodes;
  }
  
  private List<Node> getReplicaUrls(SolrQueryRequest req, String collection, String shardZkNodeName) {
    CloudState cloudState = req.getCore().getCoreDescriptor()
        .getCoreContainer().getZkController().getCloudState();
    List<Node> urls = new ArrayList<Node>();
    Map<String,Slice> slices = cloudState.getSlices(collection);
    if (slices == null) {
      throw new ZooKeeperException(ErrorCode.BAD_REQUEST,
          "Could not find collection in zk: " + cloudState);
    }
    for (Map.Entry<String,Slice> sliceEntry : slices.entrySet()) {
      Slice replicas = slices.get(sliceEntry.getKey());
      
      Map<String,ZkNodeProps> shardMap = replicas.getShards();
      
      for (Entry<String,ZkNodeProps> entry : shardMap.entrySet()) {
        ZkCoreNodeProps nodeProps = new ZkCoreNodeProps(entry.getValue());
        if (cloudState.liveNodesContain(nodeProps.getNodeName()) && !entry.getKey().equals(shardZkNodeName)) {
          urls.add(new StdNode(nodeProps));
        }
      }
    }
    if (urls.size() == 0) {
      return null;
    }
    return urls;
  }
  
  // TODO: move this to AddUpdateCommand/DeleteUpdateCommand and cache it? And
  // make the hash pluggable of course.
  // The hash also needs to be pluggable
  private int hash(AddUpdateCommand cmd) {
    BytesRef br = cmd.getIndexedId();
    return Hash.murmurhash3_x86_32(br.bytes, br.offset, br.length, 0);
  }
  
  private int hash(DeleteUpdateCommand cmd) {
    BytesRef br = cmd.getIndexedId();
    return Hash.murmurhash3_x86_32(br.bytes, br.offset, br.length, 0);
  }
  
  // RetryNodes are used in the case of 'forward to leader' where we want
  // to try the latest leader on a fail in the case the leader just went down.
  public static class RetryNode extends StdNode {
    
    private ZkStateReader zkStateReader;
    private String collection;
    private String shardId;
    
    public RetryNode(ZkCoreNodeProps nodeProps, ZkStateReader zkStateReader, String collection, String shardId) {
      super(nodeProps);
      this.zkStateReader = zkStateReader;
      this.collection = collection;
      this.shardId = shardId;
    }
    
    @Override
    public String toString() {
      return url;
    }

    @Override
    public boolean checkRetry() {
      ZkCoreNodeProps leaderProps;
      try {
        leaderProps = new ZkCoreNodeProps(zkStateReader.getLeaderProps(
            collection, shardId));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
      
      this.url = leaderProps.getCoreUrl();

      return true;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result
          + ((collection == null) ? 0 : collection.hashCode());
      result = prime * result + ((shardId == null) ? 0 : shardId.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!super.equals(obj)) return false;
      if (getClass() != obj.getClass()) return false;
      RetryNode other = (RetryNode) obj;
      if (url == null) {
        if (other.url != null) return false;
      } else if (!url.equals(other.url)) return false;

      return true;
    }
  }
  
}
