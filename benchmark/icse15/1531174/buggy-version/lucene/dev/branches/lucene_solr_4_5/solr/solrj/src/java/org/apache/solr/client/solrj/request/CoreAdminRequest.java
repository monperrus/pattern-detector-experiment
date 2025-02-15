  Merged /lucene/dev/trunk/solr/core:r1530772
  Merged /lucene/dev/branches/branch_4x/solr/core:r1530773
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

package org.apache.solr.client.solrj.request;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * This class is experimental and subject to change.
 *
 * @since solr 1.3
 */
public class CoreAdminRequest extends SolrRequest
{
  protected String core = null;
  protected String other = null;
  protected boolean isIndexInfoNeeded = true;
  protected CoreAdminParams.CoreAdminAction action = null;
  
  //a create core request
  public static class Create extends CoreAdminRequest {
    protected String instanceDir;
    protected String configName = null;
    protected String schemaName = null;
    protected String dataDir = null;
    protected String ulogDir = null;
    protected String collection;
    private Integer numShards;
    private String shardId;
    private String roles;
    private String coreNodeName;
    private Boolean loadOnStartup;
    private Boolean isTransient;

    public Create() {
      action = CoreAdminAction.CREATE;
    }
    
    public void setInstanceDir(String instanceDir) { this.instanceDir = instanceDir; }
    public void setSchemaName(String schema) { this.schemaName = schema; }
    public void setConfigName(String config) { this.configName = config; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public void setUlogDir(String ulogDir) { this.ulogDir = ulogDir; }
    public void setCollection(String collection) { this.collection = collection; }
    public void setNumShards(int numShards) {this.numShards = numShards;}
    public void setShardId(String shardId) {this.shardId = shardId;}
    public void setRoles(String roles) {this.roles = roles;}
    public void setCoreNodeName(String coreNodeName) {this.coreNodeName = coreNodeName;}
    public void setIsTransient(Boolean isTransient) { this.isTransient = isTransient; }
    public void setIsLoadOnStartup(Boolean loadOnStartup) { this.loadOnStartup = loadOnStartup;}

    public String getInstanceDir() { return instanceDir; }
    public String getSchemaName()  { return schemaName; }
    public String getConfigName()  { return configName; }
    public String getDataDir() { return dataDir; }
    public String getUlogDir() { return ulogDir; }
    public String getCollection() { return collection; }
    public String getShardId() { return shardId; }
    public String getRoles() { return roles; }
    public String getCoreNodeName() { return coreNodeName; }
    public Boolean getIsLoadOnStartup() { return loadOnStartup; }
    public Boolean getIsTransient() { return isTransient; }

    @Override
    public SolrParams getParams() {
      if( action == null ) {
        throw new RuntimeException( "no action specified!" );
      }
      ModifiableSolrParams params = new ModifiableSolrParams();
      params.set( CoreAdminParams.ACTION, action.toString() );
      if( action.equals(CoreAdminAction.CREATE) ) {
        params.set( CoreAdminParams.NAME, core );
      } else {
        params.set( CoreAdminParams.CORE, core );
      }
      params.set( CoreAdminParams.INSTANCE_DIR, instanceDir);
      if (configName != null) {
        params.set( CoreAdminParams.CONFIG, configName);
      }
      if (schemaName != null) {
        params.set( CoreAdminParams.SCHEMA, schemaName);
      }
      if (dataDir != null) {
        params.set( CoreAdminParams.DATA_DIR, dataDir);
      }
      if (ulogDir != null) {
        params.set( CoreAdminParams.ULOG_DIR, ulogDir);
      }
      if (collection != null) {
        params.set( CoreAdminParams.COLLECTION, collection);
      }
      if (numShards != null) {
        params.set( ZkStateReader.NUM_SHARDS_PROP, numShards);
      }
      if (shardId != null) {
        params.set( CoreAdminParams.SHARD, shardId);
      }
      if (roles != null) {
        params.set( CoreAdminParams.ROLES, roles);
      }
      if (coreNodeName != null) {
        params.set( CoreAdminParams.CORE_NODE_NAME, coreNodeName);
      }

      if (isTransient != null) {
        params.set(CoreAdminParams.TRANSIENT, isTransient);
      }

      if (loadOnStartup != null) {
        params.set(CoreAdminParams.LOAD_ON_STARTUP, loadOnStartup);
      }
      return params;
    }

  }
  
  public static class WaitForState extends CoreAdminRequest {
    protected String nodeName;
    protected String coreNodeName;
    protected String state;
    protected Boolean checkLive;
    protected Boolean onlyIfLeader;
    

    public WaitForState() {
      action = CoreAdminAction.PREPRECOVERY;
    }
    
    public void setNodeName(String nodeName) {
      this.nodeName = nodeName;
    }
    
    public String getNodeName() {
      return nodeName;
    }
    
    public String getCoreNodeName() {
      return coreNodeName;
    }

    public void setCoreNodeName(String coreNodeName) {
      this.coreNodeName = coreNodeName;
    }

    public String getState() {
      return state;
    }

    public void setState(String state) {
      this.state = state;
    }

    public Boolean getCheckLive() {
      return checkLive;
    }

    public void setCheckLive(Boolean checkLive) {
      this.checkLive = checkLive;
    }
    
    public boolean isOnlyIfLeader() {
      return onlyIfLeader;
    }

    public void setOnlyIfLeader(boolean onlyIfLeader) {
      this.onlyIfLeader = onlyIfLeader;
    }
    
    @Override
    public SolrParams getParams() {
      if( action == null ) {
        throw new RuntimeException( "no action specified!" );
      }
      ModifiableSolrParams params = new ModifiableSolrParams();
      params.set( CoreAdminParams.ACTION, action.toString() );
 
      params.set( CoreAdminParams.CORE, core );
      
      if (nodeName != null) {
        params.set( "nodeName", nodeName);
      }
      
      if (coreNodeName != null) {
        params.set( "coreNodeName", coreNodeName);
      }
      
      if (state != null) {
        params.set( "state", state);
      }
      
      if (checkLive != null) {
        params.set( "checkLive", checkLive);
      }
      
      if (onlyIfLeader != null) {
        params.set( "onlyIfLeader", onlyIfLeader);
      }

      return params;
    }

  }
  
  public static class RequestRecovery extends CoreAdminRequest {

    public RequestRecovery() {
      action = CoreAdminAction.REQUESTRECOVERY;
    }

    @Override
    public SolrParams getParams() {
      if( action == null ) {
        throw new RuntimeException( "no action specified!" );
      }
      ModifiableSolrParams params = new ModifiableSolrParams();
      params.set( CoreAdminParams.ACTION, action.toString() );
 
      params.set( CoreAdminParams.CORE, core );

      return params;
    }
  }
  
  public static class RequestSyncShard extends CoreAdminRequest {
    private String shard;
    private String collection;
    
    public RequestSyncShard() {
      action = CoreAdminAction.REQUESTSYNCSHARD;
    }

    @Override
    public SolrParams getParams() {
      if( action == null ) {
        throw new RuntimeException( "no action specified!" );
      }
      ModifiableSolrParams params = new ModifiableSolrParams();
      params.set(CoreAdminParams.ACTION, action.toString());
      params.set("shard", shard);
      params.set("collection", collection);
      params.set(CoreAdminParams.CORE, core);
      return params;
    }

    public String getShard() {
      return shard;
    }

    public void setShard(String shard) {
      this.shard = shard;
    }

    public String getCollection() {
      return collection;
    }

    public void setCollection(String collection) {
      this.collection = collection;
    }
  }
  
    //a persist core request
  public static class Persist extends CoreAdminRequest {
    protected String fileName = null;
    
    public Persist() {
      action = CoreAdminAction.PERSIST;
    }
    
    public void setFileName(String name) {
      fileName = name;
    }
    public String getFileName() {
      return fileName;
    }
    @Override
    public SolrParams getParams() {
      if( action == null ) {
        throw new RuntimeException( "no action specified!" );
      }
      ModifiableSolrParams params = new ModifiableSolrParams();
      params.set( CoreAdminParams.ACTION, action.toString() );
      if (fileName != null) {
        params.set( CoreAdminParams.FILE, fileName);
      }
      return params;
    }
  }
  
  public static class MergeIndexes extends CoreAdminRequest {
    protected List<String> indexDirs;
    protected List<String> srcCores;

    public MergeIndexes() {
      action = CoreAdminAction.MERGEINDEXES;
    }

    public void setIndexDirs(List<String> indexDirs) {
      this.indexDirs = indexDirs;
    }

    public List<String> getIndexDirs() {
      return indexDirs;
    }

    public List<String> getSrcCores() {
      return srcCores;
    }

    public void setSrcCores(List<String> srcCores) {
      this.srcCores = srcCores;
    }

    @Override
    public SolrParams getParams() {
      if (action == null) {
        throw new RuntimeException("no action specified!");
      }
      ModifiableSolrParams params = new ModifiableSolrParams();
      params.set(CoreAdminParams.ACTION, action.toString());
      params.set(CoreAdminParams.CORE, core);
      if (indexDirs != null)  {
        for (String indexDir : indexDirs) {
          params.add(CoreAdminParams.INDEX_DIR, indexDir);
        }
      }
      if (srcCores != null) {
        for (String srcCore : srcCores) {
          params.add(CoreAdminParams.SRC_CORE, srcCore);
        }
      }
      return params;
    }
  }

  public static class Unload extends CoreAdminRequest {
    protected boolean deleteIndex;
    private boolean deleteDataDir;

    public Unload(boolean deleteIndex) {
      action = CoreAdminAction.UNLOAD;
      this.deleteIndex = deleteIndex;
    }

    public boolean isDeleteIndex() {
      return deleteIndex;
    }

    public void setDeleteIndex(boolean deleteIndex) {
      this.deleteIndex = deleteIndex;
    }

    public void setDeleteDataDir(boolean deleteDataDir) {
     this.deleteDataDir = deleteDataDir; 
    }

    @Override
    public SolrParams getParams() {
      ModifiableSolrParams params = (ModifiableSolrParams) super.getParams();
      params.set(CoreAdminParams.DELETE_INDEX, deleteIndex);
      params.set(CoreAdminParams.DELETE_DATA_DIR, deleteDataDir);
      return params;
    }

  }

  public CoreAdminRequest()
  {
    super( METHOD.GET, "/admin/cores" );
  }

  public CoreAdminRequest( String path )
  {
    super( METHOD.GET, path );
  }

  public final void setCoreName( String coreName )
  {
    this.core = coreName;
  }

  public final void setOtherCoreName( String otherCoreName )
  {
    this.other = otherCoreName;
  }

  public final void setIndexInfoNeeded(boolean isIndexInfoNeeded) {
    this.isIndexInfoNeeded = isIndexInfoNeeded;
  }
  
  //---------------------------------------------------------------------------------------
  //
  //---------------------------------------------------------------------------------------

  public void setAction( CoreAdminAction action )
  {
    this.action = action;
  }

  //---------------------------------------------------------------------------------------
  //
  //---------------------------------------------------------------------------------------

  @Override
  public SolrParams getParams() 
  {
    if( action == null ) {
      throw new RuntimeException( "no action specified!" );
    }
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set( CoreAdminParams.ACTION, action.toString() );
    params.set( CoreAdminParams.CORE, core );
    params.set(CoreAdminParams.INDEX_INFO, (isIndexInfoNeeded ? "true" : "false"));
    if (other != null) {
      params.set(CoreAdminParams.OTHER, other);
    }
    return params;
  }

  //---------------------------------------------------------------------------------------
  //
  //---------------------------------------------------------------------------------------

  @Override
  public Collection<ContentStream> getContentStreams() throws IOException {
    return null;
  }

  @Override
  public CoreAdminResponse process(SolrServer server) throws SolrServerException, IOException 
  {
    long startTime = System.currentTimeMillis();
    CoreAdminResponse res = new CoreAdminResponse();
    res.setResponse( server.request( this ) );
    res.setElapsedTime( System.currentTimeMillis()-startTime );
    return res;
  }

  //---------------------------------------------------------------------------------------
  //
  //---------------------------------------------------------------------------------------

  public static CoreAdminResponse reloadCore( String name, SolrServer server ) throws SolrServerException, IOException
  {
    CoreAdminRequest req = new CoreAdminRequest();
    req.setCoreName( name );
    req.setAction( CoreAdminAction.RELOAD );
    return req.process( server );
  }

  public static CoreAdminResponse unloadCore( String name, SolrServer server ) throws SolrServerException, IOException
  {
    return unloadCore(name, false, server);
  }

  public static CoreAdminResponse unloadCore( String name, boolean deleteIndex, SolrServer server ) throws SolrServerException, IOException
  {
    Unload req = new Unload(deleteIndex);
    req.setCoreName( name );
    return req.process( server );
  }

  public static CoreAdminResponse renameCore(String coreName, String newName, SolrServer server ) throws SolrServerException, IOException
  {
    CoreAdminRequest req = new CoreAdminRequest();
    req.setCoreName(coreName);
    req.setOtherCoreName(newName);
    req.setAction( CoreAdminAction.RENAME );
    return req.process( server );
  }

  public static CoreAdminResponse getStatus( String name, SolrServer server ) throws SolrServerException, IOException
  {
    CoreAdminRequest req = new CoreAdminRequest();
    req.setCoreName( name );
    req.setAction( CoreAdminAction.STATUS );
    return req.process( server );
  }
  
  public static CoreAdminResponse createCore( String name, String instanceDir, SolrServer server ) throws SolrServerException, IOException 
  {
    return CoreAdminRequest.createCore(name, instanceDir, server, null, null);
  }
  
  public static CoreAdminResponse createCore( String name, String instanceDir, SolrServer server, String configFile, String schemaFile ) throws SolrServerException, IOException { 
    return createCore(name, instanceDir, server, configFile, schemaFile, null, null);
  }
  
  public static CoreAdminResponse createCore( String name, String instanceDir, SolrServer server, String configFile, String schemaFile, String dataDir, String tlogDir ) throws SolrServerException, IOException 
  {
    CoreAdminRequest.Create req = new CoreAdminRequest.Create();
    req.setCoreName( name );
    req.setInstanceDir(instanceDir);
    if (dataDir != null) {
      req.setDataDir(dataDir);
    }
    if (tlogDir != null) {
      req.setUlogDir(tlogDir);
    }
    if(configFile != null){
      req.setConfigName(configFile);
    }
    if(schemaFile != null){
      req.setSchemaName(schemaFile);
    }
    return req.process( server );
  }

  @Deprecated
  public static CoreAdminResponse persist(String fileName, SolrServer server) throws SolrServerException, IOException 
  {
    CoreAdminRequest.Persist req = new CoreAdminRequest.Persist();
    req.setFileName(fileName);
    return req.process(server);
  }

  public static CoreAdminResponse mergeIndexes(String name,
      String[] indexDirs, String[] srcCores, SolrServer server) throws SolrServerException,
      IOException {
    CoreAdminRequest.MergeIndexes req = new CoreAdminRequest.MergeIndexes();
    req.setCoreName(name);
    req.setIndexDirs(Arrays.asList(indexDirs));
    req.setSrcCores(Arrays.asList(srcCores));
    return req.process(server);
  }
}
