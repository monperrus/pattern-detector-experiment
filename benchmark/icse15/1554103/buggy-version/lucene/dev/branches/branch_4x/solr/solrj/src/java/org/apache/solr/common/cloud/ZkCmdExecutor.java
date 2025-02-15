  Merged /lucene/dev/trunk/solr/core:r1554101
package org.apache.solr.common.cloud;

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

import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;


public class ZkCmdExecutor {
  private long retryDelay = 1500L; // 500 ms over for padding
  private int retryCount;
  private List<ACL> acl = ZooDefs.Ids.OPEN_ACL_UNSAFE;
  
  public ZkCmdExecutor(int timeoutms) {
    double timeouts = timeoutms / 1000.0;
    this.retryCount = Math.round(0.5f * ((float)Math.sqrt(8.0f * timeouts + 1.0f) - 1.0f));
  }
  
  public List<ACL> getAcl() {
    return acl;
  }
  
  public void setAcl(List<ACL> acl) {
    this.acl = acl;
  }
  
  public long getRetryDelay() {
    return retryDelay;
  }
  
  public void setRetryDelay(long retryDelay) {
    this.retryDelay = retryDelay;
  }
  

  /**
   * Perform the given operation, retrying if the connection fails
   */
  @SuppressWarnings("unchecked")
  public <T> T retryOperation(ZkOperation operation)
      throws KeeperException, InterruptedException {
    KeeperException exception = null;
    for (int i = 0; i < retryCount; i++) {
      try {
        return (T) operation.execute();
      } catch (KeeperException.ConnectionLossException e) {
        if (exception == null) {
          exception = e;
        }
        if (Thread.currentThread().isInterrupted()) {
          Thread.currentThread().interrupt();
          throw new InterruptedException();
        }
        if (Thread.currentThread() instanceof ClosableThread) {
          if (((ClosableThread) Thread.currentThread()).isClosed()) {
            throw exception;
          }
        }
        retryDelay(i);
      }
    }
    throw exception;
  }
  
  public void ensureExists(String path, final SolrZkClient zkClient) throws KeeperException, InterruptedException {
    ensureExists(path, null, CreateMode.PERSISTENT, zkClient);
  }
  
  public void ensureExists(final String path, final byte[] data,
      CreateMode createMode, final SolrZkClient zkClient) throws KeeperException, InterruptedException {
    
    if (zkClient.exists(path, true)) {
      return;
    }
    try {
      zkClient.makePath(path, data, true);
    } catch (NodeExistsException e) {
      // its okay if another beats us creating the node
    }
    
  }
  
  /**
   * Performs a retry delay if this is not the first attempt
   * 
   * @param attemptCount
   *          the number of the attempts performed so far
   */
  protected void retryDelay(int attemptCount) throws InterruptedException {
    if (attemptCount > 0) {
      Thread.sleep(attemptCount * retryDelay);
    }
  }

}
