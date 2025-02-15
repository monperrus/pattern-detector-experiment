/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.jndi;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.InitialContextFactoryBuilder;

import org.osgi.framework.BundleContext;

public class OSGiInitialContextFactoryBuilder implements InitialContextFactoryBuilder, InitialContextFactory {

	private BundleContext _context;
	
	public OSGiInitialContextFactoryBuilder(BundleContext context) {	
		_context = context;
	}
	
	public InitialContextFactory createInitialContextFactory(Hashtable<?, ?> environment) 
	    throws NamingException {
	    return this;
	}
  
	public Context getInitialContext(Hashtable<?, ?> environment) 
	    throws NamingException {
	    
	    // TODO: use caller's bundle context
	    
	    return ContextHelper.getInitialContext(_context, environment);
	}
	
}
