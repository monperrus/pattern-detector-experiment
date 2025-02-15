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

import java.util.Map;

public class MethodRestriction {
    private final String methodName;
    private final ArgRestrictions argRestrictions;

    public MethodRestriction(String methodName) {
        this(methodName, null);
    }

    public MethodRestriction(String methodName, ArgRestrictions argRestrictions) {
        this.methodName = methodName;
        this.argRestrictions = argRestrictions;
    }

    public String getMethodName() {
        return methodName;
    }

    public String[] getArgClasses() {
        if (argRestrictions == null) {
            return null;
        }

        return argRestrictions.getArgClasses();
    }

    public boolean matches(String mtdName, Map<Pair<Integer, String>, String> args) {
        if (!methodName.equals(mtdName))
            return false;
        
        if (args == null) 
            return true;
        
        return argRestrictions.matches(args);
    }
}
