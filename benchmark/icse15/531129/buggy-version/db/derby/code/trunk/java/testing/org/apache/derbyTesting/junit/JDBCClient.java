/*
 *
 * Derby - Class JDBCClient
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific 
 * language governing permissions and limitations under the License.
 */
package org.apache.derbyTesting.junit;

import junit.framework.Assert;

/**
 * Type-safe enumerator of valid JDBC clients.
 * Each JDBC client definition consists of the client name, the name of the
 * JDBC driver class, the name of a DataSource class and the base JDBC url.
 */
public final class JDBCClient {

    /**
     * The embedded JDBC client.
     */
    static final JDBCClient EMBEDDED_30= new JDBCClient(
            "Embedded_30", 
            "org.apache.derby.jdbc.EmbeddedDriver", 
            "org.apache.derby.jdbc.EmbeddedDataSource", 
            "org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource",
            "org.apache.derby.jdbc.EmbeddedXADataSource",
            "jdbc:derby:");
    
    /**
     * The embedded JDBC client for JDBC 4.0.
     */
    static final JDBCClient EMBEDDED_40 = new JDBCClient(
            "Embedded_40", 
            "org.apache.derby.jdbc.EmbeddedDriver", 
            "org.apache.derby.jdbc.EmbeddedDataSource40", 
            "org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource40",
            "org.apache.derby.jdbc.EmbeddedXADataSource40",
            "jdbc:derby:");
    
    /**
     * The embedded JDBC client for JSR 169
     */
    private static final JDBCClient EMBEDDED_169 = new JDBCClient(
            "Embedded_169", 
            null, // No driver
            "org.apache.derby.jdbc.EmbeddedSimpleDataSource", 
            null, // No connection pooling
            null, // No XA
            null); // No JDBC URLs
    
    /**
     * Return the default embedded client for this JVM.
     */
    static JDBCClient getDefaultEmbedded()
    {
        if (JDBC.vmSupportsJDBC4())
            return EMBEDDED_40;
        if (JDBC.vmSupportsJDBC2())
            return EMBEDDED_30;
        if (JDBC.vmSupportsJSR169())
            return EMBEDDED_169;
        
        Assert.fail("Unknown JVM environment");
        return null;
    }
    
    /**
     * The Derby network client.
     */
    static final JDBCClient DERBYNETCLIENT= new JDBCClient(
            "DerbyNetClient",
            "org.apache.derby.jdbc.ClientDriver",
            JDBC.vmSupportsJDBC4() ?
            "org.apache.derby.jdbc.ClientDataSource40" :
            "org.apache.derby.jdbc.ClientDataSource",
            JDBC.vmSupportsJDBC4() ?
            "org.apache.derby.jdbc.ClientConnectionPoolDataSource40" :
            "org.apache.derby.jdbc.ClientConnectionPoolDataSource",
            JDBC.vmSupportsJDBC4() ?
            "org.apache.derby.jdbc.ClientXADataSource40" :
            "org.apache.derby.jdbc.ClientXADataSource",
            "jdbc:derby://");
    
    /**
     * The DB2 Universal JDBC network client.
     * AKA: JCC or DerbyNet.
     * (the "old net" client for Derby).
     */
    static final JDBCClient DB2CLIENT= new JDBCClient(
            "DerbyNet",
            "com.ibm.db2.jcc.DB2Driver",
            null, null, null,
            "jdbc:derby:net://");
    
    /**
     * Is this the embdded client.
    */
    public boolean isEmbedded()
    {
    	return getName().startsWith("Embedded");
    }
    /**
     * Is this Derby's network client.
     */
    public boolean isDerbyNetClient()
    {
    	return getName().equals(DERBYNETCLIENT.getName());
    }
    /**
     * Is this DB2's Universal JDBC 
     */
    public boolean isDB2Client()
    {
    	return getName().equals(DB2CLIENT.getName());
    }
    
    /**
     * Get the name of the client
     */
    public String getName()
    {
    	return frameWork;
    }
    
    /**
     * Get JDBC driver class name.
     * 
     * @return class name for JDBC driver.
     */
    public String getJDBCDriverName() {
        return driverClassName;
    }

    /**
     * Get DataSource class name.
     * 
     * @return class name for DataSource implementation.
     */
    public String getDataSourceClassName() {
        return dsClassName;
    }

    /**
     * Get ConnectionPoolDataSource class name.
     *
     * @return class name for ConnectionPoolDataSource implementation.
     */
    public String getConnectionPoolDataSourceClassName() {
        return poolDsClassName;
    }

    /**
     * Get XADataSource class name.
     *
     * @return class name for XADataSource implementation.
     */
    public String getXADataSourceClassName() {
        return xaDsClassName;
    }

    /**
     * Return the base JDBC url.
     * The JDBC base url specifies the protocol and possibly the subprotcol
     * in the JDBC connection string.
     * 
     * @return JDBC base url.
     */
    public String getUrlBase() {
        return urlBase;
    }
    
    /**
     * Return string representation of this object.
     * 
     * @return string representation of this object.
     */
    public String toString() {
        return frameWork;
    }
    
    /**
     * Create a JDBC client definition.
     */
    private JDBCClient(String frameWork, String driverClassName,
                       String dataSourceClassName,
                       String connectionPoolDataSourceClassName,
                       String xaDataSourceClassName,
                       String urlBase) {
        this.frameWork          = frameWork;
        this.driverClassName    = driverClassName;
        this.dsClassName        = dataSourceClassName;
        this.poolDsClassName    = connectionPoolDataSourceClassName;
        this.xaDsClassName      = xaDataSourceClassName;
        this.urlBase            = urlBase;
    }
    
    private final String frameWork;
    private final String driverClassName;
    private final String dsClassName;
    private final String poolDsClassName;
    private final String xaDsClassName;
    private final String urlBase;
    
}
