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
package org.apache.aries.transaction.itests;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.vmOption;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.modifyBundle;

import java.io.IOException;
import java.io.InputStream;

import org.junit.runner.RunWith;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Customizer;
import org.ops4j.pax.exam.Inject;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.container.def.options.VMOption;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.BootClasspathLibraryOption;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.TimeoutOption;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;
import org.osgi.util.tracker.ServiceTracker;

@RunWith(JUnit4TestRunner.class)
public abstract class AbstractIntegrationTest {

    public static final long DEFAULT_TIMEOUT = 30000;
    
    @Inject
    protected BundleContext bundleContext;

    @org.ops4j.pax.exam.junit.Configuration
    public static Option[] configuration() {
        Option[] options = options(
                bootDelegationPackages("javax.transaction", "javax.transaction.*"),
                vmOption("-Dorg.osgi.framework.system.packages=javax.accessibility,javax.activation,javax.activity,javax.annotation,javax.annotation.processing,javax.crypto,javax.crypto.interfaces,javax.crypto.spec,javax.imageio,javax.imageio.event,javax.imageio.metadata,javax.imageio.plugins.bmp,javax.imageio.plugins.jpeg,javax.imageio.spi,javax.imageio.stream,javax.jws,javax.jws.soap,javax.lang.model,javax.lang.model.element,javax.lang.model.type,javax.lang.model.util,javax.management,javax.management.loading,javax.management.modelmbean,javax.management.monitor,javax.management.openmbean,javax.management.relation,javax.management.remote,javax.management.remote.rmi,javax.management.timer,javax.naming,javax.naming.directory,javax.naming.event,javax.naming.ldap,javax.naming.spi,javax.net,javax.net.ssl,javax.print,javax.print.attribute,javax.print.attribute.standard,javax.print.event,javax.rmi,javax.rmi.CORBA,javax.rmi.ssl,javax.script,javax.security.auth,javax.security.auth.callback,javax.security.auth.kerberos,javax.security.auth.login,javax.security.auth.spi,javax.security.auth.x500,javax.security.cert,javax.security.sasl,javax.sound.midi,javax.sound.midi.spi,javax.sound.sampled,javax.sound.sampled.spi,javax.sql,javax.sql.rowset,javax.sql.rowset.serial,javax.sql.rowset.spi,javax.swing,javax.swing.border,javax.swing.colorchooser,javax.swing.event,javax.swing.filechooser,javax.swing.plaf,javax.swing.plaf.basic,javax.swing.plaf.metal,javax.swing.plaf.multi,javax.swing.plaf.synth,javax.swing.table,javax.swing.text,javax.swing.text.html,javax.swing.text.html.parser,javax.swing.text.rtf,javax.swing.tree,javax.swing.undo,javax.tools,javax.xml,javax.xml.bind,javax.xml.bind.annotation,javax.xml.bind.annotation.adapters,javax.xml.bind.attachment,javax.xml.bind.helpers,javax.xml.bind.util,javax.xml.crypto,javax.xml.crypto.dom,javax.xml.crypto.dsig,javax.xml.crypto.dsig.dom,javax.xml.crypto.dsig.keyinfo,javax.xml.crypto.dsig.spec,javax.xml.datatype,javax.xml.namespace,javax.xml.parsers,javax.xml.soap,javax.xml.stream,javax.xml.stream.events,javax.xml.stream.util,javax.xml.transform,javax.xml.transform.dom,javax.xml.transform.sax,javax.xml.transform.stax,javax.xml.transform.stream,javax.xml.validation,javax.xml.ws,javax.xml.ws.handler,javax.xml.ws.handler.soap,javax.xml.ws.http,javax.xml.ws.soap,javax.xml.ws.spi,javax.xml.xpath,org.ietf.jgss,org.omg.CORBA,org.omg.CORBA.DynAnyPackage,org.omg.CORBA.ORBPackage,org.omg.CORBA.TypeCodePackage,org.omg.CORBA.portable,org.omg.CORBA_2_3,org.omg.CORBA_2_3.portable,org.omg.CosNaming,org.omg.CosNaming.NamingContextExtPackage,org.omg.CosNaming.NamingContextPackage,org.omg.Dynamic,org.omg.DynamicAny,org.omg.DynamicAny.DynAnyFactoryPackage,org.omg.DynamicAny.DynAnyPackage,org.omg.IOP,org.omg.IOP.CodecFactoryPackage,org.omg.IOP.CodecPackage,org.omg.Messaging,org.omg.PortableInterceptor,org.omg.PortableInterceptor.ORBInitInfoPackage,org.omg.PortableServer,org.omg.PortableServer.CurrentPackage,org.omg.PortableServer.POAManagerPackage,org.omg.PortableServer.POAPackage,org.omg.PortableServer.ServantLocatorPackage,org.omg.PortableServer.portable,org.omg.SendingContext,org.omg.stub.java.rmi,org.w3c.dom,org.w3c.dom.bootstrap,org.w3c.dom.css,org.w3c.dom.events,org.w3c.dom.html,org.w3c.dom.ls,org.w3c.dom.ranges,org.w3c.dom.stylesheets,org.w3c.dom.traversal,org.w3c.dom.views,org.xml.sax,org.xml.sax.ext,org.xml.sax.helpers,javax.transaction;partial=true;mandatory:=partial,javax.transaction.xa;partial=true;mandatory:=partial"),

                // Log
                mavenBundle("org.ops4j.pax.logging", "pax-logging-api"),
                mavenBundle("org.ops4j.pax.logging", "pax-logging-service"),

                // this is how you set the default log level when using pax
                // logging (logProfile)
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("DEBUG"),

                // Bundles
                mavenBundle("org.osgi", "org.osgi.compendium"),
                mavenBundle("asm", "asm-all"),
                mavenBundle("org.apache.derby", "derby"),
                mavenBundle("org.apache.aries", "org.apache.aries.util"),
                mavenBundle("org.apache.aries.blueprint", "org.apache.aries.blueprint"), 
                mavenBundle("org.apache.aries.transaction", "org.apache.aries.transaction.manager"),
                mavenBundle("org.apache.aries.transaction", "org.apache.aries.transaction.blueprint"),
                mavenBundle("org.apache.aries.transaction", "org.apache.aries.transaction.wrappers"),
                mavenBundle("org.apache.aries.transaction", "org.apache.aries.transaction.testbundle"),
                mavenBundle("org.apache.aries.transaction", "org.apache.aries.transaction.testds"),

                //new VMOption( "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000" ),
                //new TimeoutOption( 0 ),

                equinox().version("3.5.0"));
        options = updateOptions(options);
        return options;
    }

    protected static MavenArtifactProvisionOption mavenBundle(String groupId, String artifactId) {
        return CoreOptions.mavenBundle().groupId(groupId).artifactId(artifactId)
            .versionAsInProject();
      }

    protected Bundle getBundle(String symbolicName) {
        return getBundle(symbolicName, null);
      }

    protected Bundle getBundle(String bundleSymbolicName, String version) {
        Bundle result = null;
        for (Bundle b : bundleContext.getBundles()) {
          if (b.getSymbolicName().equals(bundleSymbolicName)) {
            if (version == null
                || b.getVersion().equals(Version.parseVersion(version))) {
              result = b;
              break;
            }
          }
        }
        return result;
      }

    protected static Option[] updateOptions(Option[] options) {
        // We need to add pax-exam-junit here when running with the ibm
        // jdk to avoid the following exception during the test run:
        // ClassNotFoundException: org.ops4j.pax.exam.junit.Configuration
        if ("IBM Corporation".equals(System.getProperty("java.vendor"))) {
          Option[] ibmOptions = options(wrappedBundle(mavenBundle(
              "org.ops4j.pax.exam", "pax-exam-junit")));
          options = combine(ibmOptions, options);
        }
    
        return options;
      }

    protected <T> T getOsgiService(Class<T> type, long timeout) {
        return getOsgiService(type, null, timeout);
      }

    protected <T> T getOsgiService(Class<T> type) {
        return getOsgiService(type, null, DEFAULT_TIMEOUT);
      }

    protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
        return getOsgiService(null, type, filter, timeout);
      }

    protected <T> T getOsgiService(BundleContext bc, Class<T> type, String filter,
            long timeout) {
                ServiceTracker tracker = null;
                try {
                  String flt;
                  if (filter != null) {
                    if (filter.startsWith("(")) {
                      flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")"
                          + filter + ")";
                    } else {
                      flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")("
                          + filter + "))";
                    }
                  } else {
                    flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
                  }
                  Filter osgiFilter = FrameworkUtil.createFilter(flt);
                  tracker = new ServiceTracker(bc == null ? bundleContext : bc, osgiFilter,
                      null);
                  tracker.open();
                  // Note that the tracker is not closed to keep the reference
                  // This is buggy, has the service reference may change i think
                  Object svc = type.cast(tracker.waitForService(timeout));
                  if (svc == null) {
                    throw new RuntimeException("Gave up waiting for service " + flt);
                  }
                  return type.cast(svc);
                } catch (InvalidSyntaxException e) {
                  throw new IllegalArgumentException("Invalid filter", e);
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              }

}
