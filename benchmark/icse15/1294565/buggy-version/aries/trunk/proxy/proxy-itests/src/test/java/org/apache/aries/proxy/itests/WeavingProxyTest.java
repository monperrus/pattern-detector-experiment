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
package org.apache.aries.proxy.itests;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.apache.aries.itest.ExtraOptions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;

import org.apache.aries.proxy.FinalModifierException;
import org.apache.aries.proxy.ProxyManager;
import org.apache.aries.proxy.weaving.WovenProxy;
import org.apache.aries.proxy.weavinghook.ProxyWeavingController;
import org.apache.aries.proxy.weavinghook.WeavingHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.container.def.PaxRunnerOptions;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.hooks.weaving.WovenClass;

@RunWith(JUnit4TestRunner.class)
public class WeavingProxyTest extends AbstractProxyTest
{
  
  /**
   * This test does two things. First of all it checks that we can proxy a final 
   * class. It also validates that the class implements WovenProxy, and that the
   * delegation still works
   */
  @Test
  public void checkProxyFinalClass() throws Exception
  {
    ProxyManager mgr = context().getService(ProxyManager.class);
    Bundle b = FrameworkUtil.getBundle(this.getClass());
    TestCallable dispatcher = new TestCallable();
    TestCallable template = new TestCallable();
    Collection<Class<?>> classes = new ArrayList<Class<?>>();
    classes.add(TestCallable.class);
    Callable<Object> o = (Callable<Object>) mgr.createDelegatingProxy(b, classes, 
        dispatcher, template);
    if(!!!(o instanceof WovenProxy))
      fail("Proxy should be woven!");
    
    Object inner = new Integer(3);
    dispatcher.setReturn(new TestCallable());
    ((TestCallable)dispatcher.call()).setReturn(inner);
    
    assertSame("Should return the same object", inner, o.call());
  }
  
  /**
   * This method checks that we correctly proxy a class with final methods.
   */
  @Test
  public void checkProxyFinalMethods() throws Exception
  {
    ProxyManager mgr = context().getService(ProxyManager.class);
    Bundle b = FrameworkUtil.getBundle(this.getClass());
    Callable<Object> c = new TestCallable();
    Collection<Class<?>> classes = new ArrayList<Class<?>>();
    Runnable r = new Runnable() {
      public final void run() {
      }
    };
    classes.add(r.getClass());
    Object o = mgr.createDelegatingProxy(b, classes, c, r);
    if(!!!(o instanceof WovenProxy))
      fail("Proxy should be woven!");
  }
  
  @Test(expected = FinalModifierException.class)
  public void checkProxyController() throws Exception
  {
    context().registerService(ProxyWeavingController.class.getName(), new ProxyWeavingController() {
      
      public boolean shouldWeave(WovenClass arg0, WeavingHelper arg1)
      {
        return false;
      }
    }, null);
    
    ProxyManager mgr = context().getService(ProxyManager.class);
    Bundle b = FrameworkUtil.getBundle(this.getClass());
    Callable<Object> c = new TestCallable();
    Collection<Class<?>> classes = new ArrayList<Class<?>>();
    Runnable r = new Runnable() {
      public final void run() {
      }
    };
    classes.add(r.getClass());
    Object o = mgr.createDelegatingProxy(b, classes, c, r);
    if(o instanceof WovenProxy)
      fail("Proxy should not have been woven!");
  }
   
  @org.ops4j.pax.exam.junit.Configuration
  public static Option[] configuration() {
      return testOptions(
          paxLogging("DEBUG"),

          // Bundles
          mavenBundle("org.apache.aries", "org.apache.aries.util"),
          mavenBundle("org.apache.aries.proxy", "org.apache.aries.proxy"),
          mavenBundle("org.ow2.asm", "asm-all"),
          // don't install the blueprint sample here as it will be installed onto the same framework as the blueprint core bundle
          // mavenBundle("org.apache.aries.blueprint", "org.apache.aries.blueprint.sample").noStart(),
          mavenBundle("org.osgi", "org.osgi.compendium"),
//          org.ops4j.pax.exam.container.def.PaxRunnerOptions.vmOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),
          PaxRunnerOptions.rawPaxRunnerOption("config", "classpath:ss-runner.properties"),

          equinox().version("3.7.0.v20110613")
      );
  }
}
