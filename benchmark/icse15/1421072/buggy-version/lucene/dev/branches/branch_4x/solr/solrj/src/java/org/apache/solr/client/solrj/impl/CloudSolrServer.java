  Merged /lucene/dev/trunk/solr/core:r1421071
package org.apache.solr.client.solrj.impl;

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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.IsUpdateRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.cloud.ZooKeeperException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.StrUtils;
import org.apache.zookeeper.KeeperException;

/**
 * SolrJ client class to communicate with SolrCloud.
 * Instances of this class communicate with Zookeeper to discover
 * Solr endpoints for SolrCloud collections, and then use the 
 * {@link LBHttpSolrServer} to issue requests.
 */
public class CloudSolrServer extends SolrServer {
  private volatile ZkStateReader zkStateReader;
  private String zkHost; // the zk server address
  private int zkConnectTimeout = 10000;
  private int zkClientTimeout = 10000;
  private volatile String defaultCollection;
  private LBHttpSolrServer lbServer;
  private HttpClient myClient;
  Random rand = new Random();
  
  private Object cachLock = new Object();
  // since the state shouldn't change often, should be very cheap reads
  private Map<String,List<String>> urlLists = new HashMap<String,List<String>>();
  private Map<String,List<String>> leaderUrlLists = new HashMap<String,List<String>>();

  private Map<String,List<String>> replicasLists = new HashMap<String,List<String>>();
  
  private volatile int lastClusterStateHashCode;
  
  private final boolean updatesToLeaders;

  
  /**
   * @param zkHost The client endpoint of the zookeeper quorum containing the cloud state,
   * in the form HOST:PORT.
   */
  public CloudSolrServer(String zkHost) throws MalformedURLException {
      this.zkHost = zkHost;
      this.myClient = HttpClientUtil.createClient(null);
      this.lbServer = new LBHttpSolrServer(myClient);
      this.updatesToLeaders = true;
  }

  /**
   * @param zkHost The client endpoint of the zookeeper quorum containing the cloud state,
   * in the form HOST:PORT.
   * @param lbServer LBHttpSolrServer instance for requests. 
   */
  public CloudSolrServer(String zkHost, LBHttpSolrServer lbServer) {
    this.zkHost = zkHost;
    this.lbServer = lbServer;
    this.updatesToLeaders = true;
  }
  
  /**
   * @param zkHost The client endpoint of the zookeeper quorum containing the cloud state,
   * in the form HOST:PORT.
   * @param lbServer LBHttpSolrServer instance for requests. 
   * @param updatesToLeaders sends updates only to leaders - defaults to true
   */
  public CloudSolrServer(String zkHost, LBHttpSolrServer lbServer, boolean updatesToLeaders) {
    this.zkHost = zkHost;
    this.lbServer = lbServer;
    this.updatesToLeaders = updatesToLeaders;
  }

  public ZkStateReader getZkStateReader() {
    return zkStateReader;
  }
  
  /** Sets the default collection for request */
  public void setDefaultCollection(String collection) {
    this.defaultCollection = collection;
  }

  /** Set the connect timeout to the zookeeper ensemble in ms */
  public void setZkConnectTimeout(int zkConnectTimeout) {
    this.zkConnectTimeout = zkConnectTimeout;
  }

  /** Set the timeout to the zookeeper ensemble in ms */
  public void setZkClientTimeout(int zkClientTimeout) {
    this.zkClientTimeout = zkClientTimeout;
  }

  /**
   * Connect to the zookeeper ensemble.
   * This is an optional method that may be used to force a connect before any other requests are sent.
   *
   */
  public void connect() {
    if (zkStateReader == null) {
      synchronized (this) {
        if (zkStateReader == null) {
          try {
            ZkStateReader zk = new ZkStateReader(zkHost, zkConnectTimeout,
                zkClientTimeout);
            zk.createClusterStateWatchersAndUpdate();
            zkStateReader = zk;
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
                "", e);
          } catch (KeeperException e) {
            throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
                "", e);
          } catch (IOException e) {
            throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
                "", e);
          } catch (TimeoutException e) {
            throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
                "", e);
          }
        }
      }
    }
  }

  @Override
  public NamedList<Object> request(SolrRequest request) throws SolrServerException, IOException {
    connect();

    // TODO: if you can hash here, you could favor the shard leader
    
    ClusterState clusterState = zkStateReader.getClusterState();
    boolean sendToLeaders = false;
    List<String> replicas = null;
    
    if (request instanceof IsUpdateRequest && updatesToLeaders) {
      sendToLeaders = true;
      replicas = new ArrayList<String>();
    }

    SolrParams reqParams = request.getParams();
    if (reqParams == null) {
      reqParams = new ModifiableSolrParams();
    }
    String collection = reqParams.get("collection", defaultCollection);
    
    if (collection == null) {
      throw new SolrServerException("No collection param specified on request and no default collection has been set.");
    }
    
    // Extract each comma separated collection name and store in a List.
    List<String> collectionList = StrUtils.splitSmart(collection, ",", true);
    
    // TODO: not a big deal because of the caching, but we could avoid looking at every shard
    // when getting leaders if we tweaked some things
    
    // Retrieve slices from the cloud state and, for each collection specified,
    // add it to the Map of slices.
    Map<String,Slice> slices = new HashMap<String,Slice>();
    for (String collectionName : collectionList) {
      ClientUtils.addSlices(slices, collectionName, clusterState.getSlices(collectionName), true);
    }

    Set<String> liveNodes = clusterState.getLiveNodes();

    List<String> theUrlList;
    synchronized (cachLock) {
      List<String> leaderUrlList = leaderUrlLists.get(collection);
      List<String> urlList = urlLists.get(collection);
      List<String> replicasList = replicasLists.get(collection);

      if ((sendToLeaders && leaderUrlList == null) || (!sendToLeaders
          && urlList == null)
          || clusterState.hashCode() != this.lastClusterStateHashCode) {
        // build a map of unique nodes
        // TODO: allow filtering by group, role, etc
        Map<String,ZkNodeProps> nodes = new HashMap<String,ZkNodeProps>();
        List<String> urlList2 = new ArrayList<String>();
        for (Slice slice : slices.values()) {
          for (ZkNodeProps nodeProps : slice.getReplicasMap().values()) {
            ZkCoreNodeProps coreNodeProps = new ZkCoreNodeProps(nodeProps);
            String node = coreNodeProps.getNodeName();
            if (!liveNodes.contains(coreNodeProps.getNodeName())
                || !coreNodeProps.getState().equals(ZkStateReader.ACTIVE)) continue;
            if (nodes.put(node, nodeProps) == null) {
              if (!sendToLeaders || (sendToLeaders && coreNodeProps.isLeader())) {
                String url = coreNodeProps.getCoreUrl();
                urlList2.add(url);
              } else if (sendToLeaders) {
                String url = coreNodeProps.getCoreUrl();
                replicas.add(url);
              }
            }
          }
        }
        if (sendToLeaders) {
          this.leaderUrlLists.put(collection, urlList2);
          leaderUrlList = urlList2;
          this.replicasLists.put(collection, replicas);
          replicasList = replicas;
        } else {
          this.urlLists.put(collection, urlList2);
          urlList = urlList2;
        }
        this.lastClusterStateHashCode = clusterState.hashCode();
      }
      
      if (sendToLeaders) {
        theUrlList = new ArrayList<String>(leaderUrlList.size());
        theUrlList.addAll(leaderUrlList);
      } else {
        theUrlList = new ArrayList<String>(urlList.size());
        theUrlList.addAll(urlList);
      }
      Collections.shuffle(theUrlList, rand);
      if (sendToLeaders) {
        ArrayList<String> theReplicas = new ArrayList<String>(
            replicasList.size());
        theReplicas.addAll(replicasList);
        Collections.shuffle(theReplicas, rand);
        // System.out.println("leaders:" + theUrlList);
        // System.out.println("replicas:" + theReplicas);
        theUrlList.addAll(theReplicas);
      }
    }
 
   // System.out.println("########################## MAKING REQUEST TO " + theUrlList);
 
    LBHttpSolrServer.Req req = new LBHttpSolrServer.Req(request, theUrlList);
    LBHttpSolrServer.Rsp rsp = lbServer.request(req);
    return rsp.getResponse();
  }

  @Override
  public void shutdown() {
    if (zkStateReader != null) {
      synchronized(this) {
        if (zkStateReader!= null)
          zkStateReader.close();
        zkStateReader = null;
      }
    }
    if (myClient!=null) {
      myClient.getConnectionManager().shutdown();
    }
  }

  public LBHttpSolrServer getLbServer() {
    return lbServer;
  }

  // for tests
  Map<String,List<String>> getUrlLists() {
    return urlLists;
  }

  //for tests
  Map<String,List<String>> getLeaderUrlLists() {
    return leaderUrlLists;
  }

  //for tests
  Map<String,List<String>> getReplicasLists() {
    return replicasLists;
  }

}
