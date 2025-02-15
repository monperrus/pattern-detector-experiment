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
 * "AS IS" BASIS, WITHOUT WARRANTIESOR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.jndi.url;

import java.util.Enumeration;

import javax.naming.CompositeName;

/**
 * A composite name for the aries namespace. We only have this so that we can
 * provide a nicer toString()
 */
public final class ServiceRegistryName extends CompositeName
{
  /** The serial version UID */
  private static final long serialVersionUID = 6617580228852444656L;
  
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    
    buffer.append("aries:services");
    Enumeration<String> components = getAll();
    while (components.hasMoreElements()) {
      buffer.append('/');
      buffer.append(components.nextElement());
    }
    
    return buffer.toString();
  }
}
