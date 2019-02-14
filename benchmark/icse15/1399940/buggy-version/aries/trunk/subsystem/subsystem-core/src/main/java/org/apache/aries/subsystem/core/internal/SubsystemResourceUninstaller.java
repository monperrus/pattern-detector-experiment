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

import org.apache.aries.util.io.IOUtils;
import org.osgi.resource.Resource;
import org.osgi.service.subsystem.Subsystem;
import org.osgi.service.subsystem.Subsystem.State;
import org.osgi.service.subsystem.SubsystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubsystemResourceUninstaller extends ResourceUninstaller {
	private static final Logger logger = LoggerFactory.getLogger(AriesSubsystem.class);
	
	private static void removeChild(AriesSubsystem parent, AriesSubsystem child) {
		Activator.getInstance().getSubsystems().removeChild(parent, child);
	}
	
	public SubsystemResourceUninstaller(Resource resource, AriesSubsystem subsystem) {
		super(resource, subsystem);
	}
	
	public void uninstall() {
		removeReferences();
		try {
			if (isResourceUninstallable())
				uninstallSubsystem();
		}
		finally {
			removeConstituents();
			removeChildren();
			removeSubsystem();
		}
	}
	
	private void removeChildren() {
		if (!isExplicit()) {
			removeChild((AriesSubsystem)subsystem, (AriesSubsystem)resource);
			return;
		}
		for (Subsystem subsystem : ((AriesSubsystem)resource).getParents())
			removeChild((AriesSubsystem)subsystem, (AriesSubsystem)resource);
	}
	
	private void removeConstituents() {
		if (!isExplicit()) {
			removeConstituent();
			return;
		}
		for (Subsystem subsystem : ((AriesSubsystem)resource).getParents())
			removeConstituent((AriesSubsystem)subsystem, (AriesSubsystem)resource);
	}
	
	private void removeReferences() {
		if (!isExplicit()) {
			removeReference();
			return;
		}
		for (Subsystem subsystem : ((AriesSubsystem)resource).getParents())
			removeReference((AriesSubsystem)subsystem, (AriesSubsystem)resource);
	}
	
	private void removeSubsystem() {
		Activator.getInstance().getSubsystems().removeSubsystem((AriesSubsystem)resource);
	}
	
	private void uninstallSubsystem() {
		AriesSubsystem subsystem = (AriesSubsystem) resource;
		try {
			if (subsystem.getState().equals(Subsystem.State.RESOLVED))
				subsystem.setState(State.INSTALLED);
			subsystem.setState(State.UNINSTALLING);
			Throwable firstError = null;
			for (Resource resource : Activator.getInstance().getSubsystems()
					.getResourcesReferencedBy(subsystem)) {
				// Don't uninstall the region context bundle here.
				if (Utils.isRegionContextBundle(resource))
					continue;
				try {
					ResourceUninstaller.newInstance(resource, subsystem)
							.uninstall();
				} catch (Throwable t) {
					logger.error("An error occurred while uninstalling resource "
							+ resource + " of subsystem " + subsystem, t);
					if (firstError == null)
						firstError = t;
				}
			}
			subsystem.setState(State.UNINSTALLED);
			Activator.getInstance().getSubsystemServiceRegistrar()
					.unregister(subsystem);
			if (subsystem.isScoped())
				RegionContextBundleHelper.uninstallRegionContextBundle(subsystem);
			if (firstError != null)
				throw new SubsystemException(firstError);
		}
		finally {
			// Let's be sure to always clean up the directory.
			IOUtils.deleteRecursive(subsystem.getDirectory());
		}
	}
}
