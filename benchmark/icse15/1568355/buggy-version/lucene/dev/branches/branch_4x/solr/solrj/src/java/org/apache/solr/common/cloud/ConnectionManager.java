  Merged /lucene/dev/trunk/solr/core:r1568353
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

import java.util.concurrent.TimeoutException;

import org.apache.solr.common.SolrException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionManager implements Watcher {
  protected static final Logger log = LoggerFactory
      .getLogger(ConnectionManager.class);

  private final String name;
  
  private volatile boolean connected = false;

  private final ZkClientConnectionStrategy connectionStrategy;

  private final String zkServerAddress;

  private final SolrZkClient client;

  private final OnReconnect onReconnect;
  private final BeforeReconnect beforeReconnect;

  private volatile boolean isClosed = false;

  // Track the likely expired state
  private static class LikelyExpiredState {
    private static LikelyExpiredState NOT_EXPIRED = new LikelyExpiredState(StateType.NOT_EXPIRED, 0);
    private static LikelyExpiredState EXPIRED = new LikelyExpiredState(StateType.EXPIRED, 0);

    public enum StateType {
      NOT_EXPIRED,    // definitely not expired
      EXPIRED,        // definitely expired
      TRACKING_TIME   // not sure, tracking time of last disconnect
    }

    private StateType stateType;
    private long lastDisconnectTime;
    public LikelyExpiredState(StateType stateType, long lastDisconnectTime) {
      this.stateType = stateType;
      this.lastDisconnectTime = lastDisconnectTime;
    }

    public boolean isLikelyExpired(long timeToExpire) {
      return stateType == StateType.EXPIRED
        || ( stateType == StateType.TRACKING_TIME && (System.currentTimeMillis() - lastDisconnectTime >  timeToExpire));
    }
  }

  private volatile LikelyExpiredState likelyExpiredState = LikelyExpiredState.EXPIRED;

  public ConnectionManager(String name, SolrZkClient client, String zkServerAddress, ZkClientConnectionStrategy strat, OnReconnect onConnect, BeforeReconnect beforeReconnect) {
    this.name = name;
    this.client = client;
    this.connectionStrategy = strat;
    this.zkServerAddress = zkServerAddress;
    this.onReconnect = onConnect;
    this.beforeReconnect = beforeReconnect;
  }
  
  private synchronized void connected() {
    connected = true;
    likelyExpiredState = LikelyExpiredState.NOT_EXPIRED;
    notifyAll();
  }

  private synchronized void disconnected() {
    connected = false;
    // record the time we expired unless we are already likely expired
    if (!likelyExpiredState.isLikelyExpired(0)) {
      likelyExpiredState = new LikelyExpiredState(LikelyExpiredState.StateType.TRACKING_TIME, System.currentTimeMillis());
    }
    notifyAll();
  }

  @Override
  public void process(WatchedEvent event) {
    if (log.isInfoEnabled()) {
      log.info("Watcher " + this + " name:" + name + " got event " + event
          + " path:" + event.getPath() + " type:" + event.getType());
    }
    
    if (isClosed) {
      log.info("Client->ZooKeeper status change trigger but we are already closed");
      return;
    }
    
    KeeperState state = event.getState();
    
    if (state == KeeperState.SyncConnected) {
      connected();
      connectionStrategy.connected();
    } else if (state == KeeperState.Expired) {
      // we don't call disconnected here, because we know we are expired
      connected = false;
      likelyExpiredState = LikelyExpiredState.EXPIRED;
      
      log.info("Our previous ZooKeeper session was expired. Attempting to reconnect to recover relationship with ZooKeeper...");
      
      if (beforeReconnect != null) {
        beforeReconnect.command();
      }
      
      try {
        connectionStrategy.reconnect(zkServerAddress,
            client.getZkClientTimeout(), this,
            new ZkClientConnectionStrategy.ZkUpdate() {
              @Override
              public void update(SolrZooKeeper keeper) {
                try {
                  waitForConnected(Long.MAX_VALUE);
                } catch (Exception e1) {
                  closeKeeper(keeper);
                  throw new RuntimeException(e1);
                }
                
                log.info("Connection with ZooKeeper reestablished.");
                try {
                  client.updateKeeper(keeper);
                } catch (InterruptedException e) {
                  closeKeeper(keeper);
                  Thread.currentThread().interrupt();
                  // we must have been asked to stop
                  throw new RuntimeException(e);
                } catch (Exception t) {
                  closeKeeper(keeper);
                  throw new RuntimeException(t);
                }

                // The new event thread will call connected
                // we just start the reconnect thread and 
                // do nothing else
                
                if (onReconnect != null) {
                  Thread thread = new Thread() {
                    @Override
                    public void run() {
                      try {
                        onReconnect.command();
                      } catch (Exception e) {
                        log.warn("Exception running onReconnect command", e);
                      }
                    }
                  };
                  thread.start();
                }
                
              }
            });
      } catch (Exception e) {
        SolrException.log(log, "", e);
      }
      log.info("Connected:" + connected);
    } else if (state == KeeperState.Disconnected) {
      log.info("zkClient has disconnected");
      disconnected();
      connectionStrategy.disconnected();
    } else if (state == KeeperState.AuthFailed) {
      log.warn("zkClient received AuthFailed");
    }
  }

  public synchronized boolean isConnected() {
    return !isClosed && connected;
  }
  
  // we use a volatile rather than sync
  // to avoid possible deadlock on shutdown
  public void close() {
    this.isClosed = true;
    this.likelyExpiredState = LikelyExpiredState.EXPIRED;
  }
  
  public boolean isLikelyExpired() {
    return isClosed || likelyExpiredState.isLikelyExpired((long) (client.getZkClientTimeout() * 0.90));
  }

  public synchronized void waitForConnected(long waitForConnection)
      throws TimeoutException {
    log.info("Waiting for client to connect to ZooKeeper");
    long expire = System.currentTimeMillis() + waitForConnection;
    long left = 1;
    while (!connected && left > 0) {
      if (isClosed) {
        break;
      }
      try {
        wait(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      left = expire - System.currentTimeMillis();
    }
    if (!connected) {
      throw new TimeoutException("Could not connect to ZooKeeper " + zkServerAddress + " within " + waitForConnection + " ms");
    }
    log.info("Client is connected to ZooKeeper");
  }

  public synchronized void waitForDisconnected(long timeout)
      throws InterruptedException, TimeoutException {
    long expire = System.currentTimeMillis() + timeout;
    long left = timeout;
    while (connected && left > 0) {
      wait(left);
      left = expire - System.currentTimeMillis();
    }
    if (connected) {
      throw new TimeoutException("Did not disconnect");
    }
  }

  private void closeKeeper(SolrZooKeeper keeper) {
    try {
      keeper.close();
    } catch (InterruptedException e) {
      // Restore the interrupted status
      Thread.currentThread().interrupt();
      log.error("", e);
      throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
          "", e);
    }
  }
}
