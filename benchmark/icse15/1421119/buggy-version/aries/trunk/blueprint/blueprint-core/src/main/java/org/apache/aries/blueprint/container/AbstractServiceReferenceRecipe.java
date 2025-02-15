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
package org.apache.aries.blueprint.container;

import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.DomainCombiner;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.aries.blueprint.BlueprintConstants;
import org.apache.aries.blueprint.ExtendedServiceReferenceMetadata;
import org.apache.aries.blueprint.di.AbstractRecipe;
import org.apache.aries.blueprint.di.CollectionRecipe;
import org.apache.aries.blueprint.di.Recipe;
import org.apache.aries.blueprint.services.ExtendedBlueprintContainer;
import org.apache.aries.blueprint.utils.ReflectionUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.blueprint.container.ComponentDefinitionException;
import org.osgi.service.blueprint.container.ReifiedType;
import org.osgi.service.blueprint.reflect.ReferenceListener;
import org.osgi.service.blueprint.reflect.ReferenceMetadata;
import org.osgi.service.blueprint.reflect.ServiceReferenceMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for service reference recipes.
 *
 * TODO: if we have a single interface (which is the standard behavior), then we should be able to get rid of
 *       the proxyClassloader and just use this interface classloader to define the proxy
 *
 * TODO: it is allowed to have no interface defined at all, which should result in an empty proxy
 *
 * @version $Rev$, $Date$
 */
public abstract class AbstractServiceReferenceRecipe extends AbstractRecipe implements ServiceListener, SatisfiableRecipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractServiceReferenceRecipe.class);

    protected final ExtendedBlueprintContainer blueprintContainer;
    protected final ServiceReferenceMetadata metadata;
    protected final CollectionRecipe listenersRecipe;
    protected final List<Recipe> explicitDependencies;
    protected final boolean optional;
    /** The OSGi filter for tracking references */
    protected final String filter;
    /** The list of listeners for this reference.  This list will be lazy created */
    protected List<Listener> listeners;

    protected final Object monitor = new Object();
    private final List<ServiceReference> references = new ArrayList<ServiceReference>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean satisfied = new AtomicBoolean();
    private SatisfactionListener satisfactionListener;

    private final AccessControlContext accessControlContext;

    protected AbstractServiceReferenceRecipe(String name,
                                             ExtendedBlueprintContainer blueprintContainer,
                                             ServiceReferenceMetadata metadata,
                                             CollectionRecipe listenersRecipe,
                                             List<Recipe> explicitDependencies) {
        super(name);
        this.prototype = false;
        this.blueprintContainer = blueprintContainer;
        this.metadata = metadata;
        this.listenersRecipe = listenersRecipe;
        this.explicitDependencies = explicitDependencies;
        
        
        this.optional = (metadata.getAvailability() == ReferenceMetadata.AVAILABILITY_OPTIONAL);
        this.filter = createOsgiFilter(metadata);
        
        if (System.getSecurityManager() != null) {
            accessControlContext = createAccessControlContext();
        } else
        {
        	accessControlContext = null;
        }
    }



    public CollectionRecipe getListenersRecipe() {
        return listenersRecipe;
    }

    static boolean waited = false;
    public void start(SatisfactionListener listener) {
        if (listener == null) throw new NullPointerException("satisfactionListener is null");
        if (started.compareAndSet(false, true)) {
            try {
                satisfactionListener = listener;
                satisfied.set(optional);
                // Synchronized block on references so that service events won't interfere with initial references tracking
                // though this may not be sufficient because we don't control ordering of those events
                synchronized (monitor) {
                    getBundleContextForServiceLookup().addServiceListener(this, getOsgiFilter());
                    ServiceReference[] references = getBundleContextForServiceLookup().getServiceReferences(null, getOsgiFilter());
                    if (references != null) {
                        for (ServiceReference reference : references) {
                            this.references.add(reference);
                            track(reference);                           
                        }
                        satisfied.set(optional || !this.references.isEmpty());
                    }
                    LOGGER.debug("Found initial references {} for OSGi service {}", references, getOsgiFilter());
                }
            } catch (InvalidSyntaxException e) {
                throw new ComponentDefinitionException(e);
            }
        }
    }

    public void stop() {
        if (started.compareAndSet(true, false)) {
            synchronized (monitor) {
                getBundleContextForServiceLookup().removeServiceListener(this);
                doStop();
                for (Iterator<ServiceReference> it = references.iterator(); it.hasNext();) {
                    ServiceReference ref = it.next();
                    it.remove();
                    untrack(ref);
                }
                satisfied.set(false);
            }
            satisfactionListener = null;
        }
    }

    protected void doStop() {
    }

    protected boolean isStarted() {
        return started.get();
    }

    public boolean isSatisfied() {
        return satisfied.get();
    }

    @Override
    public List<Recipe> getConstructorDependencies() {
        List<Recipe> recipes = new ArrayList<Recipe>();
        if (explicitDependencies != null) {
            recipes.addAll(explicitDependencies);
        }
        return recipes;
    }
    
    public List<Recipe> getDependencies() {
        List<Recipe> recipes = new ArrayList<Recipe>();
        if (listenersRecipe != null) {
            recipes.add(listenersRecipe);
        }
        recipes.addAll(getConstructorDependencies());
        return recipes;
    }

    public String getOsgiFilter() {
        return filter;
    }

	protected Object getServiceSecurely(final ServiceReference serviceReference) {
		if (accessControlContext == null) {
			return getBundleContextForServiceLookup().getService(
					serviceReference);

		} else {
			// If we're operating with security, use the privileges of the bundle
			// we're managing to do the lookup
			return AccessController.doPrivileged(
					new PrivilegedAction<Object>() {
						public Object run() {
							return getBundleContextForServiceLookup()
									.getService(serviceReference);
						}
					}, accessControlContext);
		}
	}
    

	/**
	 * We may need to execute code within a doPrivileged block, and if so, it should be the 
	 * privileges of the bundle with the blueprint file that get used, not the privileges 
	 * of blueprint-core. To achieve this we use an access context. 
	 * @return
	 */
    private AccessControlContext createAccessControlContext() {
        return new AccessControlContext(AccessController.getContext(),
                new DomainCombiner() {               
                    public ProtectionDomain[] combine(ProtectionDomain[] arg0,
                                                      ProtectionDomain[] arg1) {                    
                        return new ProtectionDomain[] { new ProtectionDomain(null, null) {                        
                            public boolean implies(Permission permission) {                                                           
                                return getBundleContextForServiceLookup().getBundle().hasPermission(permission);
                            }
                        } 
                    };
                }
        });
    }

    protected void createListeners() {
            if (listenersRecipe != null) {
                List<Listener> listeners = (List<Listener>) listenersRecipe.create();
                for (Listener listener : listeners) {
                    List<Class> classList = new ArrayList<Class>();
                    Class clz = getInterfaceClass();
                    if (clz != null) { 
                        classList.add(clz);
                    } else {
                        classList.add(Object.class);
                    }
                    listener.init(classList);
                }
                this.listeners = listeners;
            } else {
                this.listeners = Collections.emptyList();
            }
    }

    protected List<Class<?>> loadAllClasses(Iterable<String> interfaceNames) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        for (String name : interfaceNames) {
            Class<?> clazz = loadClass(name);
            classes.add(clazz);
        }
        return classes;
    }

    protected ReifiedType loadType(String typeName, ClassLoader fromClassLoader) {
        if (typeName == null) {
            return null;
        }
        try {
            // this method is overriden to use the blueprint container directly
            // because proxies can be created outside of the recipe creation which
            // would lead to an exception because the context is not set
            // TODO: consider having the context as a property on the recipe rather than a thread local
            return GenericType.parse(typeName, fromClassLoader != null ? fromClassLoader : blueprintContainer);
        } catch (ClassNotFoundException e) {
            throw new ComponentDefinitionException("Unable to load class " + typeName + " from recipe " + this, e);
        }
    }


    protected Object createProxy(final Callable<Object> dispatcher, Set<Class<?>> interfaces) throws Exception {
        if (!interfaces.iterator().hasNext()) {
            return new Object();
        } else {
            // Check class proxying
            boolean proxyClass = false;
            if (metadata instanceof ExtendedServiceReferenceMetadata) {
                proxyClass = (((ExtendedServiceReferenceMetadata) metadata).getProxyMethod() & ExtendedServiceReferenceMetadata.PROXY_METHOD_CLASSES) != 0;
            }
            if (!proxyClass) {
                for (Class cl : interfaces) {
                    if (!cl.isInterface()) {
                        throw new ComponentDefinitionException("A class " + cl.getName() + " was found in the interfaces list, but class proxying is not allowed by default. The ext:proxy-method='classes' attribute needs to be added to this service reference.");
                    }
                }
            }
            //We don't use the #getBundleContextForServiceLookup() method here, the bundle requesting the proxy is the
            //blueprint client, not the context of the lookup
            return blueprintContainer.getProxyManager().createDelegatingProxy(blueprintContainer.getBundleContext().getBundle(), interfaces, dispatcher, null);
        }
    }

    public void serviceChanged(final ServiceEvent event) {
        final int eventType = event.getType();
        final ServiceReference ref = event.getServiceReference();
        try {
            blueprintContainer.getExecutors().submit(new Runnable() {
                public void run() {
                    switch (eventType) {
                        case ServiceEvent.REGISTERED:
                            serviceAdded(ref);
                            break;
                        case ServiceEvent.MODIFIED:
                            serviceModified(ref);
                            break;
                        case ServiceEvent.UNREGISTERING:
                            serviceRemoved(ref);
                            break;
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // The job has been rejected because the executor is shut down
            // so ignore the exception
        }
    }

    private void serviceAdded(ServiceReference ref) {
        LOGGER.debug("Tracking reference {} for OSGi service {}", ref, getOsgiFilter());
        if (isStarted()) {
            synchronized (monitor) {
                if (references.contains(ref)) {
                    return;
                }
                references.add(ref);
            }
            track(ref);
            setSatisfied(true);
        }
    }

    private void serviceModified(ServiceReference ref) {
        // ref must be in references and must be satisfied
        if (isStarted()) {
            synchronized (monitor) {
                if (references.contains(ref)) {
                    track(ref);
                }
            }
        }
    }

    private void serviceRemoved(ServiceReference ref) {
        if (isStarted()) {
            LOGGER.debug("Untracking reference {} for OSGi service {}", ref, getOsgiFilter());
            boolean removed;
            boolean satisfied;
            synchronized (monitor) {
                removed = references.remove(ref);
                satisfied = optional || !references.isEmpty();
            }
            if (removed) {
                untrack(ref);
            }
            setSatisfied(satisfied);
        }
    }
    
    protected Class getInterfaceClass() {
        Class clz = getRuntimeClass(metadata);
        if (clz != null)
            return clz;
        else if (metadata.getInterface() != null)
            return loadClass(metadata.getInterface());
        return null;
    }
    
    protected static Class getRuntimeClass(ServiceReferenceMetadata metadata) {
        if (metadata instanceof ExtendedServiceReferenceMetadata && ((ExtendedServiceReferenceMetadata) metadata).getRuntimeInterface() != null) {
           return ((ExtendedServiceReferenceMetadata) metadata).getRuntimeInterface();
        } 
        return null;
    }

    protected BundleContext getBundleContextForServiceLookup() {
        if (metadata instanceof ExtendedServiceReferenceMetadata && ((ExtendedServiceReferenceMetadata) metadata).getRuntimeInterface() != null) {
            BundleContext context = ((ExtendedServiceReferenceMetadata) metadata).getBundleContext();
            if(context != null) {
              return context;
            }
        }
         
        return blueprintContainer.getBundleContext();
    }
    
    protected void setSatisfied(boolean s) {
        // This check will ensure an atomic comparision and set
        // so that it will only be true if the value actually changed
        if (satisfied.getAndSet(s) != s) {
            LOGGER.debug("Service reference with filter {} satisfied {}", getOsgiFilter(), this.satisfied);
            this.satisfactionListener.notifySatisfaction(this);
        }
    }

    protected abstract void track(ServiceReference reference);

    protected abstract void untrack(ServiceReference reference);

    protected abstract void retrack();

    protected void updateListeners() {  
        if (references.isEmpty()) {
            unbind(null, null);
        } else {
            retrack();
        }
    }
    
    protected void bind(ServiceReference reference, Object service) {
        if (listeners != null) {    
            for (Listener listener : listeners) {
                if (listener != null) {
                    listener.bind(reference, service);
                }
            } 
        }
    }
    
    protected void unbind(ServiceReference reference, Object service) {
        if (listeners != null) {    
            for (Listener listener : listeners) {
                if (listener != null) {
                    listener.unbind(reference, service);
                }
            } 
        }
    }
    
    public List<ServiceReference> getServiceReferences() {
        synchronized (monitor) {
            return new ArrayList<ServiceReference>(references);
        }
    }

    public ServiceReference getBestServiceReference() {
        synchronized (monitor) {
            int length = references.size();
            if (length == 0) { /* if no service is being tracked */
                return null;
            }
            int index = 0;
            if (length > 1) { /* if more than one service, select highest ranking */
                int maxRanking = Integer.MIN_VALUE;
                long minId = Long.MAX_VALUE;
                for (int i = 0; i < length; i++) {
                    Object property = references.get(i).getProperty(Constants.SERVICE_RANKING);
                    int ranking = (property instanceof Integer) ? (Integer) property : 0;
                    long id = (Long) references.get(i).getProperty(Constants.SERVICE_ID);
                    if ((ranking > maxRanking) || (ranking == maxRanking && id < minId)) {
                        index = i;
                        maxRanking = ranking;
                        minId = id;
                    }
                }
            }
            return references.get(index);
        }
    }

    public static class Listener {

        private static final Logger LOGGER = LoggerFactory.getLogger(Listener.class);

        private Object listener;
        private ReferenceListener metadata;
        private ExtendedBlueprintContainer blueprintContainer;

        private Set<Method> bindMethodsReference = new HashSet<Method>();
        private Set<Method> bindMethodsObjectProp = new HashSet<Method>();
        private Set<Method> bindMethodsObject = new HashSet<Method>();
        private Set<Method> unbindMethodsReference = new HashSet<Method>();
        private Set<Method> unbindMethodsObject = new HashSet<Method>();
        private Set<Method> unbindMethodsObjectProp = new HashSet<Method>();

        public void setListener(Object listener) {
            this.listener = listener;
        }

        public void setMetadata(ReferenceListener metadata) {
            this.metadata = metadata;
        }

        public void setBlueprintContainer(ExtendedBlueprintContainer blueprintContainer) {
            this.blueprintContainer = blueprintContainer;
        }
        
        public void init(Collection<Class> classes) {
            Set<Class> clazzes = new HashSet<Class>(classes);
            clazzes.add(Object.class);
            Class listenerClass = listener.getClass();
            String bindName = metadata.getBindMethod();
            if (bindName != null) {
                bindMethodsReference.addAll(ReflectionUtils.findCompatibleMethods(listenerClass, bindName, new Class[] { ServiceReference.class }));
                for (Class clazz : clazzes) {
                    bindMethodsObject.addAll(ReflectionUtils.findCompatibleMethods(listenerClass, bindName, new Class[] { clazz }));
                    bindMethodsObjectProp.addAll(ReflectionUtils.findCompatibleMethods(listenerClass, bindName, new Class[] { clazz, Map.class }));
                }
                if (bindMethodsReference.size() + bindMethodsObject.size() + bindMethodsObjectProp.size() == 0) {
                    throw new ComponentDefinitionException("No matching methods found for listener bind method: " + bindName);
                }
            }
            String unbindName = metadata.getUnbindMethod();
            if (unbindName != null) {
                unbindMethodsReference.addAll(ReflectionUtils.findCompatibleMethods(listenerClass, unbindName, new Class[] { ServiceReference.class }));
                for (Class clazz : clazzes) {
                    unbindMethodsObject.addAll(ReflectionUtils.findCompatibleMethods(listenerClass, unbindName, new Class[] { clazz }));
                    unbindMethodsObjectProp.addAll(ReflectionUtils.findCompatibleMethods(listenerClass, unbindName, new Class[] { clazz, Map.class }));
                }
                if (unbindMethodsReference.size() + unbindMethodsObject.size() + unbindMethodsObjectProp.size() == 0) {
                    throw new ComponentDefinitionException("No matching methods found for listener unbind method: " + unbindName);
                }
            }
        }

        public void bind(ServiceReference reference, Object service) {
            invokeMethods(bindMethodsReference, bindMethodsObject, bindMethodsObjectProp, reference, service);
        }

        public void unbind(ServiceReference reference, Object service) {
            invokeMethods(unbindMethodsReference, unbindMethodsObject, unbindMethodsObjectProp, reference, service);
        }

        private void invokeMethods(Set<Method> referenceMethods, Set<Method> objectMethods, Set<Method> objectPropMethods, ServiceReference reference, Object service) {
            for (Method method : referenceMethods) {
                try {
                    ReflectionUtils.invoke(blueprintContainer.getAccessControlContext(), 
                                           method, listener, reference);
                } catch (Exception e) {
                    LOGGER.error("Error calling listener method " + method, e);
                }
            }
            for (Method method : objectMethods) {
                try {
                    ReflectionUtils.invoke(blueprintContainer.getAccessControlContext(), 
                                           method, listener, service);
                } catch (Exception e) {
                    LOGGER.error("Error calling listener method " + method, e);
                }
            }
            Map<String, Object> props = null;
            for (Method method : objectPropMethods) {
                if (props == null) {
                    props = new HashMap<String, Object>();
                    if (reference != null) {
                        for (String name : reference.getPropertyKeys()) {
                            props.put(name, reference.getProperty(name));
                        }
                    }
                }
                try {
                    ReflectionUtils.invoke(blueprintContainer.getAccessControlContext(), 
                                           method, listener, service, props);
                } catch (Exception e) {
                    LOGGER.error("Error calling listener method " + method, e);
                }
            }
        }
    }

    /**
     * Create the OSGi filter corresponding to the ServiceReferenceMetadata constraints
     *
     * @param metadata the service reference metadata
     * @return the OSGi filter
     */
    private static String createOsgiFilter(ServiceReferenceMetadata metadata) {
        List<String> members = new ArrayList<String>();
        // Handle filter
        String flt = metadata.getFilter();
        if (flt != null && flt.length() > 0) {
            if (!flt.startsWith("(")) {
                flt = "(" + flt + ")";
            }
            members.add(flt);
        }
        // Handle interfaces
        String interfaceName = metadata.getInterface();
        Class runtimeClass = getRuntimeClass(metadata);
        if (runtimeClass != null) {
            interfaceName = runtimeClass.getName();
        }
        if (interfaceName != null && interfaceName.length() > 0) {
            members.add("(" + Constants.OBJECTCLASS + "=" + interfaceName + ")");
        }
        // Handle component name
        String componentName = metadata.getComponentName();
        if (componentName != null && componentName.length() > 0) {
            members.add("(" + BlueprintConstants.COMPONENT_NAME_PROPERTY + "=" + componentName + ")");
        }
        // Create filter
        if (members.isEmpty()) {
            throw new IllegalStateException("No constraints were specified on the service reference");
        }
        if (members.size() == 1) {
            return members.get(0);
        }
        StringBuilder sb = new StringBuilder("(&");
        for (String member : members) {
            sb.append(member);
        }
        sb.append(")");
        return sb.toString();
    }

    private static Class[] getInterfaces(Class[] classes) {
        Set<Class> interfaces = new HashSet<Class>();
        for (Class clazz : classes) {
            if (clazz.isInterface()) {
                interfaces.add(clazz);
            }
        }
        return toClassArray(interfaces);
    }

    private static Class[] toClassArray(Set<Class> classes) {
        return classes.toArray(new Class [classes.size()]);
    }

}
