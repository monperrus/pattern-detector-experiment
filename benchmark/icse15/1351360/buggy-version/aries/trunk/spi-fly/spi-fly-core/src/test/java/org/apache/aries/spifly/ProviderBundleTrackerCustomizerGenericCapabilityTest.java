/**
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
package org.apache.aries.spifly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.aries.spifly.api.SpiFlyConstants;
import org.apache.aries.spifly.impl1.MySPIImpl1;
import org.apache.aries.spifly.impl2.MySPIImpl2a;
import org.apache.aries.spifly.impl2.MySPIImpl2b;
import org.apache.aries.spifly.impl3.MySPIImpl3;
import org.apache.aries.spifly.impl4.MySPIImpl4a;
import org.apache.aries.spifly.impl4.MySPIImpl4b;
import org.apache.aries.spifly.impl4.MySPIImpl4c;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

public class ProviderBundleTrackerCustomizerGenericCapabilityTest {
    @Test
    public void testAddingRemovedBundle() throws Exception {
        Bundle mediatorBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mediatorBundle.getBundleId()).andReturn(42l).anyTimes();
        EasyMock.replay(mediatorBundle);
        BaseActivator activator = new BaseActivator() {
            @Override
            public void start(BundleContext context) throws Exception {}
        };

        ProviderBundleTrackerCustomizer customizer = new ProviderBundleTrackerCustomizer(activator, mediatorBundle);

        ServiceRegistration sreg = EasyMock.createMock(ServiceRegistration.class);
        sreg.unregister();
        EasyMock.expectLastCall();
        EasyMock.replay(sreg);

        BundleContext implBC = mockSPIBundleContext(sreg);
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.REQUIRE_CAPABILITY, SpiFlyConstants.PROVIDER_REQUIREMENT);
        headers.put(SpiFlyConstants.PROVIDE_CAPABILITY, SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI");
        Bundle implBundle = mockSPIBundle(implBC, headers);


        assertEquals("Precondition", 0, activator.findProviderBundles("org.apache.aries.mytest.MySPI").size());
        // Call addingBundle();
        List<ServiceRegistration> registrations = customizer.addingBundle(implBundle, null);
        Collection<Bundle> bundles = activator.findProviderBundles("org.apache.aries.mytest.MySPI");
        assertEquals(1, bundles.size());
        assertSame(implBundle, bundles.iterator().next());

        // The bc.registerService() call should now have been made
        EasyMock.verify(implBC);

        // Call removedBundle();
        customizer.removedBundle(implBundle, null, registrations);
        // sreg.unregister() should have been called.
        EasyMock.verify(sreg);
    }

    @Test
    public void testCustomAttributesBundle() throws Exception {
        Bundle mediatorBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mediatorBundle.getBundleId()).andReturn(42l).anyTimes();
        EasyMock.replay(mediatorBundle);
        BaseActivator activator = new BaseActivator() {
            @Override
            public void start(BundleContext context) throws Exception {}
        };

        ProviderBundleTrackerCustomizer customizer = new ProviderBundleTrackerCustomizer(activator, mediatorBundle);

        ServiceRegistration sreg = EasyMock.createMock(ServiceRegistration.class);
        EasyMock.replay(sreg);

        BundleContext implBC = mockSPIBundleContext(sreg);
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.REQUIRE_CAPABILITY, SpiFlyConstants.PROVIDER_REQUIREMENT);
        headers.put(SpiFlyConstants.PROVIDE_CAPABILITY, SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI; approval=yeah; ");
        Bundle implBundle = mockSPIBundle(implBC, headers);

        List<ServiceRegistration> registrations = customizer.addingBundle(implBundle, null);
        assertEquals(1, registrations.size());
        Collection<Bundle> bundles = activator.findProviderBundles("org.apache.aries.mytest.MySPI");
        assertEquals(1, bundles.size());
        assertSame(implBundle, bundles.iterator().next());

        Map<String, Object> attrs = activator.getCustomBundleAttributes("org.apache.aries.mytest.MySPI", implBundle);
        assertEquals(1, attrs.size());
        assertEquals("yeah", attrs.get("approval"));
    }

    @Test
    public void testNonServiceRegistryBundle() throws Exception {
        Bundle mediatorBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mediatorBundle.getBundleId()).andReturn(42l).anyTimes();
        EasyMock.replay(mediatorBundle);
        BaseActivator activator = new BaseActivator() {
            @Override
            public void start(BundleContext context) throws Exception {}
        };

        ProviderBundleTrackerCustomizer customizer = new ProviderBundleTrackerCustomizer(activator, mediatorBundle);

        ServiceRegistration sreg = EasyMock.createMock(ServiceRegistration.class);
        EasyMock.replay(sreg);

        BundleContext implBC = mockSPIBundleContext(sreg);
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.REQUIRE_CAPABILITY, SpiFlyConstants.PROVIDER_REQUIREMENT);
        headers.put(SpiFlyConstants.PROVIDE_CAPABILITY, SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI; approval=yeah;" +
                SpiFlyConstants.REGISTER_DIRECTIVE + ":=\"\"");
        Bundle implBundle = mockSPIBundle(implBC, headers);

        List<ServiceRegistration> registrations = customizer.addingBundle(implBundle, null);
        assertEquals(0, registrations.size());
        Collection<Bundle> bundles = activator.findProviderBundles("org.apache.aries.mytest.MySPI");
        assertEquals(1, bundles.size());
        assertSame(implBundle, bundles.iterator().next());

        Map<String, Object> attrs = activator.getCustomBundleAttributes("org.apache.aries.mytest.MySPI", implBundle);
        assertEquals(1, attrs.size());
        assertEquals("yeah", attrs.get("approval"));
    }

    @Test
    public void testRegisterAltAttributeDatatype() throws Exception {
        // TODO
    }

    @Test
    public void testServiceSubsetSelectionAndRegistrationProperties() throws Exception {
        Bundle mediatorBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mediatorBundle.getBundleId()).andReturn(42l).anyTimes();
        EasyMock.replay(mediatorBundle);

        BaseActivator activator = new BaseActivator() {
            @Override
            public void start(BundleContext context) throws Exception {}
        };

        ProviderBundleTrackerCustomizer customizer = new ProviderBundleTrackerCustomizer(activator, mediatorBundle);

        BundleContext implBC = mockSPIBundleContext4();
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.REQUIRE_CAPABILITY, SpiFlyConstants.PROVIDER_REQUIREMENT);
        headers.put(SpiFlyConstants.PROVIDE_CAPABILITY, SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI2; approval=yeah; " +
        		SpiFlyConstants.REGISTER_DIRECTIVE + ":=\"org.apache.aries.spifly.impl4.MySPIImpl4b\"");
        Bundle implBundle = mockSPIBundle4(implBC, headers);

        List<ServiceRegistration> registrations = customizer.addingBundle(implBundle, null);
        assertEquals(1, registrations.size());
        assertEquals("org.apache.aries.mytest.MySPI2", registrations.iterator().next().getReference().getProperty(Constants.OBJECTCLASS));
        assertNotNull(registrations.iterator().next().getReference().getProperty(SpiFlyConstants.SERVICELOADER_MEDIATOR_PROPERTY));
        assertEquals("yeah", registrations.iterator().next().getReference().getProperty("approval"));
    }

    @Test
    public void testProvidedSPIDirective() throws Exception {
        Bundle mediatorBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mediatorBundle.getBundleId()).andReturn(42l).anyTimes();
        EasyMock.replay(mediatorBundle);

        BaseActivator activator = new BaseActivator() {
            @Override
            public void start(BundleContext context) throws Exception {}
        };

        ProviderBundleTrackerCustomizer customizer = new ProviderBundleTrackerCustomizer(activator, mediatorBundle);

        BundleContext implBC = mockSPIBundleContext4();
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.REQUIRE_CAPABILITY, SpiFlyConstants.PROVIDER_REQUIREMENT);
        headers.put(SpiFlyConstants.PROVIDE_CAPABILITY,
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI; approval=yeah, " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI2");
        Bundle implBundle = mockSPIBundle4(implBC, headers);

        List<ServiceRegistration> registrations = customizer.addingBundle(implBundle, null);
        assertEquals("Expected 3 registrations, one for MySPI and 2 for MySPI2", 3, registrations.size());
        Set<String> expectedObjectClasses = new HashSet<String>(Arrays.asList("org.apache.aries.mytest.MySPI", "org.apache.aries.mytest.MySPI2"));
        Set<String> actualObjectClasses = new HashSet<String>();

        boolean foundMySPI = false;
        boolean foundMySPI2 = false;
        for (ServiceRegistration sr : registrations) {
            actualObjectClasses.add((String) sr.getReference().getProperty(Constants.OBJECTCLASS));
            assertNotNull(sr.getReference().getProperty(SpiFlyConstants.SERVICELOADER_MEDIATOR_PROPERTY));
            if ("org.apache.aries.mytest.MySPI".equals(sr.getReference().getProperty(Constants.OBJECTCLASS))) {
                assertEquals("yeah", sr.getReference().getProperty("approval"));
                foundMySPI = true;
            } else if ("org.apache.aries.mytest.MySPI2".equals(sr.getReference().getProperty(Constants.OBJECTCLASS))) {
                assertNull(sr.getReference().getProperty("approval"));
                foundMySPI2 = true;
            }
        }
        assertTrue(foundMySPI);
        assertTrue(foundMySPI2);

        assertEquals(expectedObjectClasses, actualObjectClasses);
    }

    @Test
    public void testMultipleServiceInstancesAndTypes() throws Exception {
        Bundle mediatorBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mediatorBundle.getBundleId()).andReturn(42l).anyTimes();
        EasyMock.replay(mediatorBundle);

        BaseActivator activator = new BaseActivator() {
            @Override
            public void start(BundleContext context) throws Exception {}
        };

        ProviderBundleTrackerCustomizer customizer = new ProviderBundleTrackerCustomizer(activator, mediatorBundle);

        BundleContext implBC = mockSPIBundleContext4();
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.REQUIRE_CAPABILITY, SpiFlyConstants.PROVIDER_REQUIREMENT);
        headers.put(SpiFlyConstants.PROVIDE_CAPABILITY,
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI," +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI2");
        Bundle implBundle = mockSPIBundle4(implBC, headers);

        List<ServiceRegistration> registrations = customizer.addingBundle(implBundle, null);
        assertEquals(3, registrations.size());

        boolean foundA = false, foundB = false, foundC = false;
        for (ServiceRegistration sreg : registrations) {
            ServiceReference sref = sreg.getReference();
            String objectClassName = sref.getProperty(Constants.OBJECTCLASS).toString();
            String serviceImplClassName = sref.getProperty("serviceObject").getClass().getName();
            if (MySPIImpl4a.class.getName().equals(serviceImplClassName)) {
                assertEquals("org.apache.aries.mytest.MySPI", objectClassName);
                foundA = true;
            } else if (MySPIImpl4b.class.getName().equals(serviceImplClassName)) {
                assertEquals("org.apache.aries.mytest.MySPI2", objectClassName);
                foundB = true;
            } else if (MySPIImpl4c.class.getName().equals(serviceImplClassName)) {
                assertEquals("org.apache.aries.mytest.MySPI2", objectClassName);
                foundC = true;
            }
        }

        assertTrue(foundA);
        assertTrue(foundB);
        assertTrue(foundC);
    }

    @Test
    public void testNoServiceRegistration() throws Exception {
        Bundle mediatorBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mediatorBundle.getBundleId()).andReturn(42l).anyTimes();
        EasyMock.replay(mediatorBundle);
        BaseActivator activator = new BaseActivator() {
            @Override
            public void start(BundleContext context) throws Exception {}
        };

        ProviderBundleTrackerCustomizer customizer = new ProviderBundleTrackerCustomizer(activator, mediatorBundle);

        ServiceRegistration sreg = EasyMock.createMock(ServiceRegistration.class);
        EasyMock.replay(sreg);

        BundleContext implBC = mockSPIBundleContext(sreg);
        Bundle implBundle = mockSPIBundle(implBC, SpiFlyConstants.PROVIDER_REQUIREMENT);

        List<ServiceRegistration> registrations = customizer.addingBundle(implBundle, null);
        assertEquals(0, registrations.size());
        Collection<Bundle> bundles = activator.findProviderBundles("org.apache.aries.mytest.MySPI");
        assertEquals(1, bundles.size());
        assertSame(implBundle, bundles.iterator().next());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAddingBundleWithBundleClassPath() throws Exception {
        Bundle mediatorBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mediatorBundle.getBundleId()).andReturn(42l).anyTimes();
        EasyMock.replay(mediatorBundle);
        BaseActivator activator = new BaseActivator() {
            @Override
            public void start(BundleContext context) throws Exception {}
        };

        ProviderBundleTrackerCustomizer customizer = new ProviderBundleTrackerCustomizer(activator, mediatorBundle);

        BundleContext implBC = EasyMock.createMock(BundleContext.class);
        EasyMock.<Object>expect(implBC.registerService(
                EasyMock.eq("org.apache.aries.mytest.MySPI"),
                EasyMock.isA(MySPIImpl2a.class),
                (Dictionary<String,?>) EasyMock.anyObject())).andReturn(EasyMock.createNiceMock(ServiceRegistration.class));
        EasyMock.<Object>expect(implBC.registerService(
                EasyMock.eq("org.apache.aries.mytest.MySPI"),
                EasyMock.isA(MySPIImpl2b.class),
                (Dictionary<String,?>) EasyMock.anyObject())).andReturn(EasyMock.createNiceMock(ServiceRegistration.class));
        EasyMock.<Object>expect(implBC.registerService(
                EasyMock.eq("org.apache.aries.mytest.MySPI"),
                EasyMock.isA(MySPIImpl3.class),
                (Dictionary<String,?>) EasyMock.anyObject())).andReturn(EasyMock.createNiceMock(ServiceRegistration.class));
        EasyMock.replay(implBC);


        Bundle implBundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(implBundle.getBundleContext()).andReturn(implBC).anyTimes();

        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.REQUIRE_CAPABILITY, SpiFlyConstants.PROVIDER_REQUIREMENT);
        headers.put(SpiFlyConstants.PROVIDE_CAPABILITY,
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "; " +
                SpiFlyConstants.SERVICELOADER_CAPABILITY_NAMESPACE + "=org.apache.aries.mytest.MySPI");
        headers.put(Constants.BUNDLE_CLASSPATH, ".,non-jar.jar,embedded.jar,embedded2.jar");
        EasyMock.expect(implBundle.getHeaders()).andReturn(headers).anyTimes();

        URL embeddedJar = getClass().getResource("/embedded.jar");
        assertNotNull("precondition", embeddedJar);
        EasyMock.expect(implBundle.getResource("embedded.jar")).andReturn(embeddedJar).anyTimes();
        URL embedded2Jar = getClass().getResource("/embedded2.jar");
        assertNotNull("precondition", embedded2Jar);
        EasyMock.expect(implBundle.getResource("embedded2.jar")).andReturn(embedded2Jar).anyTimes();
        URL dir = new URL("jar:" + embeddedJar + "!/META-INF/services");
        assertNotNull("precondition", dir);
        EasyMock.expect(implBundle.getResource("/META-INF/services")).andReturn(dir).anyTimes();
        EasyMock.expect(implBundle.findEntries((String) EasyMock.anyObject(), (String) EasyMock.anyObject(), EasyMock.anyBoolean())).
            andReturn(null).anyTimes();

        ClassLoader cl = new URLClassLoader(new URL [] {embeddedJar}, getClass().getClassLoader());
        Class<?> clsA = cl.loadClass("org.apache.aries.spifly.impl2.MySPIImpl2a");
        EasyMock.<Object>expect(implBundle.loadClass("org.apache.aries.spifly.impl2.MySPIImpl2a")).andReturn(clsA).anyTimes();
        Class<?> clsB = cl.loadClass("org.apache.aries.spifly.impl2.MySPIImpl2b");
        EasyMock.<Object>expect(implBundle.loadClass("org.apache.aries.spifly.impl2.MySPIImpl2b")).andReturn(clsB).anyTimes();
        ClassLoader cl2 = new URLClassLoader(new URL [] {embedded2Jar}, getClass().getClassLoader());
        Class<?> clsC = cl2.loadClass("org.apache.aries.spifly.impl3.MySPIImpl3");
        EasyMock.<Object>expect(implBundle.loadClass("org.apache.aries.spifly.impl3.MySPIImpl3")).andReturn(clsC).anyTimes();
        EasyMock.replay(implBundle);

        assertEquals("Precondition", 0, activator.findProviderBundles("org.apache.aries.mytest.MySPI").size());
        // Call addingBundle();
        customizer.addingBundle(implBundle, null);
        Collection<Bundle> bundles = activator.findProviderBundles("org.apache.aries.mytest.MySPI");
        assertEquals(1, bundles.size());
        assertSame(implBundle, bundles.iterator().next());

        // The bc.registerService() call should now have been made
        EasyMock.verify(implBC);
    }

    @SuppressWarnings("unchecked")
    private BundleContext mockSPIBundleContext(ServiceRegistration sreg) {
        BundleContext implBC = EasyMock.createMock(BundleContext.class);
        EasyMock.<Object>expect(implBC.registerService(
                EasyMock.eq("org.apache.aries.mytest.MySPI"),
                EasyMock.isA(MySPIImpl1.class),
                (Dictionary<String,?>) EasyMock.anyObject())).andReturn(sreg);
        EasyMock.replay(implBC);
        return implBC;
    }

    private Bundle mockSPIBundle(BundleContext implBC, String spiProviderHeader) throws ClassNotFoundException {
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.REQUIRE_CAPABILITY, spiProviderHeader);
        return mockSPIBundle(implBC, headers);
    }

    private Bundle mockSPIBundle(BundleContext implBC, Dictionary<String, String> headers) throws ClassNotFoundException {
        if (headers == null)
            headers = new Hashtable<String, String>();

        Bundle implBundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(implBundle.getBundleContext()).andReturn(implBC).anyTimes();
        EasyMock.expect(implBundle.getHeaders()).andReturn(headers).anyTimes();

        // List the resources found at META-INF/services in the test bundle
        URL dir = getClass().getResource("impl1/META-INF/services");
        assertNotNull("precondition", dir);
        EasyMock.expect(implBundle.getResource("/META-INF/services")).andReturn(dir).anyTimes();
        URL res = getClass().getResource("impl1/META-INF/services/org.apache.aries.mytest.MySPI");
        assertNotNull("precondition", res);
        EasyMock.expect(implBundle.findEntries("META-INF/services", "*", false)).andReturn(
                Collections.enumeration(Collections.singleton(res))).anyTimes();
        Class<?> cls = getClass().getClassLoader().loadClass("org.apache.aries.spifly.impl1.MySPIImpl1");
        EasyMock.<Object>expect(implBundle.loadClass("org.apache.aries.spifly.impl1.MySPIImpl1")).andReturn(cls).anyTimes();
        EasyMock.replay(implBundle);
        return implBundle;
    }

    @SuppressWarnings("unchecked")
    private BundleContext mockSPIBundleContext4() {
        BundleContext implBC = EasyMock.createNiceMock(BundleContext.class);

        EasyMock.expect(implBC.
            registerService((String) EasyMock.anyObject(), EasyMock.anyObject(), (Dictionary<String,?>)EasyMock.anyObject())).
            andAnswer(new IAnswer<ServiceRegistration>() {
                @Override
                public ServiceRegistration answer() throws Throwable {
                    final String className = (String) EasyMock.getCurrentArguments()[0];
                    final Object serviceObject = EasyMock.getCurrentArguments()[1];
                    final Dictionary<String,?> registrationProps =
                            (Dictionary<String, ?>) EasyMock.getCurrentArguments()[2];

                    ServiceReference sref = EasyMock.createMock(ServiceReference.class);
                    EasyMock.expect(sref.getProperty(EasyMock.anyObject(String.class))).andAnswer(new IAnswer<Object>() {
                        @Override
                        public Object answer() throws Throwable {
                            Object prop = EasyMock.getCurrentArguments()[0];
                            if (Constants.OBJECTCLASS.equals(prop)) {
                                return className;
                            } else if ("serviceObject".equals(prop)) {
                                // just used by the test to check the service object that was registered.
                                return serviceObject;
                            } else {
                                return registrationProps.get(prop);
                            }
                        }
                    }).anyTimes();
                    EasyMock.replay(sref);

                    ServiceRegistration sreg = EasyMock.createMock(ServiceRegistration.class);
                    EasyMock.expect(sreg.getReference()).andReturn(sref).anyTimes();
                    EasyMock.replay(sreg);
                    return sreg;
                }
            }).anyTimes();

        EasyMock.replay(implBC);
        return implBC;
    }

    private Bundle mockSPIBundle4(BundleContext implBC, Dictionary<String, String> headers) throws ClassNotFoundException {
        Bundle implBundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(implBundle.getBundleContext()).andReturn(implBC).anyTimes();
        EasyMock.expect(implBundle.getHeaders()).andReturn(headers).anyTimes();

        // List the resources found at META-INF/services in the test bundle
        URL dir = getClass().getResource("impl4/META-INF/services");
        assertNotNull("precondition", dir);
        EasyMock.expect(implBundle.getResource("/META-INF/services")).andReturn(dir).anyTimes();
        URL res = getClass().getResource("impl4/META-INF/services/org.apache.aries.mytest.MySPI");
        assertNotNull("precondition", res);
        URL res2 = getClass().getResource("impl4/META-INF/services/org.apache.aries.mytest.MySPI2");
        assertNotNull("precondition", res2);

        EasyMock.expect(implBundle.findEntries("META-INF/services", "*", false)).andReturn(
                Collections.enumeration(Arrays.asList(res, res2))).anyTimes();

        Class<?> clsa = getClass().getClassLoader().loadClass("org.apache.aries.spifly.impl4.MySPIImpl4a");
        EasyMock.<Object>expect(implBundle.loadClass("org.apache.aries.spifly.impl4.MySPIImpl4a")).andReturn(clsa).anyTimes();
        Class<?> clsb = getClass().getClassLoader().loadClass("org.apache.aries.spifly.impl4.MySPIImpl4b");
        EasyMock.<Object>expect(implBundle.loadClass("org.apache.aries.spifly.impl4.MySPIImpl4b")).andReturn(clsb).anyTimes();
        Class<?> clsc = getClass().getClassLoader().loadClass("org.apache.aries.spifly.impl4.MySPIImpl4c");
        EasyMock.<Object>expect(implBundle.loadClass("org.apache.aries.spifly.impl4.MySPIImpl4c")).andReturn(clsc).anyTimes();


        EasyMock.replay(implBundle);
        return implBundle;
    }
}
