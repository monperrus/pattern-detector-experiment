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
package org.apache.aries.subsystem.itests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Currency;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Map;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.aries.subsystem.Subsystem;
import org.apache.aries.subsystem.SubsystemAdmin;
import org.apache.aries.subsystem.scope.ScopeAdmin;
import org.apache.aries.unittest.fixture.ArchiveFixture;
import org.apache.aries.unittest.fixture.ArchiveFixture.ZipFixture;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.container.def.PaxRunnerOptions;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;


@RunWith(JUnit4TestRunner.class)
public class SubsystemAdmin2Test extends AbstractIntegrationTest {

    /* Use @Before not @BeforeClass so as to ensure that these resources
     * are created in the paxweb temp directory, and not in the svn tree 
     */
    static boolean createdApplications = false;
    @Before
    public static void createApplications() throws Exception {
      if (createdApplications) { 
        return;
      }

      ZipFixture testEba = ArchiveFixture.newZip();
      
      testEba = testEba.binary("META-INF/MANIFEST.MF", 
              SubsystemAdminTest.class.getClassLoader().getResourceAsStream("subsystem2/META-INF/MANIFEST.MF"))
          .end();
      FileOutputStream fout = new FileOutputStream("test.eba");
      testEba.writeOut(fout);
      fout.close();
      createdApplications = true;
    }
    
    @Test
    public void test() throws Exception {
        // make sure we are using a framework that provides scope admin service
        ScopeAdmin scopeA = getOsgiService(ScopeAdmin.class);
        assertNotNull("scope admin should not be null", scopeA);
        System.out.println("able to get scope admin service");
        
        // obtain subsystem admin service
        SubsystemAdmin sa = getOsgiService(SubsystemAdmin.class);
        assertNotNull("subsystem admin should not be null", sa);
        System.out.println("able to get subsytem admin service");
        
        File f = new File("test.eba");
        Subsystem subsystem = sa.install(f.toURI().toURL().toExternalForm());
        assertNotNull("subsystem should not be null", subsystem);
        
        assertTrue("subsystem should have a unique id", subsystem.getSubsystemId() > 0);
        assertTrue(subsystem.getLocation().indexOf("test.eba") != -1);
        assertTrue(subsystem.getSymbolicName().indexOf("felix-file-install2") != -1);
        assertEquals("2.0.8", subsystem.getVersion().toString());
        Collection<Bundle> bundles = subsystem.getBundles();
        assertEquals("check bundles' size", 1, bundles.size());

    }

    @org.ops4j.pax.exam.junit.Configuration
    public static Option[] configuration() {
        Option[] options = options(
            // Log
            mavenBundle("org.ops4j.pax.logging", "pax-logging-api"),
            mavenBundle("org.ops4j.pax.logging", "pax-logging-service"),
            // Felix Config Admin
            mavenBundle("org.apache.felix", "org.apache.felix.configadmin"),
            // Felix mvn url handler
            mavenBundle("org.ops4j.pax.url", "pax-url-mvn"),


            // this is how you set the default log level when using pax logging (logProfile)
            systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("DEBUG"),

            // Bundles
            mavenBundle("org.apache.aries.subsystem", "org.apache.aries.subsystem.scope.api"),
            mavenBundle("org.apache.aries.subsystem", "org.apache.aries.subsystem.scope.impl"),
            mavenBundle("org.apache.aries.testsupport", "org.apache.aries.testsupport.unit"),
            mavenBundle("org.apache.aries.application", "org.apache.aries.application.api"),
            mavenBundle("org.apache.aries", "org.apache.aries.util"),
            mavenBundle("org.apache.aries.application", "org.apache.aries.application.utils"),
            mavenBundle("org.apache.felix", "org.apache.felix.bundlerepository"),
            mavenBundle("org.apache.aries.subsystem", "org.apache.aries.subsystem.api"),

            mavenBundle("org.apache.aries.subsystem", "org.apache.aries.subsystem.core"),

            //org.ops4j.pax.exam.container.def.PaxRunnerOptions.vmOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),

            PaxRunnerOptions.rawPaxRunnerOption("config", "classpath:ss-runner.properties"),

            equinox().version("3.7.0.v20110110")
        );
        options = updateOptions(options);
        return options;
    }

}
