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
package org.apache.aries.subsystem.core.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleRevision;

public class RegionContextBundleHelper {
	public static final String SYMBOLICNAME_PREFIX = Constants.RegionContextBundleSymbolicNamePrefix;
	public static final Version VERSION = Version.parseVersion("1.0.0");
	
	public static void installRegionContextBundle(BasicSubsystem subsystem) throws BundleException, IOException {
		String symbolicName = SYMBOLICNAME_PREFIX + subsystem.getSubsystemId();
		String location = subsystem.getLocation() + '/' + subsystem.getSubsystemId();
		Bundle b = subsystem.getRegion().getBundle(symbolicName, VERSION);
		if (b == null) {
			ThreadLocalSubsystem.set(subsystem);
			b = subsystem.getRegion().installBundleAtLocation(location, createRegionContextBundle(symbolicName));
		}
		Utils.installResource(b.adapt(BundleRevision.class), subsystem);
		// The region context bundle must be started persistently.
		b.start();
	}
	
	public static void uninstallRegionContextBundle(BasicSubsystem subsystem) {
		String symbolicName = SYMBOLICNAME_PREFIX + subsystem.getSubsystemId();
		Bundle bundle = subsystem.getRegion().getBundle(symbolicName, VERSION);
		if (bundle == null)
			throw new IllegalStateException("Missing region context bundle: " + symbolicName);
		ThreadLocalSubsystem.set(subsystem);
		BundleRevision revision = bundle.adapt(BundleRevision.class);
		try {
			bundle.uninstall();
		}
		catch (BundleException e) {
			// TODO Should we really eat this? At least log it?
		}
		ResourceUninstaller.newInstance(revision, subsystem).uninstall();
	}
	
	private static Manifest createManifest(String symbolicName) {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().putValue(Constants.BundleSymbolicName, symbolicName);
		manifest.getMainAttributes().putValue(Constants.BundleVersion, VERSION.toString());
		return manifest;
	}
	
	private static InputStream createRegionContextBundle(String symbolicName) throws IOException {
		Manifest manifest = createManifest(symbolicName);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JarOutputStream jos = new JarOutputStream(baos, manifest);
		jos.close();
		return new ByteArrayInputStream(baos.toByteArray());
	}
}
