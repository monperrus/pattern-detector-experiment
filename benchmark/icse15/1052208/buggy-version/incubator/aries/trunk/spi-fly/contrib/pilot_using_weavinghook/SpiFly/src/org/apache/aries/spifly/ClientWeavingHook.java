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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.aries.spifly.HeaderParser.PathElement;
import org.apache.aries.spifly.api.SpiFlyConstants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.service.log.LogService;

public class ClientWeavingHook implements WeavingHook {
    private final String addedImport;
    
    ClientWeavingHook(BundleContext context) {
        Bundle b = context.getBundle();
        String bver = b.getVersion().toString();
        String bsn = b.getSymbolicName();
        
        addedImport = Util.class.getPackage().getName() + 
            ";bundle-symbolic-name=" + bsn + 
            ";bundle-version=" + bver;
    }
    
	@Override
	public void weave(WovenClass wovenClass) {
	    Bundle consumerBundle = wovenClass.getBundleWiring().getBundle();
        String consumerHeader = consumerBundle.getHeaders().get(SpiFlyConstants.SPI_CONSUMER_HEADER);
        if (consumerHeader != null) {
	        Activator.activator.log(LogService.LOG_DEBUG, "Weaving class " + wovenClass.getClassName());            
            
            WeavingData wd = processHeader(consumerBundle, consumerHeader);
	        
	        ClassReader cr = new ClassReader(wovenClass.getBytes());
	        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
	        TCCLSetterVisitor tsv = new TCCLSetterVisitor(cw, wovenClass.getClassName(), wd);
	        cr.accept(tsv, 0);	        
	        wovenClass.setBytes(cw.toByteArray());
	        if (tsv.additionalImportRequired())
	            wovenClass.getDynamicImports().add(addedImport);
	    }			
	}

	/**
	 * Parses headers of the following syntax:
	 * <ul>
	 * <li><tt>org.acme.MyClass#myMethod</tt> - apply the weaving to all overloads of <tt>myMethod()</tt> 
	 * in <tt>MyClass</tt>
	 * <li><tt>org.acme.MyClass#myMethod(java.lang.String, java.util.List)</tt> - apply the weaving only 
	 * to the <tt>myMethod(String, List)</tt> overload in <tt>MyClass</tt>
	 * <li><tt>org.acme.MyClass#myMethod()</tt> - apply the weaving only to the noarg overload of 
	 * <tt>myMethod()</tt>
	 * <li><b>true</b> - equivalend to <tt>java.util.ServiceLoader#load(java.lang.Class)</tt>
	 * </ul>
	 * Additionally, it registers the consumer's contraints with the consumer registry in the activator, if the 
	 * consumer is only constrained to a certain set of bundles.<p/>
	 * 
	 * The following attributes are supported:
	 * <ul>
	 * <li><tt>bundle</tt> - restrict wiring to the bundle with the specifies Symbolic Name.
	 * <li><tt>bundleId</tt> - restrict wiring to the bundle with the specified bundle ID. Typically used when 
	 * the service should be forceably picked up from the system bundle (<tt>bundleId=0</tt>).
	 * </ul>
	 * 
	 * @param consumerBundle the consuming bundle.
	 * @param consumerHeader the <tt>SPI-Consumer</tt> header.
	 * @return an instance of the {@link WeavingData} class.
	 */
    private WeavingData processHeader(Bundle consumerBundle, String consumerHeader) {
        List<BundleDescriptor> allowedBundles = new ArrayList<BundleDescriptor>();

        for (PathElement element : HeaderParser.parseHeader(consumerHeader)) {
            Set<ConsumerRestriction> restrictions = new HashSet<ConsumerRestriction>();
            MethodRestriction methodRestriction = null;
            String name = element.getName().trim();
            String className;
            String methodName;
            
            int hashIdx = name.indexOf('#');
            if (hashIdx > 0) {                
                className = name.substring(0, hashIdx);
                int braceIdx = name.substring(hashIdx).indexOf('(');
                if (braceIdx > 0) {
                    methodName = name.substring(hashIdx + 1, hashIdx + braceIdx);
                    // Map<Integer, Map<String, String>> args = new HashMap<Integer, Map<String,String>>();
                    ArgRestrictions argRestrictions = new ArgRestrictions();
                    int closeIdx = name.substring(hashIdx).indexOf(')');
                    if (closeIdx > 0) {
                        String classes = name.substring(hashIdx + braceIdx + 1, hashIdx + closeIdx).trim();
                        if (classes.length() > 0) {
                            if (classes.indexOf('[') > 0) {
                                int argNumber = 0;
                                for (String s : classes.split(",")) {
                                    int idx = s.indexOf('[');
                                    int end = s.indexOf(idx, ']');
                                    if (idx > 0 && end > idx) {
                                        argRestrictions.addRestriction(argNumber, s.substring(0, idx), s.substring(idx + 1, end));
                                    } else {
                                        argRestrictions.addRestriction(argNumber, s);
                                    }
                                    argNumber++;
                                }
                            } else {
                                String[] classNames = classes.split(",");
                                for (int i = 0; i < classNames.length; i++) {
                                    argRestrictions.addRestriction(i, classNames[i]);
                                }
                            }
                        }
                    }
                    methodRestriction = new MethodRestriction(methodName, argRestrictions);
                } else {
                    methodName = name.substring(hashIdx + 1);
                    methodRestriction = new MethodRestriction(methodName);
                }
            } else {
                if ("true".equalsIgnoreCase(name)) {
                    className = ServiceLoader.class.getName();
                    methodName = "load";
                    ArgRestrictions argRestrictions = new ArgRestrictions();
                    argRestrictions.addRestriction(1, Class.class.getName());
                    methodRestriction = new MethodRestriction(methodName, argRestrictions);
                } else {
                    throw new IllegalArgumentException("Must at least specify class name and method name: " + name);
                }
            }  
            ConsumerRestriction restriction = new ConsumerRestriction(className, methodRestriction);
            restrictions.add(restriction);
                
            String bsn = element.getAttribute("bundle");
            if (bsn != null) {
                allowedBundles.add(new BundleDescriptor(bsn));
            }
            
            // TODO bundle version
            
            String bid = element.getAttribute("bundleId");
            if (bid != null) {
                bid = bid.trim();
                for (Bundle b : consumerBundle.getBundleContext().getBundles()) {
                    if (("" + b.getBundleId()).equals(bid)) {                       
                        allowedBundles.add(new BundleDescriptor(b.getSymbolicName()));
                        break;                        
                    }
                }                
            }
            
            // TODO fix log message
            // Activator.activator.log(LogService.LOG_INFO, "Weaving " + className + "#" + methodName + " from bundle " + 
            //    consumerBundle.getSymbolicName() + " to " + (allowedBundles.size() == 0 ? " any provider" : allowedBundles));
                        
            Activator.activator.registerConsumerBundle(consumerBundle, restrictions, 
                    allowedBundles.size() == 0 ? null : allowedBundles);           
            String[] argClasses = restriction.getMethodRestriction(methodName).getArgClasses();

            // TODO support more than one definition            
            return new WeavingData(className, methodName, argClasses);
        }
        return null;
    }
}
