/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.google.common.collect.Iterables;

import org.apache.cassandra.cache.JMXInstrumentedCacheMBean;
import org.apache.cassandra.concurrent.IExecutorMBean;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.db.ColumnFamilyStoreMBean;
import org.apache.cassandra.db.CompactionManager;
import org.apache.cassandra.db.CompactionManagerMBean;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.net.MessagingServiceMBean;
import org.apache.cassandra.service.StorageServiceMBean;
import org.apache.cassandra.streaming.StreamingService;
import org.apache.cassandra.streaming.StreamingServiceMBean;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.cassandra.utils.FBUtilities;

/**
 * JMX client operations for Cassandra.
 */
public class NodeProbe
{
    private static final String fmtUrl = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
    private static final String ssObjName = "org.apache.cassandra.db:type=StorageService";
    private static final int defaultPort = 8080;
    final String host;
    final int port;
    private String username;
    private String password;

    private JMXConnector jmxc;
    private MBeanServerConnection mbeanServerConn;
    private CompactionManagerMBean compactionProxy;
    private StorageServiceMBean ssProxy;
    private MemoryMXBean memProxy;
    private RuntimeMXBean runtimeProxy;
    private StreamingServiceMBean streamProxy;
    
    /**
     * Creates a NodeProbe using the specified JMX host, port, username, and password.
     *
     * @param host hostname or IP address of the JMX agent
     * @param port TCP port of the remote JMX agent
     * @throws IOException on connection failures
     */
    public NodeProbe(String host, int port, String username, String password) throws IOException, InterruptedException
    {
        assert username != null && !username.isEmpty() && null != password && !password.isEmpty()
               : "neither username nor password can be blank";

        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        connect();
    }

    /**
     * Creates a NodeProbe using the specified JMX host and port.
     * 
     * @param host hostname or IP address of the JMX agent
     * @param port TCP port of the remote JMX agent
     * @throws IOException on connection failures
     */
    public NodeProbe(String host, int port) throws IOException, InterruptedException
    {
        this.host = host;
        this.port = port;
        connect();
    }
    
    /**
     * Creates a NodeProbe using the specified JMX host and default port.
     * 
     * @param host hostname or IP address of the JMX agent
     * @throws IOException on connection failures
     */
    public NodeProbe(String host) throws IOException, InterruptedException
    {
        this.host = host;
        this.port = defaultPort;
        connect();
    }
    
    /**
     * Create a connection to the JMX agent and setup the M[X]Bean proxies.
     * 
     * @throws IOException on connection failures
     */
    private void connect() throws IOException
    {
        JMXServiceURL jmxUrl = new JMXServiceURL(String.format(fmtUrl, host, port));
        Map<String,Object> env = new HashMap<String,Object>();
        if (username != null)
        {
            String[] creds = { username, password };
            env.put(JMXConnector.CREDENTIALS, creds);
        }
        jmxc = JMXConnectorFactory.connect(jmxUrl, env);
        mbeanServerConn = jmxc.getMBeanServerConnection();
        
        try
        {
            ObjectName name = new ObjectName(ssObjName);
            ssProxy = JMX.newMBeanProxy(mbeanServerConn, name, StorageServiceMBean.class);
            name = new ObjectName(StreamingService.MBEAN_OBJECT_NAME);
            streamProxy = JMX.newMBeanProxy(mbeanServerConn, name, StreamingServiceMBean.class);
            name = new ObjectName(CompactionManager.MBEAN_OBJECT_NAME);
            compactionProxy = JMX.newMBeanProxy(mbeanServerConn, name, CompactionManagerMBean.class);
        } catch (MalformedObjectNameException e)
        {
            throw new RuntimeException(
                    "Invalid ObjectName? Please report this as a bug.", e);
        }
        
        memProxy = ManagementFactory.newPlatformMXBeanProxy(mbeanServerConn, 
                ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
        runtimeProxy = ManagementFactory.newPlatformMXBeanProxy(
                mbeanServerConn, ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);
    }

    public void close() throws IOException
    {
        jmxc.close();
    }

    public void forceTableCleanup(String tableName, String... columnFamilies) throws IOException, ExecutionException, InterruptedException
    {
        ssProxy.forceTableCleanup(tableName, columnFamilies);
    }

    public void forceTableCompaction(String tableName, String... columnFamilies) throws IOException, ExecutionException, InterruptedException
    {
        ssProxy.forceTableCompaction(tableName, columnFamilies);
    }

    public void forceTableFlush(String tableName, String... columnFamilies) throws IOException, ExecutionException, InterruptedException
    {
        ssProxy.forceTableFlush(tableName, columnFamilies);
    }

    public void forceTableRepair(String tableName, String... columnFamilies) throws IOException
    {
        ssProxy.forceTableRepair(tableName, columnFamilies);
    }

    public void invalidateKeyCaches(String tableName, String... columnFamilies) throws IOException
    {
        ssProxy.invalidateKeyCaches(tableName, columnFamilies);
    }

    public void invalidateRowCaches(String tableName, String... columnFamilies) throws IOException
    {
        ssProxy.invalidateRowCaches(tableName, columnFamilies);
    }

    public void drain() throws IOException, InterruptedException, ExecutionException
    {
        ssProxy.drain();	
    }
    
    public Map<Token, String> getTokenToEndpointMap()
    {
        return ssProxy.getTokenToEndpointMap();
    }

    public List<String> getLiveNodes()
    {
        return ssProxy.getLiveNodes();
    }

    public List<String> getJoiningNodes()
    {
        return ssProxy.getJoiningNodes();
    }

    public List<String> getLeavingNodes()
    {
        return ssProxy.getLeavingNodes();
    }
    
    public List<String> getUnreachableNodes()
    {
        return ssProxy.getUnreachableNodes();
    }
    
    public Map<String, String> getLoadMap()
    {
        return ssProxy.getLoadMap();
    }

    public Map<Token, Float> getOwnership()
    {
        return ssProxy.getOwnership();
    }

    public Iterator<Map.Entry<String, ColumnFamilyStoreMBean>> getColumnFamilyStoreMBeanProxies()
    {
        try
        {
            return new ColumnFamilyStoreMBeanIterator(mbeanServerConn);
        }
        catch (MalformedObjectNameException e)
        {
            throw new RuntimeException("Invalid ObjectName? Please report this as a bug.", e);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not retrieve list of stat mbeans.", e);
        }
    }

    public CompactionManagerMBean getCompactionManagerProxy()
    {
      return compactionProxy;
    }

    public JMXInstrumentedCacheMBean getKeyCacheMBean(String tableName, String cfName)
    {
        String keyCachePath = "org.apache.cassandra.db:type=Caches,keyspace=" + tableName + ",cache=" + cfName + "KeyCache";
        try
        {
            return JMX.newMBeanProxy(mbeanServerConn, new ObjectName(keyCachePath), JMXInstrumentedCacheMBean.class);
        }
        catch (MalformedObjectNameException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    public JMXInstrumentedCacheMBean getRowCacheMBean(String tableName, String cfName)
    {
        String rowCachePath = "org.apache.cassandra.db:type=Caches,keyspace=" + tableName + ",cache=" + cfName + "RowCache";
        try
        {
            return JMX.newMBeanProxy(mbeanServerConn, new ObjectName(rowCachePath), JMXInstrumentedCacheMBean.class);
        }
        catch (MalformedObjectNameException e)
        {
            throw new RuntimeException(e);
        }
    }

    public String getToken()
    {
        return ssProxy.getToken();
    }
    
    public String getLoadString()
    {
        return ssProxy.getLoadString();
    }

    public String getReleaseVersion()
    {
        return ssProxy.getReleaseVersion();
    }

    public int getCurrentGenerationNumber()
    {
        return ssProxy.getCurrentGenerationNumber();
    }
    
    public long getUptime()
    {
        return runtimeProxy.getUptime();
    }
    
    public MemoryUsage getHeapMemoryUsage()
    {
        return memProxy.getHeapMemoryUsage();
    }
    
    /**
     * Take a snapshot of all the tables.
     * 
     * @param snapshotName the name of the snapshot.
     */
    public void takeSnapshot(String snapshotName) throws IOException
    {
        ssProxy.takeAllSnapshot(snapshotName);
    }

    /**
     * Remove all the existing snapshots.
     */
    public void clearSnapshot() throws IOException
    {
        ssProxy.clearSnapshot();
    }

    public void decommission() throws InterruptedException
    {
        ssProxy.decommission();
    }

    public void loadBalance() throws IOException, InterruptedException
    {
        ssProxy.loadBalance();
    }

    public void move(String newToken) throws IOException, InterruptedException
    {
        ssProxy.move(newToken);
    }

    public void removeToken(String token)
    {
        ssProxy.removeToken(token);
    }

    public String getRemovalStatus()
    {
        return ssProxy.getRemovalStatus();
    }

    public void forceRemoveCompletion()
    {
        ssProxy.forceRemoveCompletion();
    }
  
    public Iterator<Map.Entry<String, IExecutorMBean>> getThreadPoolMBeanProxies()
    {
        try
        {
            return new ThreadPoolProxyMBeanIterator(mbeanServerConn);
        }
        catch (MalformedObjectNameException e)
        {
            throw new RuntimeException("Invalid ObjectName? Please report this as a bug.", e);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not retrieve list of stat mbeans.", e);
        }
    }

    /**
     * Get the compaction threshold
     *
     * @param outs the stream to write to
     */
    public void getCompactionThreshold(PrintStream outs, String ks, String cf)
    {
        ColumnFamilyStoreMBean cfsProxy = getCfsProxy(ks, cf);
        outs.println("Current compaction thresholds for " + ks + "/" + cf + ": \n" +
                     " min = " + cfsProxy.getMinimumCompactionThreshold() + ", " +
                     " max = " + cfsProxy.getMaximumCompactionThreshold());
    }

    /**
     * Set the compaction threshold
     *
     * @param minimumCompactionThreshold minimum compaction threshold
     * @param maximumCompactionThreshold maximum compaction threshold
     */
    public void setCompactionThreshold(String ks, String cf, int minimumCompactionThreshold, int maximumCompactionThreshold)
    {
        ColumnFamilyStoreMBean cfsProxy = getCfsProxy(ks, cf);
        cfsProxy.setMinimumCompactionThreshold(minimumCompactionThreshold);
        cfsProxy.setMaximumCompactionThreshold(maximumCompactionThreshold);
    }

    public void setCacheCapacities(String tableName, String cfName, int keyCacheCapacity, int rowCacheCapacity)
    {
        try
        {
            String keyCachePath = "org.apache.cassandra.db:type=Caches,keyspace=" + tableName + ",cache=" + cfName + "KeyCache";
            JMXInstrumentedCacheMBean keyCacheMBean = JMX.newMBeanProxy(mbeanServerConn, new ObjectName(keyCachePath), JMXInstrumentedCacheMBean.class);
            keyCacheMBean.setCapacity(keyCacheCapacity);

            String rowCachePath = "org.apache.cassandra.db:type=Caches,keyspace=" + tableName + ",cache=" + cfName + "RowCache";
            JMXInstrumentedCacheMBean rowCacheMBean = null;
            rowCacheMBean = JMX.newMBeanProxy(mbeanServerConn, new ObjectName(rowCachePath), JMXInstrumentedCacheMBean.class);
            rowCacheMBean.setCapacity(rowCacheCapacity);
        }
        catch (MalformedObjectNameException e)
        {
            throw new RuntimeException(e);
        }
    }

    public List<InetAddress> getEndpoints(String keyspace, String key)
    {
        return ssProxy.getNaturalEndpoints(keyspace, FBUtilities.hexToBytes(key));
    }

    public Set<InetAddress> getStreamDestinations()
    {
        return streamProxy.getStreamDestinations();
    }

    public List<String> getFilesDestinedFor(InetAddress host) throws IOException
    {
        return streamProxy.getOutgoingFiles(host.getHostAddress());
    }

    public Set<InetAddress> getStreamSources()
    {
        return streamProxy.getStreamSources();
    }

    public List<String> getIncomingFiles(InetAddress host) throws IOException
    {
        return streamProxy.getIncomingFiles(host.getHostAddress());
    }

    public String getOperationMode()
    {
        return ssProxy.getOperationMode();
    }

    public void truncate(String tableName, String cfName)
    {
        try
        {
            ssProxy.truncate(tableName, cfName);
        }
        catch (UnavailableException e)
        {
            throw new RuntimeException("Error while executing truncate", e);
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException("Error while executing truncate", e);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error while executing truncate", e);
        }
    }
    
    @Deprecated
    public void loadSchemaFromYAML() throws ConfigurationException, IOException
    {
        ssProxy.loadSchemaFromYAML();
    }
    
    public String exportSchemaToYAML() throws IOException
    {
        return ssProxy.exportSchema();
    }

    public MessagingServiceMBean getMsProxy()
    {
        try
        {
            return JMX.newMBeanProxy(mbeanServerConn, new ObjectName("org.apache.cassandra.net:type=MessagingService"), MessagingServiceMBean.class);
        }
        catch (MalformedObjectNameException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    public ColumnFamilyStoreMBean getCfsProxy(String ks, String cf)
    {
        ColumnFamilyStoreMBean cfsProxy = null;
        try
        {
            cfsProxy = JMX.newMBeanProxy(mbeanServerConn,
                    new ObjectName("org.apache.cassandra.db:type=ColumnFamilies,keyspace="+ks+",columnfamily="+cf), 
                    ColumnFamilyStoreMBean.class);
        }
        catch (MalformedObjectNameException mone)
        {
            System.err.println("ColumnFamilyStore for " + ks + "/" + cf + " not found.");
            System.exit(1);
        }

        return cfsProxy;
    }

    public List<String> getKeyspaces()
    {
        return ssProxy.getKeyspaces();
    }

    public void stopGossiping()
    {
        ssProxy.stopGossiping();
    }

    public void startGossiping()
    {
        ssProxy.startGossiping();
    }

    public boolean isInitialized()
    {
        return ssProxy.isInitialized();
    }
}

class ColumnFamilyStoreMBeanIterator implements Iterator<Map.Entry<String, ColumnFamilyStoreMBean>>
{
    private Iterator<ObjectName> resIter;
    private MBeanServerConnection mbeanServerConn;
    
    public ColumnFamilyStoreMBeanIterator(MBeanServerConnection mbeanServerConn)
    throws MalformedObjectNameException, NullPointerException, IOException
    {
        ObjectName query = new ObjectName("org.apache.cassandra.db:type=ColumnFamilies,*");
        resIter = mbeanServerConn.queryNames(query, null).iterator();
        this.mbeanServerConn = mbeanServerConn;
    }

    public boolean hasNext()
    {
        return resIter.hasNext();
    }

    public Entry<String, ColumnFamilyStoreMBean> next()
    {
        ObjectName objectName = resIter.next();
        String tableName = objectName.getKeyProperty("keyspace");
        ColumnFamilyStoreMBean cfsProxy = JMX.newMBeanProxy(mbeanServerConn, objectName, ColumnFamilyStoreMBean.class);
        return new AbstractMap.SimpleImmutableEntry<String, ColumnFamilyStoreMBean>(tableName, cfsProxy);
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}

class ThreadPoolProxyMBeanIterator implements Iterator<Map.Entry<String, IExecutorMBean>>
{
    private Iterator<ObjectName> resIter;
    private MBeanServerConnection mbeanServerConn;
    
    public ThreadPoolProxyMBeanIterator(MBeanServerConnection mbeanServerConn) 
    throws MalformedObjectNameException, NullPointerException, IOException
    {
        Set<ObjectName> requests = mbeanServerConn.queryNames(new ObjectName("org.apache.cassandra.request:type=*"), null);
        Set<ObjectName> internal = mbeanServerConn.queryNames(new ObjectName("org.apache.cassandra.internal:type=*"), null);
        resIter = Iterables.concat(requests, internal).iterator();
        this.mbeanServerConn = mbeanServerConn;
    }
    
    public boolean hasNext()
    {
        return resIter.hasNext();
    }

    public Map.Entry<String, IExecutorMBean> next()
    {
        ObjectName objectName = resIter.next();
        String poolName = objectName.getKeyProperty("type");
        IExecutorMBean threadPoolProxy = JMX.newMBeanProxy(mbeanServerConn, objectName, IExecutorMBean.class);
        return new AbstractMap.SimpleImmutableEntry<String, IExecutorMBean>(poolName, threadPoolProxy);
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
