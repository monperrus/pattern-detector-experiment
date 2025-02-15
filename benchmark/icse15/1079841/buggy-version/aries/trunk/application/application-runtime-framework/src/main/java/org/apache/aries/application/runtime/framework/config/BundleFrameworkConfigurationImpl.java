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

package org.apache.aries.application.runtime.framework.config;

import java.util.Properties;

import org.apache.aries.application.management.spi.framework.BundleFrameworkConfiguration;

public class BundleFrameworkConfigurationImpl implements BundleFrameworkConfiguration
{

  String frameworkId;
  Properties frameworkBundleManifest;
  Properties frameworkConfig;

  public BundleFrameworkConfigurationImpl(String frameworkId, Properties frameworkConfig,
      Properties frameworkBundleManifest)
  {
    this.frameworkId = frameworkId;
    this.frameworkConfig = frameworkConfig;
    this.frameworkBundleManifest = frameworkBundleManifest;
  }

  public String getFrameworkID()
  {
    return frameworkId;
  }

  public Properties getFrameworkManifest()
  {
    return frameworkBundleManifest;
  }

  public Properties getFrameworkProperties()
  {
    return frameworkConfig;
  }

}
