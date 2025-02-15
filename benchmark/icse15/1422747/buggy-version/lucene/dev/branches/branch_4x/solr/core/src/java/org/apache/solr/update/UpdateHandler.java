  Merged /lucene/dev/trunk/lucene/licenses:r1422746
  Merged /lucene/dev/trunk/lucene/memory:r1422746
  Merged /lucene/dev/trunk/lucene/JRE_VERSION_MIGRATION.txt:r1422746
  Merged /lucene/dev/trunk/lucene/BUILD.txt:r1422746
  Merged /lucene/dev/trunk/lucene/suggest:r1422746
  Merged /lucene/dev/trunk/lucene/analysis/icu/src/java/org/apache/lucene/collation/ICUCollationKeyFilterFactory.java:r1422746
  Merged /lucene/dev/trunk/lucene/analysis:r1422746
  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1422746
  Merged /lucene/dev/trunk/lucene/grouping:r1422746
  Merged /lucene/dev/trunk/lucene/misc:r1422746
  Merged /lucene/dev/trunk/lucene/sandbox:r1422746
  Merged /lucene/dev/trunk/lucene/highlighter:r1422746
  Merged /lucene/dev/trunk/lucene/NOTICE.txt:r1422746
  Merged /lucene/dev/trunk/lucene/codecs:r1422746
  Merged /lucene/dev/trunk/lucene/LICENSE.txt:r1422746
  Merged /lucene/dev/trunk/lucene/ivy-settings.xml:r1422746
  Merged /lucene/dev/trunk/lucene/SYSTEM_REQUIREMENTS.txt:r1422746
  Merged /lucene/dev/trunk/lucene/MIGRATE.txt:r1422746
  Merged /lucene/dev/trunk/lucene/test-framework:r1422746
  Merged /lucene/dev/trunk/lucene/README.txt:r1422746
  Merged /lucene/dev/trunk/lucene/queries:r1422746
  Merged /lucene/dev/trunk/lucene/module-build.xml:r1422746
  Merged /lucene/dev/trunk/lucene/facet:r1422746
  Merged /lucene/dev/trunk/lucene/queryparser:r1422746
  Merged /lucene/dev/trunk/lucene/common-build.xml:r1422746
  Merged /lucene/dev/trunk/lucene/demo:r1422746
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.nocfs.zip:r1422746
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.nocfs.zip:r1422746
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.cfs.zip:r1422746
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/TestBackwardsCompatibility.java:r1422746
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.cfs.zip:r1422746
  Merged /lucene/dev/trunk/lucene/core:r1422746
  Merged /lucene/dev/trunk/lucene/benchmark:r1422746
  Merged /lucene/dev/trunk/lucene/spatial:r1422746
  Merged /lucene/dev/trunk/lucene/build.xml:r1422746
  Merged /lucene/dev/trunk/lucene/join:r1422746
  Merged /lucene/dev/trunk/lucene/tools:r1422746
  Merged /lucene/dev/trunk/lucene/backwards:r1422746
  Merged /lucene/dev/trunk/lucene/site:r1422746
  Merged /lucene/dev/trunk/lucene:r1422746
  Merged /lucene/dev/trunk/dev-tools:r1422746
  Merged /lucene/dev/trunk/solr/site:r1422746
  Merged /lucene/dev/trunk/solr/SYSTEM_REQUIREMENTS.txt:r1422746
  Merged /lucene/dev/trunk/solr/licenses/httpcore-LICENSE-ASL.txt:r1422746
  Merged /lucene/dev/trunk/solr/licenses/httpclient-NOTICE.txt:r1422746
  Merged /lucene/dev/trunk/solr/licenses/httpcore-NOTICE.txt:r1422746
  Merged /lucene/dev/trunk/solr/licenses/httpmime-NOTICE.txt:r1422746
  Merged /lucene/dev/trunk/solr/licenses/httpmime-LICENSE-ASL.txt:r1422746
  Merged /lucene/dev/trunk/solr/licenses/httpclient-LICENSE-ASL.txt:r1422746
  Merged /lucene/dev/trunk/solr/licenses:r1422746
  Merged /lucene/dev/trunk/solr/test-framework:r1422746
  Merged /lucene/dev/trunk/solr/README.txt:r1422746
  Merged /lucene/dev/trunk/solr/webapp:r1422746
  Merged /lucene/dev/trunk/solr/testlogging.properties:r1422746
  Merged /lucene/dev/trunk/solr/cloud-dev:r1422746
  Merged /lucene/dev/trunk/solr/common-build.xml:r1422746
  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1422746
  Merged /lucene/dev/trunk/solr/scripts:r1422746
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

package org.apache.solr.update;


import java.io.IOException;
import java.util.Vector;

import org.apache.commons.io.FileUtils;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrEventListener;
import org.apache.solr.core.SolrInfoMBean;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>UpdateHandler</code> handles requests to change the index
 * (adds, deletes, commits, optimizes, etc).
 *
 *
 * @since solr 0.9
 */

public abstract class UpdateHandler implements SolrInfoMBean {
  protected final static Logger log = LoggerFactory.getLogger(UpdateHandler.class);

  protected final SolrCore core;
  protected final IndexSchema schema;

  protected final SchemaField idField;
  protected final FieldType idFieldType;

  protected Vector<SolrEventListener> commitCallbacks = new Vector<SolrEventListener>();
  protected Vector<SolrEventListener> softCommitCallbacks = new Vector<SolrEventListener>();
  protected Vector<SolrEventListener> optimizeCallbacks = new Vector<SolrEventListener>();

  protected volatile UpdateLog ulog;

  private void parseEventListeners() {
    final Class<SolrEventListener> clazz = SolrEventListener.class;
    final String label = "Event Listener";
    for (PluginInfo info : core.getSolrConfig().getPluginInfos(SolrEventListener.class.getName())) {
      String event = info.attributes.get("event");
      if ("postCommit".equals(event)) {
        SolrEventListener obj = core.createInitInstance(info,clazz,label,null);
        commitCallbacks.add(obj);
        log.info("added SolrEventListener for postCommit: " + obj);
      } else if ("postOptimize".equals(event)) {
        SolrEventListener obj = core.createInitInstance(info,clazz,label,null);
        optimizeCallbacks.add(obj);
        log.info("added SolrEventListener for postOptimize: " + obj);
      }
    }
  }


  private void initLog() {
    PluginInfo ulogPluginInfo = core.getSolrConfig().getPluginInfo(UpdateLog.class.getName());
    if (ulogPluginInfo != null && ulogPluginInfo.isEnabled()) {
      ulog = new UpdateLog();
      ulog.init(ulogPluginInfo);
      // ulog = core.createInitInstance(ulogPluginInfo, UpdateLog.class, "update log", "solr.NullUpdateLog");
      ulog.init(this, core);
    }
  }

  // not thread safe - for startup
  protected void clearLog() throws IOException {
    if (ulog != null) {
      ulog.close(false, true);
      //FileUtils.deleteDirectory(ulog.getLogDir());
      initLog();
    }
  }

  protected void callPostCommitCallbacks() {
    for (SolrEventListener listener : commitCallbacks) {
      listener.postCommit();
    }
  }

  protected void callPostSoftCommitCallbacks() {
    for (SolrEventListener listener : softCommitCallbacks) {
      listener.postSoftCommit();
    }
  }  
  
  protected void callPostOptimizeCallbacks() {
    for (SolrEventListener listener : optimizeCallbacks) {
      listener.postCommit();
    }
  }

  public UpdateHandler(SolrCore core)  {
    this.core=core;
    schema = core.getSchema();
    idField = schema.getUniqueKeyField();
    idFieldType = idField!=null ? idField.getType() : null;
    parseEventListeners();
    initLog();
    if (!core.isReloaded() && !core.getDirectoryFactory().isPersistent()) {
      try {
        clearLog();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Called when the Writer should be opened again - eg when replication replaces
   * all of the index files.
   * 
   * @param rollback IndexWriter if true else close
   * @param forceNewDir Force a new Directory instance
   * 
   * @throws IOException If there is a low-level I/O error.
   */
  public abstract void newIndexWriter(boolean rollback, boolean forceNewDir) throws IOException;

  public abstract SolrCoreState getSolrCoreState();

  public abstract int addDoc(AddUpdateCommand cmd) throws IOException;
  public abstract void delete(DeleteUpdateCommand cmd) throws IOException;
  public abstract void deleteByQuery(DeleteUpdateCommand cmd) throws IOException;
  public abstract int mergeIndexes(MergeIndexesCommand cmd) throws IOException;
  public abstract void commit(CommitUpdateCommand cmd) throws IOException;
  public abstract void rollback(RollbackUpdateCommand cmd) throws IOException;
  public abstract void close() throws IOException;
  public abstract UpdateLog getUpdateLog();

  /**
   * NOTE: this function is not thread safe.  However, it is safe to call within the
   * <code>inform( SolrCore core )</code> function for <code>SolrCoreAware</code> classes.
   * Outside <code>inform</code>, this could potentially throw a ConcurrentModificationException
   *
   * @see SolrCoreAware
   */
  public void registerCommitCallback( SolrEventListener listener )
  {
    commitCallbacks.add( listener );
  }
  
  /**
   * NOTE: this function is not thread safe.  However, it is safe to call within the
   * <code>inform( SolrCore core )</code> function for <code>SolrCoreAware</code> classes.
   * Outside <code>inform</code>, this could potentially throw a ConcurrentModificationException
   *
   * @see SolrCoreAware
   */
  public void registerSoftCommitCallback( SolrEventListener listener )
  {
    softCommitCallbacks.add( listener );
  }

  /**
   * NOTE: this function is not thread safe.  However, it is safe to call within the
   * <code>inform( SolrCore core )</code> function for <code>SolrCoreAware</code> classes.
   * Outside <code>inform</code>, this could potentially throw a ConcurrentModificationException
   *
   * @see SolrCoreAware
   */
  public void registerOptimizeCallback( SolrEventListener listener )
  {
    optimizeCallbacks.add( listener );
  }

  public abstract void split(SplitIndexCommand cmd) throws IOException;
}
