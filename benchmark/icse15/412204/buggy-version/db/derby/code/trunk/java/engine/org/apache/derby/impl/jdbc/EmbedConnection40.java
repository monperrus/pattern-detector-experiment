/*
 
   Derby - Class org.apache.derby.impl.jdbc.EmbedConnection40
 
   Copyright 2005 The Apache Software Foundation or its licensors, as applicable.
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
      http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
 */

package org.apache.derby.impl.jdbc;

import java.sql.Array;
import java.sql.BaseQuery;
import java.sql.Blob;
import java.sql.ClientInfoException;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.QueryObjectFactory;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Struct;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Enumeration;
import org.apache.derby.jdbc.InternalDriver;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.jdbc.FailedProperties40;

public class EmbedConnection40 extends EmbedConnection30 {
    
    /** Creates a new instance of EmbedConnection40 */
    public EmbedConnection40(EmbedConnection inputConnection) {
        super(inputConnection);
    }
    
    public EmbedConnection40(
        InternalDriver driver,
        String url,
        Properties info)
        throws SQLException {
        super(driver, url, info);
    }
    
    /*
     *-------------------------------------------------------
     * JDBC 4.0
     *-------------------------------------------------------
     */
    
    public Array createArray(String typeName, Object[] elements)
        throws SQLException {
        throw Util.notImplemented();
    }
    
    /**
     *
     * Constructs an object that implements the <code>Clob</code> interface. The object
     * returned initially contains no data.  The <code>setAsciiStream</code>,
     * <code>setCharacterStream</code> and <code>setString</code> methods of 
     * the <code>Clob</code> interface may be used to add data to the <code>Clob</code>.
     *
     * @return An object that implements the <code>Clob</code> interface
     * @throws SQLException if an object that implements the
     * <code>Clob</code> interface can not be constructed, this method is 
     * called on a closed connection or a database access error occurs.
     *
     */
    public Clob createClob() throws SQLException {
        checkIfClosed();
        return new EmbedClob("",this);
    }
    
    /**
     *
     * Constructs an object that implements the <code>Blob</code> interface. The object
     * returned initially contains no data.  The <code>setBinaryStream</code> and
     * <code>setBytes</code> methods of the <code>Blob</code> interface may be used to add data to
     * the <code>Blob</code>.
     *
     * @return  An object that implements the <code>Blob</code> interface
     * @throws SQLException if an object that implements the
     * <code>Blob</code> interface can not be constructed, this method is 
     * called on a closed connection or a database access error occurs.
     *
     */
    public Blob createBlob() throws SQLException {
        checkIfClosed();
        return new EmbedBlob(new byte[0],this);
    }
    
    public NClob createNClob() throws SQLException {
        throw Util.notImplemented();
    }
    
    public SQLXML createSQLXML() throws SQLException {
        throw Util.notImplemented();
    }
    
    public Struct createStruct(String typeName, Object[] attributes)
        throws SQLException {
        throw Util.notImplemented();
    }
    
    /**
     * Checks if the connection has not been closed and is still valid. 
     * The validity is checked by checking that the connection is not closed.
     *
     * @param timeout This should be the time in seconds to wait for the 
     * database operation used to validate the connection to complete 
     * (according to the JDBC4 JavaDoc). This is currently not supported/used.
     *
     * @return true if the connection is valid, false otherwise
     * @exception SQLException if the parameter value is illegal or if a
     * database error has occured
     */
    public boolean isValid(int timeout) throws SQLException {
        // Validate that the timeout has a legal value
        if (timeout < 0) {
            throw Util.generateCsSQLException(SQLState.INVALID_API_PARAMETER,
                                              new Integer(timeout), "timeout",
                                              "java.sql.Connection.isValid");
        }

        // Use the closed status for the connection to determine if the
        // connection is valid or not
        return !isClosed();
    }

    /**
     * <code>setClientInfo</code> will always throw a
     * <code>ClientInfoException</code> since Derby does not support
     * any properties.
     *
     * @param name a property key <code>String</code>
     * @param value a property value <code>String</code>
     * @exception SQLException always.
     */
    public void setClientInfo(String name, String value)
    throws SQLException{
        checkIfClosed();
        // Allow null to simplify compliance testing through
        // reflection, (test all methods in an interface with null
        // arguments)
        if (name == null && value == null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty(name, value);
        setClientInfo(p);
    }
    
    /**
     * <code>setClientInfo</code> will throw a
     * <code>ClientInfoException</code> uless the <code>properties</code>
     * paramenter is empty, since Derby does not support any
     * properties. All the property keys in the
     * <code>properties</code> parameter are added to failedProperties
     * of the exception thrown, with REASON_UNKNOWN_PROPERTY as the
     * value. 
     *
     * @param properties a <code>Properties</code> object with the
     * properties to set
     * @exception ClientInfoException always
     */
    public void setClientInfo(Properties properties)
    throws ClientInfoException {
        FailedProperties40 fp = new FailedProperties40(properties);
        
        try { checkIfClosed(); }
        catch (SQLException se) {
            throw new ClientInfoException(se.getMessage(), se.getSQLState(),
                                          fp.getProperties());
        }

        // Allow null to simplify compliance testing through
        // reflection, (test all methods in an interface with null
        // arguments)
        // An empty properties object is meaningless, but allowed
        if (properties == null || properties.isEmpty()) {
            return;
        }

        StandardException se = 
            StandardException.newException
            (SQLState.PROPERTY_UNSUPPORTED_CHANGE, 
             fp.getFirstKey(), 
             fp.getFirstValue());
        throw new ClientInfoException(se.getMessage(),
                                      se.getSQLState(), fp.getProperties());
    }
    
    /**
     * <code>getClientInfo</code> always returns a
     * <code>null String</code> since Derby doesn't support
     * ClientInfoProperties.
     *
     * @param name a <code>String</code> value
     * @return a <code>null String</code> value
     * @exception SQLException if the connection is closed.
     */
    public String getClientInfo(String name)
    throws SQLException{
        checkIfClosed();
        return null;
    }
    
    /**
     * <code>getClientInfo</code> always returns an empty
     * <code>Properties</code> object since Derby doesn't support
     * ClientInfoProperties.
     *
     * @return an empty <code>Properties</code> object
     * @exception SQLException if the connection is closed.
     */
    public Properties getClientInfo()
    throws SQLException{
        checkIfClosed();
        return new Properties();
    }

    /**
     * Returns the type map for this connection.
     *
     * @return type map for this connection
     * @exception SQLException if a database access error occurs
     */
    public final Map<String, Class<?>> getTypeMap() throws SQLException {
        // This method is already implemented with a non-generic
        // signature in EmbedConnection. We could just use that method
        // directly, but then we get a compiler warning (unchecked
        // cast/conversion). Copy the map to avoid the compiler
        // warning.
        Map typeMap = super.getTypeMap();
        if (typeMap == null) return null;
        Map<String, Class<?>> genericTypeMap = new HashMap<String, Class<?>>();
        for (Object key : typeMap.keySet()) {
            genericTypeMap.put((String) key, (Class) typeMap.get(key));
        }
        return genericTypeMap;
    }
    
    /**
     * This method forwards all the calls to default query object provided by 
     * the jdk.
     * @param ifc interface to generated concreate class
     * @return concreat class generated by default qury object generator
     */
    public <T extends BaseQuery> T createQueryObject(Class<T> ifc) 
                                                    throws SQLException {
        checkIfClosed();
        return QueryObjectFactory.createDefaultQueryObject (ifc, this);
    } 
    
    /**
     * Returns false unless <code>interfaces</code> is implemented 
     * 
     * @param  interfaces             a Class defining an interface.
     * @return true                   if this implements the interface or 
     *                                directly or indirectly wraps an object 
     *                                that does.
     * @throws java.sql.SQLException  if an error occurs while determining 
     *                                whether this is a wrapper for an object 
     *                                with the given interface.
     */
    public boolean isWrapperFor(Class<?> interfaces) throws SQLException {
        checkIfClosed();
        return interfaces.isInstance(this);
    }
    
    /**
     * Returns <code>this</code> if this class implements the interface
     *
     * @param  interfaces a Class defining an interface
     * @return an object that implements the interface
     * @throws java.sql.SQLExption if no object if found that implements the 
     * interface
     */
    public <T> T unwrap(java.lang.Class<T> interfaces) 
                            throws SQLException{
        checkIfClosed();
        //Derby does not implement non-standard methods on 
        //JDBC objects
        //hence return this if this class implements the interface 
        //or throw an SQLException
        try {
            return interfaces.cast(this);
        } catch (ClassCastException cce) {
            throw newSQLException(SQLState.UNABLE_TO_UNWRAP,interfaces);
        }
    }
}
