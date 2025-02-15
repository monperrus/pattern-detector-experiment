package org.apache.solr.handler.admin;

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

import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest.RequestSyncShard;
import org.apache.solr.cloud.DistributedQueue.QueueEvent;
import org.apache.solr.cloud.Overseer;
import org.apache.solr.cloud.OverseerCollectionProcessor;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.ImplicitDocRouter;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.params.CollectionParams.CollectionAction;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.solr.cloud.Overseer.QUEUE_OPERATION;
import static org.apache.solr.cloud.OverseerCollectionProcessor.COLL_CONF;
import static org.apache.solr.cloud.OverseerCollectionProcessor.CREATESHARD;
import static org.apache.solr.cloud.OverseerCollectionProcessor.CREATE_NODE_SET;
import static org.apache.solr.cloud.OverseerCollectionProcessor.MAX_SHARDS_PER_NODE;
import static org.apache.solr.cloud.OverseerCollectionProcessor.NUM_SLICES;
import static org.apache.solr.cloud.OverseerCollectionProcessor.REPLICATION_FACTOR;
import static org.apache.solr.cloud.OverseerCollectionProcessor.ROUTER;
import static org.apache.solr.cloud.OverseerCollectionProcessor.SHARDS_PROP;
import static org.apache.solr.common.cloud.DocRouter.ROUTE_FIELD;
import static org.apache.solr.common.cloud.ZkStateReader.COLLECTION_PROP;
import static org.apache.solr.common.cloud.ZkStateReader.SHARD_ID_PROP;

public class CollectionsHandler extends RequestHandlerBase {
  protected static Logger log = LoggerFactory.getLogger(CollectionsHandler.class);
  protected final CoreContainer coreContainer;

  public CollectionsHandler() {
    super();
    // Unlike most request handlers, CoreContainer initialization 
    // should happen in the constructor...  
    this.coreContainer = null;
  }


  /**
   * Overloaded ctor to inject CoreContainer into the handler.
   *
   * @param coreContainer Core Container of the solr webapp installed.
   */
  public CollectionsHandler(final CoreContainer coreContainer) {
    this.coreContainer = coreContainer;
  }


  @Override
  final public void init(NamedList args) {

  }

  /**
   * The instance of CoreContainer this handler handles. This should be the CoreContainer instance that created this
   * handler.
   *
   * @return a CoreContainer instance
   */
  public CoreContainer getCoreContainer() {
    return this.coreContainer;
  }

  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
    // Make sure the cores is enabled
    CoreContainer cores = getCoreContainer();
    if (cores == null) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
              "Core container instance missing");
    }

    // Pick the action
    SolrParams params = req.getParams();
    CollectionAction action = null;
    String a = params.get(CoreAdminParams.ACTION);
    if (a != null) {
      action = CollectionAction.get(a);
    }
    if (action == null) {
      throw new SolrException(ErrorCode.BAD_REQUEST, "Unknown action: " + a);
    }
    
    switch (action) {
      case CREATE: {
        this.handleCreateAction(req, rsp);
        break;
      }
      case DELETE: {
        this.handleDeleteAction(req, rsp);
        break;
      }
      case RELOAD: {
        this.handleReloadAction(req, rsp);
        break;
      }
      case SYNCSHARD: {
        this.handleSyncShardAction(req, rsp);
        break;
      }
      case CREATEALIAS: {
        this.handleCreateAliasAction(req, rsp);
        break;
      }
      case DELETEALIAS: {
        this.handleDeleteAliasAction(req, rsp);
        break;
      }
      case SPLITSHARD:  {
        this.handleSplitShardAction(req, rsp);
        break;
      }
      case DELETESHARD: {
        this.handleDeleteShardAction(req, rsp);
        break;
      }case CREATESHARD: {
        this.handleCreateShard(req, rsp);
        break;
      }

      default: {
          throw new RuntimeException("Unknown action: " + action);
      }
    }

    rsp.setHttpCaching(false);
  }
  
  public static long DEFAULT_ZK_TIMEOUT = 60*1000;

  private void handleResponse(String operation, ZkNodeProps m,
                              SolrQueryResponse rsp) throws KeeperException, InterruptedException {
    handleResponse(operation, m, rsp, DEFAULT_ZK_TIMEOUT);
  }
  
  private void handleResponse(String operation, ZkNodeProps m,
      SolrQueryResponse rsp, long timeout) throws KeeperException, InterruptedException {
    long time = System.currentTimeMillis();
    QueueEvent event = coreContainer.getZkController()
        .getOverseerCollectionQueue()
        .offer(ZkStateReader.toJSON(m), timeout);
    if (event.getBytes() != null) {
      SolrResponse response = SolrResponse.deserialize(event.getBytes());
      rsp.getValues().addAll(response.getResponse());
      SimpleOrderedMap exp = (SimpleOrderedMap) response.getResponse().get("exception");
      if (exp != null) {
        Integer code = (Integer) exp.get("rspCode");
        rsp.setException(new SolrException(code != null && code != -1 ? ErrorCode.getErrorCode(code) : ErrorCode.SERVER_ERROR, (String)exp.get("msg")));
      }
    } else {
      if (System.currentTimeMillis() - time >= timeout) {
        throw new SolrException(ErrorCode.SERVER_ERROR, operation
            + " the collection time out:" + timeout / 1000 + "s");
      } else if (event.getWatchedEvent() != null) {
        throw new SolrException(ErrorCode.SERVER_ERROR, operation
            + " the collection error [Watcher fired on path: "
            + event.getWatchedEvent().getPath() + " state: "
            + event.getWatchedEvent().getState() + " type "
            + event.getWatchedEvent().getType() + "]");
      } else {
        throw new SolrException(ErrorCode.SERVER_ERROR, operation
            + " the collection unkown case");
      }
    }
  }
  
  private void handleReloadAction(SolrQueryRequest req, SolrQueryResponse rsp) throws KeeperException, InterruptedException {
    log.info("Reloading Collection : " + req.getParamString());
    String name = req.getParams().required().get("name");
    
    ZkNodeProps m = new ZkNodeProps(Overseer.QUEUE_OPERATION,
        OverseerCollectionProcessor.RELOADCOLLECTION, "name", name);

    handleResponse(OverseerCollectionProcessor.RELOADCOLLECTION, m, rsp);
  }
  
  private void handleSyncShardAction(SolrQueryRequest req, SolrQueryResponse rsp) throws KeeperException, InterruptedException, SolrServerException, IOException {
    log.info("Syncing shard : " + req.getParamString());
    String collection = req.getParams().required().get("collection");
    String shard = req.getParams().required().get("shard");
    
    ClusterState clusterState = coreContainer.getZkController().getClusterState();
    
    ZkNodeProps leaderProps = clusterState.getLeader(collection, shard);
    ZkCoreNodeProps nodeProps = new ZkCoreNodeProps(leaderProps);
    
    HttpSolrServer server = new HttpSolrServer(nodeProps.getBaseUrl());
    server.setConnectionTimeout(15000);
    server.setSoTimeout(30000);
    RequestSyncShard reqSyncShard = new CoreAdminRequest.RequestSyncShard();
    reqSyncShard.setCollection(collection);
    reqSyncShard.setShard(shard);
    reqSyncShard.setCoreName(nodeProps.getCoreName());
    server.request(reqSyncShard);
  }
  
  private void handleCreateAliasAction(SolrQueryRequest req,
      SolrQueryResponse rsp) throws Exception {
    log.info("Create alias action : " + req.getParamString());
    String name = req.getParams().required().get("name");
    String collections = req.getParams().required().get("collections");
    
    ZkNodeProps m = new ZkNodeProps(Overseer.QUEUE_OPERATION,
        OverseerCollectionProcessor.CREATEALIAS, "name", name, "collections",
        collections);
    
    handleResponse(OverseerCollectionProcessor.CREATEALIAS, m, rsp);
  }
  
  private void handleDeleteAliasAction(SolrQueryRequest req,
      SolrQueryResponse rsp) throws Exception {
    log.info("Delete alias action : " + req.getParamString());
    String name = req.getParams().required().get("name");
    
    ZkNodeProps m = new ZkNodeProps(Overseer.QUEUE_OPERATION,
        OverseerCollectionProcessor.DELETEALIAS, "name", name);
    
    handleResponse(OverseerCollectionProcessor.CREATEALIAS, m, rsp);
  }

  private void handleDeleteAction(SolrQueryRequest req, SolrQueryResponse rsp) throws KeeperException, InterruptedException {
    log.info("Deleting Collection : " + req.getParamString());
    
    String name = req.getParams().required().get("name");
    
    ZkNodeProps m = new ZkNodeProps(Overseer.QUEUE_OPERATION,
        OverseerCollectionProcessor.DELETECOLLECTION, "name", name);

    handleResponse(OverseerCollectionProcessor.DELETECOLLECTION, m, rsp);
  }

  // very simple currently, you can pass a template collection, and the new collection is created on
  // every node the template collection is on
  // there is a lot more to add - you should also be able to create with an explicit server list
  // we might also want to think about error handling (add the request to a zk queue and involve overseer?)
  // as well as specific replicas= options
  private void handleCreateAction(SolrQueryRequest req,
      SolrQueryResponse rsp) throws InterruptedException, KeeperException {
    log.info("Creating Collection : " + req.getParamString());
    String name = req.getParams().required().get("name");
    if (name == null) {
      log.error("Collection name is required to create a new collection");
      throw new SolrException(ErrorCode.BAD_REQUEST,
          "Collection name is required to create a new collection");
    }
    
    Map<String,Object> props = new HashMap<String,Object>();
    props.put(Overseer.QUEUE_OPERATION,
        OverseerCollectionProcessor.CREATECOLLECTION);

    copyIfNotNull(req.getParams(),props,
        "name",
        REPLICATION_FACTOR,
         COLL_CONF,
         NUM_SLICES,
         MAX_SHARDS_PER_NODE,
        CREATE_NODE_SET ,
        ROUTER,
        SHARDS_PROP,
        ROUTE_FIELD);


    ZkNodeProps m = new ZkNodeProps(props);
    handleResponse(OverseerCollectionProcessor.CREATECOLLECTION, m, rsp);
  }

  private void handleCreateShard(SolrQueryRequest req, SolrQueryResponse rsp) throws KeeperException, InterruptedException {
    log.info("Create shard: " + req.getParamString());
    req.getParams().required().check(COLLECTION_PROP, SHARD_ID_PROP);
    ClusterState clusterState = coreContainer.getZkController().getClusterState();
    if(!ImplicitDocRouter.NAME.equals( clusterState.getCollection(req.getParams().get(COLLECTION_PROP)).getStr(ROUTER)))
      throw new SolrException(ErrorCode.BAD_REQUEST, "shards can be added only to 'implicit' collections" );

    Map<String, Object> map = OverseerCollectionProcessor.asMap(QUEUE_OPERATION, CREATESHARD);
    copyIfNotNull(req.getParams(),map,COLLECTION_PROP, SHARD_ID_PROP, REPLICATION_FACTOR,CREATE_NODE_SET);
    ZkNodeProps m = new ZkNodeProps(map);
    handleResponse(CREATESHARD, m, rsp);
  }

  private static void copyIfNotNull(SolrParams params, Map<String, Object> props, String... keys) {
    if(keys !=null){
      for (String key : keys) {
        String v = params.get(key);
        if(v != null) props.put(key,v);
      }
    }

  }
  
  private void handleDeleteShardAction(SolrQueryRequest req,
      SolrQueryResponse rsp) throws InterruptedException, KeeperException {
    log.info("Deleting Shard : " + req.getParamString());
    String name = req.getParams().required().get("collection");
    String shard = req.getParams().required().get("shard");
    
    Map<String,Object> props = new HashMap<String,Object>();
    props.put("collection", name);
    props.put(Overseer.QUEUE_OPERATION, OverseerCollectionProcessor.DELETESHARD);
    props.put(ZkStateReader.SHARD_ID_PROP, shard);

    ZkNodeProps m = new ZkNodeProps(props);
    handleResponse(OverseerCollectionProcessor.DELETESHARD, m, rsp);
  }

  private void handleSplitShardAction(SolrQueryRequest req, SolrQueryResponse rsp) throws KeeperException, InterruptedException {
    log.info("Splitting shard : " + req.getParamString());
    String name = req.getParams().required().get("collection");
    // TODO : add support for multiple shards
    String shard = req.getParams().required().get("shard");
    // TODO : add support for shard range

    Map<String,Object> props = new HashMap<String,Object>();
    props.put(Overseer.QUEUE_OPERATION, OverseerCollectionProcessor.SPLITSHARD);
    props.put("collection", name);
    props.put(ZkStateReader.SHARD_ID_PROP, shard);

    ZkNodeProps m = new ZkNodeProps(props);

    handleResponse(OverseerCollectionProcessor.SPLITSHARD, m, rsp, DEFAULT_ZK_TIMEOUT * 5);
  }

  public static ModifiableSolrParams params(String... params) {
    ModifiableSolrParams msp = new ModifiableSolrParams();
    for (int i=0; i<params.length; i+=2) {
      msp.add(params[i], params[i+1]);
    }
    return msp;
  }

  //////////////////////// SolrInfoMBeans methods //////////////////////

  @Override
  public String getDescription() {
    return "Manage SolrCloud Collections";
  }

  @Override
  public String getSource() {
    return "$URL: https://svn.apache.org/repos/asf/lucene/dev/trunk/solr/core/src/java/org/apache/solr/handler/admin/CollectionHandler.java $";
  }
}
