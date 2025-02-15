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

import org.osgi.resource.Capability;
import org.osgi.resource.Resource;

public class BasicCapability extends AbstractCapability {
	private final Map<String, Object> attributes;
	private final Map<String, String> directives;
	private final Resource resource;
	private final String namespace;
	
	public BasicCapability(Capability capability, Resource resource) {
		this(capability.getNamespace(), capability.getAttributes(), capability.getDirectives(), resource);
	}
	
	public BasicCapability(String namespace, Map<String, Object> attributes, Map<String, String> directives, Resource resource) {
		if (namespace == null)
			throw new NullPointerException();
		this.namespace = namespace;
		if (attributes == null)
			this.attributes = Collections.emptyMap();
		else
			this.attributes = Collections.unmodifiableMap(new HashMap<String, Object>(attributes));
		if (directives == null)
			this.directives = Collections.emptyMap();
		else
			this.directives = Collections.unmodifiableMap(new HashMap<String, String>(directives));
		if (resource == null)
			throw new NullPointerException();
		this.resource = resource;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Map<String, String> getDirectives() {
		return directives;
	}

	@Override
	public String getNamespace() {
		return namespace;
	}

	@Override
	public Resource getResource() {
		return resource;
	}
}
