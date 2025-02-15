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
package org.apache.aries.util;

import org.apache.aries.util.internal.DefaultWorker;
import org.apache.aries.util.internal.EquinoxWorker;
import org.apache.aries.util.internal.FelixWorker;
import org.apache.aries.util.internal.FrameworkUtilWorker;
import org.apache.aries.util.internal.R43Worker;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

public final class AriesFrameworkUtil
{
  private static FrameworkUtilWorker worker;
  
  static {
    try {
      worker = new R43Worker();
    } catch (Throwable e) {
    }
    Bundle b = FrameworkUtil.getBundle(AriesFrameworkUtil.class);
    String bundleClassName = b == null? "": b.getClass().getName();
    if (worker == null && isEquinox(bundleClassName)) {
      worker = new EquinoxWorker();
    } else if (worker == null && bundleClassName.startsWith("org.apache.felix")) {
      worker = new FelixWorker();
    } 
    
    if (worker == null || !!!worker.isValid()) worker = new DefaultWorker();
  }
  
  
  /**
   * This method attempts to get the classloader for a bundle. It may return null if
   * their is no such classloader, or if it cannot obtain the classloader for the bundle.
   * 
   * @param b the bundle whose classloader is desired.
   * @return  the classloader if found, or null if for example the bundle is in INSTALLED or UNINSTALLED state
   */
  public static ClassLoader getClassLoader(Bundle b)
  {
    if (b != null && b.getState() != Bundle.UNINSTALLED && b.getState() != Bundle.INSTALLED) {
      return worker.getClassLoader(b);
    } else {
      return null;
    }
  }
  
  /**
   * Returns true if we are in equinox, and we can access the interfaces we need.
   * @param bundleClassName the class name of the bundle implementation.
   * @return true if we are in equinox, false otherwise.
   */
  private static boolean isEquinox(String bundleClassName) 
  {
    if (bundleClassName.startsWith("org.eclipse.osgi")) {
      try {
        Class.forName("org.eclipse.osgi.framework.internal.core.BundleHost");
        return true;
      } catch (ClassNotFoundException e) {
      }
    }
    return false;
  }

  /**
   * This method attempts to get the classloader for a bundle. It will force the creation
   * of a classloader, so if no classloader exists. If the bundle is in installed state, but
   * cannot be resolved the null will be returned.
   * 
   * @param b the bundle to get a classloader for
   * @return  the classloader.
   */
  public static ClassLoader getClassLoaderForced(Bundle b)
  {
    if (b == null)
      return null;
    try {
      b.loadClass("java.lang.Object");
    } catch (ClassNotFoundException e) {
    }
    return worker.getClassLoader(b);
  }
  
  /**
   * Safely unregister the supplied ServiceRegistration, for when you don't
   * care about the potential IllegalStateException and don't want
   * it to run wild through your code
   * 
   * @param reg The {@link ServiceRegistration}, may be null
   */
  public static void safeUnregisterService(ServiceRegistration reg) 
  {
    if(reg != null) {
      try {
        reg.unregister();
      } catch (IllegalStateException e) {
        //This can be safely ignored
      }
    }
  }
}
