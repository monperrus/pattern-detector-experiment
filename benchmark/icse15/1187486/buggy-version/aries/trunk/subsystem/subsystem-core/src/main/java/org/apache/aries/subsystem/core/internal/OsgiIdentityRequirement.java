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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Requirement;
import org.osgi.framework.resource.Resource;
import org.osgi.framework.resource.ResourceConstants;
import org.osgi.service.subsystem.SubsystemException;

public class OsgiIdentityRequirement implements Requirement {
	private static Filter createFilter(String symbolicName, Version version, String type) {
		return createFilter(
				symbolicName,
				new StringBuilder()
					.append('(')
					.append(ResourceConstants.IDENTITY_VERSION_ATTRIBUTE)
					.append('=')
					.append(version)
					.append(')')
					.toString(),
				type);
	}
	
	private static Filter createFilter(String symbolicName, VersionRange versionRange, String type) {
		return createFilter(
				symbolicName,
				versionRange.toFilterString(ResourceConstants.IDENTITY_VERSION_ATTRIBUTE),
				type);
	}
	
	private static Filter createFilter(Resource resource) {
		Map<String, Object> attributes = resource.getCapabilities(ResourceConstants.IDENTITY_NAMESPACE).get(0).getAttributes();
		String symbolicName = String.valueOf(attributes.get(ResourceConstants.IDENTITY_NAMESPACE));
		Version version = Version.parseVersion(String.valueOf(attributes.get(ResourceConstants.IDENTITY_VERSION_ATTRIBUTE)));
		String type = String.valueOf(attributes.get(ResourceConstants.IDENTITY_TYPE_ATTRIBUTE));
		return createFilter(symbolicName, version, type);
	}
	
	private static Filter createFilter(String symbolicName, String versionFilter, String type) {
		try {
			return FrameworkUtil.createFilter(createFilterString(symbolicName, versionFilter, type));
		}
		catch (InvalidSyntaxException e) {
			throw new SubsystemException(e);
		}
	}
	
	private static String createFilterString(String symbolicName, String versionFilter, String type) {
		return new StringBuilder("(&(")
			.append(ResourceConstants.IDENTITY_NAMESPACE)
			.append('=')
			.append(symbolicName)
			.append(')')
			.append(versionFilter)
			.append('(')
			.append(ResourceConstants.IDENTITY_TYPE_ATTRIBUTE)
			.append('=')
			.append(type)
			.append("))").toString();
	}
	
	private final Map<String, String> directives = new HashMap<String, String>();
	private final Filter filter;
	private final Resource resource;
	private final boolean transitive;
	
	public OsgiIdentityRequirement(String symbolicName, VersionRange versionRange, String type, boolean transitive) {
		this(createFilter(symbolicName, versionRange, type), null, transitive);
	}
	
	public OsgiIdentityRequirement(String symbolicName, Version version, String type, boolean transitive) {
		this(createFilter(symbolicName, version, type), null, transitive);
	}
	
	public OsgiIdentityRequirement(Resource resource, boolean transitive) {
		this(createFilter(resource), resource, transitive);
	}
	
	private OsgiIdentityRequirement(Filter filter, Resource resource, boolean transitive) {
		this.filter = filter;
		this.resource = resource;
		this.transitive = transitive;
		directives.put(Constants.FILTER_DIRECTIVE, filter.toString());
		directives.put(ResourceConstants.IDENTITY_SINGLETON_DIRECTIVE, Boolean.FALSE.toString());
		directives.put(Constants.EFFECTIVE_DIRECTIVE, Constants.EFFECTIVE_RESOLVE);
	}

	@Override
	public Map<String, Object> getAttributes() {
		return Collections.emptyMap();
	}

	@Override
	public Map<String, String> getDirectives() {
		return Collections.unmodifiableMap(directives);
	}

	@Override
	public String getNamespace() {
		return ResourceConstants.IDENTITY_NAMESPACE;
	}

	@Override
	public Resource getResource() {
		return resource;
	}
	
	public boolean isTransitiveDependency() {
		return transitive;
	}

	@Override
	public boolean matches(Capability capability) {
		if (capability == null) return false;
		if (!capability.getNamespace().equals(getNamespace())) return false;
		if (!filter.matches(capability.getAttributes())) return false;
		// TODO Check directives.
		return true;
	}
}
