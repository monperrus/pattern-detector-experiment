  Merged /lucene/dev/trunk/solr/CHANGES.txt:r1412140
  Merged /lucene/dev/trunk/solr/scripts:r1412140
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.cloud;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A distributed queue from zk recipes.
 */
public class DistributedQueue {
  private static final Logger LOG = LoggerFactory
      .getLogger(DistributedQueue.class);
  
  private final String dir;
  
  private SolrZkClient zookeeper;
  private List<ACL> acl = ZooDefs.Ids.OPEN_ACL_UNSAFE;
  
  private final String prefix = "qn-";
  
  public DistributedQueue(SolrZkClient zookeeper, String dir, List<ACL> acl) {
    this.dir = dir;
    
    if (acl != null) {
      this.acl = acl;
    }
    this.zookeeper = zookeeper;
    
  }
  
  /**
   * Returns a Map of the children, ordered by id.
   * 
   * @param watcher
   *          optional watcher on getChildren() operation.
   * @return map from id to child name for all children
   */
  private TreeMap<Long,String> orderedChildren(Watcher watcher)
      throws KeeperException, InterruptedException {
    TreeMap<Long,String> orderedChildren = new TreeMap<Long,String>();
    
    List<String> childNames = null;
    try {
      childNames = zookeeper.getChildren(dir, watcher, true);
    } catch (KeeperException.NoNodeException e) {
      throw e;
    }
    
    for (String childName : childNames) {
      try {
        // Check format
        if (!childName.regionMatches(0, prefix, 0, prefix.length())) {
          LOG.warn("Found child node with improper name: " + childName);
          continue;
        }
        String suffix = childName.substring(prefix.length());
        Long childId = new Long(suffix);
        orderedChildren.put(childId, childName);
      } catch (NumberFormatException e) {
        LOG.warn("Found child node with improper format : " + childName + " "
            + e, e);
      }
    }
    
    return orderedChildren;
  }
  
  /**
   * Return the head of the queue without modifying the queue.
   * 
   * @return the data at the head of the queue.
   */
  public byte[] element() throws NoSuchElementException, KeeperException,
      InterruptedException {
    TreeMap<Long,String> orderedChildren;
    
    // element, take, and remove follow the same pattern.
    // We want to return the child node with the smallest sequence number.
    // Since other clients are remove()ing and take()ing nodes concurrently,
    // the child with the smallest sequence number in orderedChildren might be
    // gone by the time we check.
    // We don't call getChildren again until we have tried the rest of the nodes
    // in sequence order.
    while (true) {
      try {
        orderedChildren = orderedChildren(null);
      } catch (KeeperException.NoNodeException e) {
        throw new NoSuchElementException();
      }
      if (orderedChildren.size() == 0) throw new NoSuchElementException();
      
      for (String headNode : orderedChildren.values()) {
        if (headNode != null) {
          try {
            return zookeeper.getData(dir + "/" + headNode, null, null, true);
          } catch (KeeperException.NoNodeException e) {
            // Another client removed the node first, try next
          }
        }
      }
    }
  }
  
  /**
   * Attempts to remove the head of the queue and return it.
   * 
   * @return The former head of the queue
   */
  public byte[] remove() throws NoSuchElementException, KeeperException,
      InterruptedException {
    TreeMap<Long,String> orderedChildren;
    // Same as for element. Should refactor this.
    while (true) {
      try {
        orderedChildren = orderedChildren(null);
      } catch (KeeperException.NoNodeException e) {
        throw new NoSuchElementException();
      }
      if (orderedChildren.size() == 0) throw new NoSuchElementException();
      
      for (String headNode : orderedChildren.values()) {
        String path = dir + "/" + headNode;
        try {
          byte[] data = zookeeper.getData(path, null, null, true);
          zookeeper.delete(path, -1, true);
          return data;
        } catch (KeeperException.NoNodeException e) {
          // Another client deleted the node first.
        }
      }
      
    }
  }
  
  private class LatchChildWatcher implements Watcher {
    
    CountDownLatch latch;
    
    public LatchChildWatcher() {
      latch = new CountDownLatch(1);
    }
    
    public void process(WatchedEvent event) {
      LOG.debug("Watcher fired on path: " + event.getPath() + " state: "
          + event.getState() + " type " + event.getType());
      latch.countDown();
    }
    
    public void await() throws InterruptedException {
      latch.await();
    }
  }
  
  /**
   * Removes the head of the queue and returns it, blocks until it succeeds.
   * 
   * @return The former head of the queue
   */
  public byte[] take() throws KeeperException, InterruptedException {
    TreeMap<Long,String> orderedChildren;
    // Same as for element. Should refactor this.
    while (true) {
      LatchChildWatcher childWatcher = new LatchChildWatcher();
      try {
        orderedChildren = orderedChildren(childWatcher);
      } catch (KeeperException.NoNodeException e) {
        zookeeper.create(dir, new byte[0], acl, CreateMode.PERSISTENT, true);
        continue;
      }
      if (orderedChildren.size() == 0) {
        childWatcher.await();
        continue;
      }
      
      for (String headNode : orderedChildren.values()) {
        String path = dir + "/" + headNode;
        try {
          byte[] data = zookeeper.getData(path, null, null, true);
          zookeeper.delete(path, -1, true);
          return data;
        } catch (KeeperException.NoNodeException e) {
          // Another client deleted the node first.
        }
      }
    }
  }
  
  /**
   * Inserts data into queue.
   * 
   * @return true if data was successfully added
   */
  public boolean offer(byte[] data) throws KeeperException,
      InterruptedException {
    for (;;) {
      try {
        zookeeper.create(dir + "/" + prefix, data, acl,
            CreateMode.PERSISTENT_SEQUENTIAL, true);
        return true;
      } catch (KeeperException.NoNodeException e) {
        try {
          zookeeper.create(dir, new byte[0], acl, CreateMode.PERSISTENT, true);
        } catch (KeeperException.NodeExistsException ne) {
        //someone created it
        }
      }
    }

    
    
  }
  
  /**
   * Returns the data at the first element of the queue, or null if the queue is
   * empty.
   * 
   * @return data at the first element of the queue, or null.
   */
  public byte[] peek() throws KeeperException, InterruptedException {
    try {
      return element();
    } catch (NoSuchElementException e) {
      return null;
    }
  }
  
  /**
   * Returns the data at the first element of the queue, or null if the queue is
   * empty.
   * 
   * @return data at the first element of the queue, or null.
   */
  public byte[] peek(boolean block) throws KeeperException, InterruptedException {
    if (!block) {
      return peek();
    }
    
    TreeMap<Long,String> orderedChildren;
    while (true) {
      LatchChildWatcher childWatcher = new LatchChildWatcher();
      try {
        orderedChildren = orderedChildren(childWatcher);
      } catch (KeeperException.NoNodeException e) {
        zookeeper.create(dir, new byte[0], acl, CreateMode.PERSISTENT, true);
        continue;
      }
      if (orderedChildren.size() == 0) {
        childWatcher.await();
        continue;
      }
      
      for (String headNode : orderedChildren.values()) {
        String path = dir + "/" + headNode;
        try {
          byte[] data = zookeeper.getData(path, null, null, true);
          return data;
        } catch (KeeperException.NoNodeException e) {
          // Another client deleted the node first.
        }
      }
    }
  }
  
  /**
   * Attempts to remove the head of the queue and return it. Returns null if the
   * queue is empty.
   * 
   * @return Head of the queue or null.
   */
  public byte[] poll() throws KeeperException, InterruptedException {
    try {
      return remove();
    } catch (NoSuchElementException e) {
      return null;
    }
  }
  
}
