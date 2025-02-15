  Merged /lucene/dev/branches/branch_4x/solr/core/src/test/org/apache/solr/core/TestConfig.java:r1457294
  Merged /lucene/dev/trunk/solr/core/src/test/org/apache/solr/core/TestConfig.java:r1457292
package org.apache.solr.core;

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext.Context;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.store.RateLimitedDirectoryWrapper;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.apache.lucene.store.SingleInstanceLockFactory;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link DirectoryFactory} impl base class for caching Directory instances
 * per path. Most DirectoryFactory implementations will want to extend this
 * class and simply implement {@link DirectoryFactory#create(String, DirContext)}.
 * 
 */
public abstract class CachingDirectoryFactory extends DirectoryFactory {
  protected class CacheValue {
    public Directory directory;
    public int refCnt = 1;
    public boolean closed;
    public String path;
    public boolean doneWithDir = false;
    @Override
    public String toString() {
      return "CachedDir<<" + directory.toString() + ";refCount=" + refCnt + ";path=" + path + ";done=" + doneWithDir + ">>";
    }
  }
  
  private static Logger log = LoggerFactory
      .getLogger(CachingDirectoryFactory.class);
  
  protected Map<String,CacheValue> byPathCache = new HashMap<String,CacheValue>();
  
  protected Map<Directory,CacheValue> byDirectoryCache = new HashMap<Directory,CacheValue>();
  
  protected Map<Directory,List<CloseListener>> closeListeners = new HashMap<Directory,List<CloseListener>>();

  private Double maxWriteMBPerSecFlush;

  private Double maxWriteMBPerSecMerge;

  private Double maxWriteMBPerSecRead;

  private Double maxWriteMBPerSecDefault;

  private boolean closed;
  
  public interface CloseListener {
    public void postClose();

    public void preClose();
  }
  
  @Override
  public void addCloseListener(Directory dir, CloseListener closeListener) {
    synchronized (this) {
      if (!byDirectoryCache.containsKey(dir)) {
        throw new IllegalArgumentException("Unknown directory: " + dir
            + " " + byDirectoryCache);
      }
      List<CloseListener> listeners = closeListeners.get(dir);
      if (listeners == null) {
        listeners = new ArrayList<CloseListener>();
        closeListeners.put(dir, listeners);
      }
      listeners.add(closeListener);
      
      closeListeners.put(dir, listeners);
    }
  }
  
  @Override
  public void doneWithDirectory(Directory directory) throws IOException {
    synchronized (this) {
      CacheValue cacheValue = byDirectoryCache.get(directory);
      if (cacheValue == null) {
        throw new IllegalArgumentException("Unknown directory: " + directory
            + " " + byDirectoryCache);
      }
      cacheValue.doneWithDir = true;
      if (cacheValue.refCnt == 0) {
        cacheValue.refCnt++; // this will go back to 0 in close
        close(directory);
      }
    }
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see org.apache.solr.core.DirectoryFactory#close()
   */
  @Override
  public void close() throws IOException {
    synchronized (this) {
      this.closed = true;
      for (CacheValue val : byDirectoryCache.values()) {
        try {
          // if there are still refs out, we have to wait for them
          int cnt = 0;
          while(val.refCnt != 0) {
            wait(100);
            
            if (cnt++ >= 1200) {
              log.error("Timeout waiting for all directory ref counts to be released");
              break;
            }
          }
          
          assert val.refCnt == 0 : val.refCnt;
          log.info("Closing directory when closing factory: " + val.path);
          closeDirectory(val);
        } catch (Throwable t) {
          SolrException.log(log, "Error closing directory", t);
        }
      }
      byDirectoryCache.clear();
      byPathCache.clear();
    }
  }
  
  private void close(Directory directory) throws IOException {
    synchronized (this) {
      // don't check if already closed here - we need to able to release
      // while #close() waits.
      
      CacheValue cacheValue = byDirectoryCache.get(directory);
      if (cacheValue == null) {
        throw new IllegalArgumentException("Unknown directory: " + directory
            + " " + byDirectoryCache);
      }
      log.debug("Releasing directory: " + cacheValue.path);

      cacheValue.refCnt--;

      if (cacheValue.refCnt == 0 && cacheValue.doneWithDir) {
        closeDirectory(cacheValue);
        
        byDirectoryCache.remove(directory);
        
        // if it's been closed, it's path is now
        // owned by another Directory instance
        if (!cacheValue.closed) {
          byPathCache.remove(cacheValue.path);
        }
      }
    }
  }

  private void closeDirectory(CacheValue cacheValue) {
    List<CloseListener> listeners = closeListeners.remove(cacheValue.directory);
    if (listeners != null) {
      for (CloseListener listener : listeners) {
        try {
          listener.preClose();
        } catch (Throwable t) {
          SolrException.log(log, "Error executing preClose for directory", t);
        }
      }
    }
    try {
      log.info("Closing directory: " + cacheValue.path);
      cacheValue.directory.close();
    } catch (Throwable t) {
      SolrException.log(log, "Error closing directory", t);
    }
    
    if (listeners != null) {
      for (CloseListener listener : listeners) {
        try {
          listener.postClose();
        } catch (Throwable t) {
          SolrException.log(log, "Error executing postClose for directory", t);
        }
      }
    }
  }
  
  @Override
  protected abstract Directory create(String path, DirContext dirContext) throws IOException;
  
  @Override
  public boolean exists(String path) throws IOException {
    // back compat behavior
    File dirFile = new File(path);
    return dirFile.canRead() && dirFile.list().length > 0;
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see org.apache.solr.core.DirectoryFactory#get(java.lang.String,
   * java.lang.String)
   */
  @Override
  public final Directory get(String path,  DirContext dirContext, String rawLockType)
      throws IOException {
    return get(path, dirContext, rawLockType, false);
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see org.apache.solr.core.DirectoryFactory#get(java.lang.String,
   * java.lang.String, boolean)
   */
  @Override
  public final Directory get(String path,  DirContext dirContext, String rawLockType, boolean forceNew)
      throws IOException {
    String fullPath = normalize(path);
    synchronized (this) {
      if (closed) {
        throw new RuntimeException("Already closed");
      }
      
      final CacheValue cacheValue = byPathCache.get(fullPath);
      Directory directory = null;
      if (cacheValue != null) {
        directory = cacheValue.directory;
        if (forceNew) {
          cacheValue.doneWithDir = true;
          
          // we make a quick close attempt,
          // otherwise this should be closed
          // when whatever is using it, releases it
          
          if (cacheValue.refCnt == 0) {
            try {
              // the following will decref, so
              // first incref
              cacheValue.refCnt++;
              close(cacheValue.directory);
            } catch (IOException e) {
              SolrException.log(log, "Error closing directory", e);
            }
          }
          
          // close the entry, it will be owned by the new dir
          // we count on it being released by directory
          cacheValue.closed = true;
          
        }
      }
      
      if (directory == null || forceNew) { 
        directory = create(fullPath, dirContext);
        
        directory = rateLimit(directory);
        
        CacheValue newCacheValue = new CacheValue();
        newCacheValue.directory = directory;
        newCacheValue.path = fullPath;
        
        injectLockFactory(directory, fullPath, rawLockType);
        
        byDirectoryCache.put(directory, newCacheValue);
        byPathCache.put(fullPath, newCacheValue);
        log.info("return new directory for " + fullPath + " forceNew: " + forceNew);
      } else {
        cacheValue.refCnt++;
      }
      
      return directory;
    }
  }

  private Directory rateLimit(Directory directory) {
    if (maxWriteMBPerSecDefault != null || maxWriteMBPerSecFlush != null || maxWriteMBPerSecMerge != null || maxWriteMBPerSecRead != null) {
      directory = new RateLimitedDirectoryWrapper(directory);
      if (maxWriteMBPerSecDefault != null) {
        ((RateLimitedDirectoryWrapper)directory).setMaxWriteMBPerSec(maxWriteMBPerSecDefault, Context.DEFAULT);
      }
      if (maxWriteMBPerSecFlush != null) {
        ((RateLimitedDirectoryWrapper)directory).setMaxWriteMBPerSec(maxWriteMBPerSecFlush, Context.FLUSH);
      }
      if (maxWriteMBPerSecMerge != null) {
        ((RateLimitedDirectoryWrapper)directory).setMaxWriteMBPerSec(maxWriteMBPerSecMerge, Context.MERGE);
      }
      if (maxWriteMBPerSecRead != null) {
        ((RateLimitedDirectoryWrapper)directory).setMaxWriteMBPerSec(maxWriteMBPerSecRead, Context.READ);
      }
    }
    return directory;
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.solr.core.DirectoryFactory#incRef(org.apache.lucene.store.Directory
   * )
   */
  @Override
  public void incRef(Directory directory) {
    synchronized (this) {
      CacheValue cacheValue = byDirectoryCache.get(directory);
      if (cacheValue == null) {
        throw new IllegalArgumentException("Unknown directory: " + directory);
      }
      
      cacheValue.refCnt++;
    }
  }
  
  @Override
  public void init(NamedList args) {
    maxWriteMBPerSecFlush = (Double) args.get("maxWriteMBPerSecFlush");
    maxWriteMBPerSecMerge = (Double) args.get("maxWriteMBPerSecMerge");
    maxWriteMBPerSecRead = (Double) args.get("maxWriteMBPerSecRead");
    maxWriteMBPerSecDefault = (Double) args.get("maxWriteMBPerSecDefault");
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.solr.core.DirectoryFactory#release(org.apache.lucene.store.Directory
   * )
   */
  @Override
  public void release(Directory directory) throws IOException {
    if (directory == null) {
      throw new NullPointerException();
    }
    close(directory);
  }
  
  private static Directory injectLockFactory(Directory dir, String lockPath,
      String rawLockType) throws IOException {
    if (null == rawLockType) {
      // we default to "simple" for backwards compatibility
      log.warn("No lockType configured for " + dir + " assuming 'simple'");
      rawLockType = "simple";
    }
    final String lockType = rawLockType.toLowerCase(Locale.ROOT).trim();
    
    if ("simple".equals(lockType)) {
      // multiple SimpleFSLockFactory instances should be OK
      dir.setLockFactory(new SimpleFSLockFactory(lockPath));
    } else if ("native".equals(lockType)) {
      dir.setLockFactory(new NativeFSLockFactory(lockPath));
    } else if ("single".equals(lockType)) {
      if (!(dir.getLockFactory() instanceof SingleInstanceLockFactory)) dir
          .setLockFactory(new SingleInstanceLockFactory());
    } else if ("none".equals(lockType)) {
      // Recipe for disaster
      log.error("CONFIGURATION WARNING: locks are disabled on " + dir);
      dir.setLockFactory(NoLockFactory.getNoLockFactory());
    } else {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
          "Unrecognized lockType: " + rawLockType);
    }
    return dir;
  }
  
  protected String stripTrailingSlash(String path) {
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path;
  }
}
