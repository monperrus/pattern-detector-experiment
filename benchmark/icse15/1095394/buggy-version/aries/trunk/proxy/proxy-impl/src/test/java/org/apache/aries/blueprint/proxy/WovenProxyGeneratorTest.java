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
package org.apache.aries.blueprint.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.aries.blueprint.proxy.pkg.ProxyTestClassUnweavableSuperWithDefaultMethodWrongPackageParent;
import org.apache.aries.proxy.FinalModifierException;
import org.apache.aries.proxy.InvocationListener;
import org.apache.aries.proxy.UnableToProxyException;
import org.apache.aries.proxy.impl.SingleInstanceDispatcher;
import org.apache.aries.proxy.impl.gen.ProxySubclassMethodHashSet;
import org.apache.aries.proxy.impl.weaving.WovenProxyGenerator;
import org.apache.aries.proxy.weaving.WovenProxy;
import org.junit.BeforeClass;
import org.junit.Test;


public class WovenProxyGeneratorTest extends AbstractProxyTest
{
  private static final String hexPattern = "[0-9_a-f]";
  private static final int[] uuid_pattern = new int[] {8,4,4,4,12};
  private static final String regexp;
  
  static {
    StringBuilder sb = new StringBuilder(".*");
    for(int i : uuid_pattern) {
      for(int j = 0; j < i; j++){
        sb.append(hexPattern);
      }
      sb.append("_");
    }
    sb.deleteCharAt(sb.length() -1);
    sb.append("\\d*");
    regexp = sb.toString();
  }
  

  private static final Class<?>[] CLASSES = new Class<?>[]{TEST_CLASS, ProxyTestClassSuper.class,
    ProxyTestClassFinalMethod.class, ProxyTestClassFinal.class, ProxyTestClassGeneric.class,
    ProxyTestClassGenericSuper.class, ProxyTestClassCovariant.class, ProxyTestClassCovariantOverride.class,
    ProxyTestClassUnweavableChild.class, ProxyTestClassUnweavableSuperWithFinalMethod.class,
    ProxyTestClassUnweavableChildWithDefaultMethodWrongPackageParent.class, ProxyTestClassUnweavableSibling.class};
 
  private static final Map<String, byte[]> rawClasses = new HashMap<String, byte[]>();
  
  private static final ClassLoader weavingLoader = new ClassLoader() {
    public Class<?> loadClass(String className)  throws ClassNotFoundException
    {
      return loadClass(className, false);
    }
    public Class<?> loadClass(String className, boolean b) throws ClassNotFoundException
    {
      if (!!!className.startsWith("org.apache.aries.blueprint.proxy.ProxyTest")){
        return Class.forName(className);
      }
      
      Class<?> clazz = findLoadedClass(className);
      if(clazz != null)
        return clazz;
      
      byte[] bytes = rawClasses.get(className);
      if(bytes == null)
        return super.loadClass(className, b);
      
      bytes = WovenProxyGenerator.getWovenProxy(bytes, className, this);
      
      return defineClass(className, bytes, 0, bytes.length);
    }
    
    protected URL findResource(String resName) {
      return WovenProxyGeneratorTest.class.getResource(resName);
    }
  };
   
  /**
   * @throws java.lang.Exception
   */
  @BeforeClass
  public static void setUp() throws Exception
  {
    for(Class<?> clazz : CLASSES) {
      InputStream is = clazz.getClassLoader().getResourceAsStream(
          clazz.getName().replace('.', '/') + ".class");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      byte[] buffer = new byte[2048];
      int read = is.read(buffer);
      while(read != -1) {
        baos.write(buffer, 0, read);
        read = is.read(buffer);
      }
      rawClasses.put(clazz.getName(), baos.toByteArray());
    }
  }

  /**
   * This test uses the WovenProxyGenerator to generate and load the specified TEST_CLASS.
   * 
   * Once the subclass is generated we check that it wasn't null. 
   * 
   * Test method for
   * {@link WovenProxyGenerator#getProxySubclass(byte[], String)}.
   */
  @Test
  public void testGenerateAndLoadProxy() throws Exception
  {
    super.testGenerateAndLoadProxy();
    assertTrue("Should be a WovenProxy", WovenProxy.class.isAssignableFrom(getProxyClass(TEST_CLASS)));
  }

  /**
   * Test that the methods found declared on the generated proxy are
   * the ones that we expect.
   */
  @Test
  public void testExpectedMethods() throws Exception
  {
    ProxySubclassMethodHashSet<String> originalMethods = getMethods(TEST_CLASS);

    ProxySubclassMethodHashSet<String> generatedMethods = getMethods(weavingLoader.
        loadClass(TEST_CLASS.getName()));

    // check that all the methods we have generated were expected
    for (String gen : generatedMethods) {
      assertTrue("Unexpected method: " + gen, originalMethods.contains(gen));
    }
    // check that all the expected methods were generated
    for (String exp : originalMethods) {
      assertTrue("Method was not generated: " + exp, generatedMethods.contains(exp));
    }
    // check the sets were the same
    assertEquals("Sets were not the same", originalMethods, generatedMethods);
  }

  private ProxySubclassMethodHashSet<String> getMethods(Class<?> clazz) {
    
    ProxySubclassMethodHashSet<String> foundMethods = 
      new ProxySubclassMethodHashSet<String>(12);
    do {
      Method[] declaredMethods = clazz.getDeclaredMethods();
      List<Method> listOfDeclaredMethods = new ArrayList<Method>();
      for (Method m : declaredMethods) {
        if(m.getName().startsWith(WovenProxy.class.getName().replace('.', '_')) ||
            m.getName().startsWith("getListener") || m.getName().startsWith("getInvocationTarget") ||
            //four hex digits
            m.getName().matches(regexp))
          continue;
        
        listOfDeclaredMethods.add(m);
      }
      declaredMethods = listOfDeclaredMethods.toArray(new Method[] {});
      foundMethods.addMethodArray(declaredMethods);
      clazz = clazz.getSuperclass();
    } while (clazz != null);
    return foundMethods;
  }

  /**
   * Test a method marked final
   */
  @Test
  public void testFinalMethod() throws Exception
  {
    assertNotNull(weavingLoader.loadClass(ProxyTestClassFinalMethod.class
        .getName()));
  }

  /**
   * Test a class marked final
   */
  @Test
  public void testFinalClass() throws Exception
  {
    assertNotNull(weavingLoader.loadClass(ProxyTestClassFinal.class
        .getName()));
  }

  /**
   * Test a private constructor
   */
  @Test
  public void testPrivateConstructor() throws Exception
  {
    assertNotNull(weavingLoader.loadClass(ProxyTestClassFinal.class
        .getName()));
  }
  
  /**
   * Test a class whose super couldn't be woven
   */
  @Test
  public void testUnweavableSuper() throws Exception
  {
    Class<?> woven = getProxyClass(ProxyTestClassUnweavableChild.class);
    
    assertNotNull(woven);
    assertNotNull(getProxyInstance(woven));
    
    TestListener tl = new TestListener();
    ProxyTestClassUnweavableSuper ptcuc = (ProxyTestClassUnweavableSuper) getProxyInstance(woven, tl);
    assertCalled(tl, false, false, false);
    
    assertEquals("Hi!", ptcuc.doStuff());
    
    assertCalled(tl, true, true, false);
    
    assertEquals(ProxyTestClassUnweavableGrandParent.class.getMethod("doStuff"), 
        tl.getLastMethod());
    

    //Because default access works on the package, and we are defined on a different classloader
    //we can only check that the method exists, not that it is callable *sigh*
    
    assertNotNull(ProxyTestClassUnweavableSuper.class.getDeclaredMethod("doStuff2"));
  }
  
  @Test
  public void testUnweavableSuperWithNoNoargsAllTheWay() throws Exception
  {
    Class<?> woven = getProxyClass(ProxyTestClassUnweavableSibling.class);
    
    assertNotNull(woven);
    assertNotNull(woven.getConstructor(int.class).newInstance(42));
    
    TestListener tl = new TestListener();
    
    WovenProxy proxy = (WovenProxy) woven.getConstructor(int.class).newInstance(42);
    proxy = proxy.org_apache_aries_proxy_weaving_WovenProxy_createNewProxyInstance(
            new SingleInstanceDispatcher(proxy), tl);
    
    ProxyTestClassUnweavableSuper ptcuc = (ProxyTestClassUnweavableSuper) proxy;
    assertCalled(tl, false, false, false);
    
    assertEquals("Hi!", ptcuc.doStuff());
    
    assertCalled(tl, true, true, false);
    
    assertEquals(ProxyTestClassUnweavableGrandParent.class.getMethod("doStuff"), 
        tl.getLastMethod());
    

    //Because default access works on the package, and we are defined on a different classloader
    //we can only check that the method exists, not that it is callable *sigh*
    
    assertNotNull(ProxyTestClassUnweavableSuper.class.getDeclaredMethod("doStuff2"));
  }  
  
  /**
   * Test a class whose super couldn't be woven
   */
  @Test
  public void testUnweavableSuperWithFinalMethod() throws Exception
  {
    try{
      getProxyClass(ProxyTestClassUnweavableChildWithFinalMethodParent.class);
    } catch (RuntimeException re) {
      assertTrue(re.getCause() instanceof FinalModifierException);
      assertEquals(ProxyTestClassUnweavableSuperWithFinalMethod.class.getName(),
          ((FinalModifierException)re.getCause()).getClassName());
      assertEquals("doStuff2", ((FinalModifierException)re.getCause())
          .getFinalMethods());
    }
  }
  
  /**
   * Test a class whose super couldn't be woven
   */
  @Test
  public void testUnweavableSuperWithDefaultMethodInWrongPackage() throws Exception
  {
    try{
      getProxyClass(ProxyTestClassUnweavableChildWithDefaultMethodWrongPackageParent.class);
    } catch (RuntimeException re) {
      assertTrue(re.getCause() instanceof UnableToProxyException);
      assertEquals(ProxyTestClassUnweavableSuperWithDefaultMethodWrongPackageParent
          .class.getName(), ((UnableToProxyException)re.getCause()).getClassName());
    }
  }
  
  @Override
  protected Object getProxyInstance(Class<?> proxyClass) {
    try {
      return proxyClass.newInstance();
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  protected Class<?> getProxyClass(Class<?> clazz) {
    try {
      return weavingLoader.loadClass(clazz.getName());
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  @Override
  protected Object setDelegate(Object proxy, Callable<Object> dispatcher) {
    return ((WovenProxy) proxy).
    org_apache_aries_proxy_weaving_WovenProxy_createNewProxyInstance(
        dispatcher, null);
  }

  @Override
  protected Object getProxyInstance(Class<?> proxyClass,
      InvocationListener listener) {
    WovenProxy proxy = (WovenProxy) getProxyInstance(proxyClass);
    proxy = proxy.org_apache_aries_proxy_weaving_WovenProxy_createNewProxyInstance(
        new SingleInstanceDispatcher(proxy), listener);
    return proxy;
  }
  
  protected Object getP3() {
    return getProxyInstance(getProxyClass(TEST_CLASS));
  }
}

