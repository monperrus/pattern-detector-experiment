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
package org.apache.aries.jndi.url;

import java.util.Hashtable;

import javax.naming.spi.ObjectFactory;

import org.apache.aries.proxy.ProxyManager;
import org.apache.aries.util.SingleServiceTracker;
import org.apache.aries.util.SingleServiceTracker.SingleServiceListener;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jndi.JNDIConstants;

public class Activator implements BundleActivator, SingleServiceListener 
{
    private BundleContext ctx;
    private volatile ServiceRegistration osgiUrlReg = null;
    private volatile ServiceRegistration blueprintUrlReg = null;
    private static SingleServiceTracker<ProxyManager> proxyManager;

    @Override
    public void start(BundleContext context) 
    {
        ctx = context;
        proxyManager = new SingleServiceTracker<ProxyManager>(context, ProxyManager.class, this);
        proxyManager.open();
    }

    @Override
    public void stop(BundleContext context) {
      proxyManager.close();
      if (osgiUrlReg != null) osgiUrlReg.unregister();
      if (blueprintUrlReg != null) blueprintUrlReg.unregister();
    }
  

  @Override
  public void serviceFound() 
  {
    Hashtable<Object, Object> osgiUrlprops = new Hashtable<Object, Object>();
    osgiUrlprops.put(JNDIConstants.JNDI_URLSCHEME, new String[] { "osgi", "aries" });
    osgiUrlReg = ctx.registerService(ObjectFactory.class.getName(),
        new OsgiURLContextServiceFactory(), osgiUrlprops);

    // Blueprint URL scheme requires access to the BlueprintContainer service.
    // We have an optional import
    // on org.osgi.service.blueprint.container: only register the blueprint:comp/URL
    // scheme if it's present
    try {
      ctx.getBundle().loadClass("org.osgi.service.blueprint.container.BlueprintContainer");
      Hashtable<Object, Object> blueprintURlSchemeProps = new Hashtable<Object, Object>();
      blueprintURlSchemeProps.put(JNDIConstants.JNDI_URLSCHEME, new String[] { "blueprint" });
      blueprintUrlReg = ctx.registerService(ObjectFactory.class.getName(),
          new BlueprintURLContextServiceFactory(), blueprintURlSchemeProps);
    } catch (ClassNotFoundException cnfe) {
      // The blueprint packages aren't available, so do nothing. That's fine.
    }
  }

  @Override
  public void serviceLost() 
  {
    if (osgiUrlReg != null) osgiUrlReg.unregister();
    osgiUrlReg = null;
    if (blueprintUrlReg != null) blueprintUrlReg.unregister();
    blueprintUrlReg = null;
  }

  @Override
  public void serviceReplaced() 
  {
    
  }
    
  public static ProxyManager getProxyManager()
  {
    return proxyManager == null ? null : proxyManager.getService();
  }
}
