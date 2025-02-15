/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aries.subsystem.itests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.MavenConfiguredJUnit4TestRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.Version;
import org.osgi.service.subsystem.Subsystem;

@RunWith(MavenConfiguredJUnit4TestRunner.class)
public class RootSubsystemTest extends SubsystemTest {
	private static final String BUNDLE_A = "bundle.a";
	
	private static boolean createdTestFiles;
	@Before
	public static void createTestFiles() throws Exception {
		if (createdTestFiles)
			return;
		createBundleA();
		createdTestFiles = true;
	}
	
	private static void createBundleA() throws IOException {
		createBundle(BUNDLE_A);
	}
	
	// TODO Test root subsystem headers.
	
	@Test
	public void testDoNotStartExtraneousRootRegionBundles() throws Exception {
		bundleContext.installBundle(new File(BUNDLE_A).toURI().toURL().toString());
		getSubsystemCoreBundle().stop();
		getSubsystemCoreBundle().start();
		Bundle bundleA = findBundleBySymbolicName(BUNDLE_A);
		assertEquals("Extraneous root region bundle should not be started", Bundle.INSTALLED, bundleA.getState());
	}
	
	@Test
	public void testId() {
		assertEquals("Wrong root ID", getRootSubsystem().getSubsystemId(), 0);
	}
	
	@Test
	public void testLocation() {
		assertEquals("Wrong root location", getRootSubsystem().getLocation(), "subsystem://?Subsystem-SymbolicName=org.osgi.service.subsystem.root&Subsystem-Version=1.0.0");
	}
	
	@Test
	public void testRegionContextBundle() throws BundleException {
		assertRegionContextBundle(getRootSubsystem());
		getSubsystemCoreBundle().stop();
		getSubsystemCoreBundle().start();
		assertRegionContextBundle(getRootSubsystem());
	}
	
	@Test
	public void testServiceEvents() throws Exception {
		Subsystem root = getRootSubsystem();
		Bundle core = getSubsystemCoreBundle();
		core.stop();
		assertServiceEventsStop(root);
		core.uninstall();
		core = bundleContext.installBundle(core.getLocation());
		core.start();
		// There should be install events since the persisted root subsystem was
		// deleted when the subsystems implementation bundle was uninstalled.
		assertServiceEventsInstall(root);
		assertServiceEventsResolve(root);
		assertServiceEventsStart(root);
		core.stop();
		assertServiceEventsStop(root);
		core.start();
		// There should be no install events or RESOLVING event since there
		// should be a persisted root subsystem already in the RESOLVED state.
		assertServiceEventResolved(root, ServiceEvent.REGISTERED);
		assertServiceEventsStart(root);
	}
	
	@Test
	public void testSymbolicName() {
		assertEquals("Wrong root symbolic name", getRootSubsystem().getSymbolicName(), "org.osgi.service.subsystem.root");
	}
	
	@Test
	public void testUninstallRootRegionBundleWithNoBundleEventHook() throws Exception {
		// Install an extraneous bundle into the root region. The bundle will
		// be recorded in the root subsystem's persistent memory.
		Bundle bundleA = bundleContext.installBundle(new File(BUNDLE_A).toURI().toURL().toString());
		try {
			Bundle core = getSubsystemCoreBundle();
			// Stop the subsystems bundle in order to unregister the bundle
			// event hook.
			core.stop();
			// Uninstall the bundle so it won't be there on restart.
			bundleA.uninstall();
			try {
				// Start the subsystems bundle and ensure the root subsystem
				// recovers from the uninstalled bundle being in persistent
				// memory.
				core.start();
			}
			catch (BundleException e) {
				fail("Could not start subsystems bundle after uninstalling a root region bundle with no bundle event hook registered");
			}
		}
		finally {
			if (Bundle.UNINSTALLED != bundleA.getState())
				bundleA.uninstall();
		}
	}
	
	@Test
	public void testVersion() {
		assertEquals("Wrong root version", getRootSubsystem().getVersion(), Version.parseVersion("1.0.0"));
	}
}
