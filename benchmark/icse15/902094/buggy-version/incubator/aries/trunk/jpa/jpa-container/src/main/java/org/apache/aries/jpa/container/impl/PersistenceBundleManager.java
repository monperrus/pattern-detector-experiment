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

package org.apache.aries.jpa.container.impl;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.aries.application.VersionRange;
import org.apache.aries.application.utils.manifest.ManifestHeaderProcessor;
import org.apache.aries.jpa.container.ManagedPersistenceUnitInfo;
import org.apache.aries.jpa.container.ManagedPersistenceUnitInfoFactory;
import org.apache.aries.jpa.container.parsing.ParsedPersistenceUnit;
import org.apache.aries.jpa.container.parsing.PersistenceDescriptor;
import org.apache.aries.jpa.container.parsing.PersistenceDescriptorParser;
import org.apache.aries.jpa.container.parsing.PersistenceDescriptorParserException;
import org.apache.aries.jpa.container.unit.impl.ManagedPersistenceUnitInfoFactoryImpl;
import org.apache.aries.util.tracker.MultiBundleTracker;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class locates, parses and manages persistence units defined in OSGi bundles.
 */
public class PersistenceBundleManager extends MultiBundleTracker
{
  /** Logger */
  private static final Logger _logger = LoggerFactory.getLogger("org.apache.aries.jpa.container");
  
  /** The bundle context for this bundle */
  private BundleContext ctx = null;
  /** 
   * A map of providers to persistence bundles this is used to guarantee that 
   * when a provider service is removed we can access all of the bundles that
   * might possibly be using it. The map should only ever be accessed when
   * synchronized on {@code this}.
   */
  private final Map<Bundle, EntityManagerFactoryManager> bundleToManagerMap = new HashMap<Bundle, EntityManagerFactoryManager>();
  /** 
   * The PersistenceProviders. The Set should only ever be accessed when
   * synchronized on {@code this}. Use a Set for constant access and add times.
   */
  private Set<ServiceReference> persistenceProviders = new HashSet<ServiceReference>();
  /** Plug-point for persistence unit providers */
  private ManagedPersistenceUnitInfoFactory persistenceUnitFactory; 
  /** Configuration for this extender */
  private Properties config;

  /**
   * Create the extender. Note that it will not start tracking 
   * until the {@code open()} method is called
   * @param ctx The extender bundle's context
   */
  public PersistenceBundleManager(BundleContext ctx) 
  {
	  super(ctx, Bundle.INSTALLED | Bundle.RESOLVED | Bundle.STARTING |
			  Bundle.ACTIVE | Bundle.STOPPING);
    this.ctx = ctx;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public void open() {
    //Create the pluggable ManagedPersistenceUnitInfoFactory
    String className = config.getProperty(ManagedPersistenceUnitInfoFactory.DEFAULT_PU_INFO_FACTORY_KEY);
    
    if(className != null) {
      try {
        Class<? extends ManagedPersistenceUnitInfoFactory> clazz = ctx.getBundle().loadClass(className);
        persistenceUnitFactory = clazz.newInstance();
      } catch (Exception e) {
        _logger.error("There was a problem creating the custom ManagedPersistenceUnitInfoFactory " + className 
            + ". The default ManagedPersistenceUnitInfo factory will be used instead", e);
      }
    }
    
    if(persistenceUnitFactory == null)
      persistenceUnitFactory = new ManagedPersistenceUnitInfoFactoryImpl();
    
    super.open();
  }
  
  public Object addingBundle(Bundle bundle, BundleEvent event) 
  {
    EntityManagerFactoryManager mgr = null;
    mgr = setupManager(bundle, mgr);
    return mgr;
  }

  /**
   * A provider is being added, add it to our Set
   * @param ref
   */
  public synchronized void addingProvider(ServiceReference ref)
  {
    persistenceProviders.add(ref);
  }
  
  /**
   * A provider is being removed, remove it from the set, and notify all
   * managers that it has been removed
   * @param ref
   */
  public void removingProvider(ServiceReference ref)
  {
    //We may get a null reference if the ref-list is empty to start with
    if(ref == null)
      return;
    Map<Bundle, EntityManagerFactoryManager> mgrs;
    synchronized (this) {
      persistenceProviders.remove(ref);
      mgrs = new HashMap<Bundle, EntityManagerFactoryManager>(bundleToManagerMap);
    }
    //If the entry is removed then make sure we notify the persistenceUnitFactory
    for(Entry<Bundle, EntityManagerFactoryManager> entry : mgrs.entrySet()) {
      if(entry.getValue().providerRemoved(ref))
        persistenceUnitFactory.destroyPersistenceBundle(entry.getKey());
    }
  }
  
  /**
   * Add config properties, making sure to read in the properties file
   * and override the supplied properties
   * @param props
   */
  public void setConfig(Properties props) {
    config = new Properties(props);
    URL u = ctx.getBundle().getResource(ManagedPersistenceUnitInfoFactory.ARIES_JPA_CONTAINER_PROPERTIES);
    
    if(u != null) {
      if(_logger.isInfoEnabled())
        _logger.info("A {} file was found. The default properties {} will be overridden.",
            new Object[] {ManagedPersistenceUnitInfoFactory.ARIES_JPA_CONTAINER_PROPERTIES, config});
      try {
        config.load(u.openStream());
      } catch (IOException e) {
        _logger.error("There was an error reading from " 
            + ManagedPersistenceUnitInfoFactory.ARIES_JPA_CONTAINER_PROPERTIES, e);
      }
    } else {
      if(_logger.isInfoEnabled())
        _logger.info("No {} file was found. The default properties {} will be used.",
            new Object[] {ManagedPersistenceUnitInfoFactory.ARIES_JPA_CONTAINER_PROPERTIES, config});
    }
  }

  public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {

    EntityManagerFactoryManager mgr = (EntityManagerFactoryManager) object;
    //If the bundle was updated we need to destroy it and re-initialize
    //the EntityManagerFactoryManager
    if(event != null && event.getType() == BundleEvent.UPDATED) {
      mgr.destroy();
      persistenceUnitFactory.destroyPersistenceBundle(bundle);
      setupManager(bundle, mgr);
    } else {
      try {
        mgr.bundleStateChange();
      } catch (InvalidPersistenceUnitException e) {
        logInvalidPersistenceUnitException(bundle, e);
        mgr.destroy();
      }
    }
  }

  public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
    EntityManagerFactoryManager mgr = (EntityManagerFactoryManager) object;   
    mgr.destroy();
    persistenceUnitFactory.destroyPersistenceBundle(bundle);
    //Remember to tidy up the map
    synchronized (this) {
      bundleToManagerMap.remove(bundle);
    }
  }
  
  /**
   * Set up an {@link EntityManagerFactoryManager} for the supplied bundle
   * 
   * @param bundle The bundle
   * @param mgr The previously existing {@link EntityManagerFactoryManager} or {@code null} if none existed
   * @return The manager to use, or null if no persistence units can be managed for this bundle
   */
  private EntityManagerFactoryManager setupManager(Bundle bundle,
      EntityManagerFactoryManager mgr) {
    //Find Persistence descriptors
    Collection <PersistenceDescriptor> persistenceXmls = PersistenceBundleHelper.findPersistenceXmlFiles(bundle);

      //If we have no persistence units then our job is done
      if (!!!persistenceXmls.isEmpty()) {
        
        if(bundle.getState() == Bundle.ACTIVE) {
          _logger.warn("The bundle {} is already active, it may not be possible to create managed persistence units for it.", 
              new Object[] {bundle.getSymbolicName() + "_" + bundle.getVersion()});
        }
        
        Collection<ParsedPersistenceUnit> pUnits = new ArrayList<ParsedPersistenceUnit>();
        
        //Parse each descriptor
        for(PersistenceDescriptor descriptor : persistenceXmls) {
          try {
            pUnits.addAll(PersistenceDescriptorParser.parse(bundle, descriptor));
          } catch (PersistenceDescriptorParserException e) {
            _logger.error("There was an error while parsing the persistence descriptor " 
                + descriptor.getLocation() + " in bundle " + bundle.getSymbolicName() 
                + "_" + bundle.getVersion() + ". No persistence units will be managed for this bundle", e);
          }
        }
        
        //If we have any persistence units then find a provider to use
        if(!!!pUnits.isEmpty()) {
          ServiceReference ref = getProviderServiceReference(pUnits);
          //If we found a provider then create the ManagedPersistenceUnitInfo objects
          if(ref != null) {  
            Collection<ManagedPersistenceUnitInfo> infos = persistenceUnitFactory.
                createManagedPersistenceUnitMetadata(ctx, bundle, ref, pUnits);
            //Either update the existing manager or create a new one
            if(mgr != null)
              mgr.manage(ref, infos);
            else {
              synchronized (this) {
                if(persistenceProviders.contains(ref)) {
                    mgr = new EntityManagerFactoryManager(ctx, bundle, ref, infos);
                    bundleToManagerMap.put(bundle, mgr);
                }
              }
            }
          }
          //If we have a manager then prod it to get it into the right state
          if(mgr != null) {
            try {
              mgr.bundleStateChange();
            } catch (InvalidPersistenceUnitException e) {
              logInvalidPersistenceUnitException(bundle, e);
              mgr.destroy();
              persistenceUnitFactory.destroyPersistenceBundle(bundle);
            }
          }
        }
      }
      return mgr;
    }
  
  /**
   * Get a persistence provider from the service registry described by the
   * persistence units defined
   * @param parsedPersistenceUnits
   * @return A service reference or null if no suitable reference is available
   */
  private ServiceReference getProviderServiceReference(Collection<ParsedPersistenceUnit> parsedPersistenceUnits)
  {
    Set<String> ppClassNames = new HashSet<String>();
    List<VersionRange> versionRanges = new ArrayList<VersionRange>();
    //Fill the set of class names and version Filters
    for(ParsedPersistenceUnit unit : parsedPersistenceUnits)
    {
      Map<String, Object> metadata = unit.getPersistenceXmlMetadata();
      String provider = (String) metadata.get(ParsedPersistenceUnit.PROVIDER_CLASSNAME);
      //get providers specified in the persistence units
      if(provider != null && !!!provider.equals(""))
      {
        ppClassNames.add(provider);
        
        Properties props = (Properties) metadata.get(ParsedPersistenceUnit.PROPERTIES);
        
        if(props != null && props.containsKey(ParsedPersistenceUnit.JPA_PROVIDER_VERSION)) {
         
          String versionRangeString = props.getProperty(ParsedPersistenceUnit.JPA_PROVIDER_VERSION, "0.0.0");
          try {
            versionRanges.add(ManifestHeaderProcessor.parseVersionRange(versionRangeString));
          } catch (IllegalArgumentException e) {
            _logger.warn("There was an error parsing the version range string {} for persistence unit {}. It will be ignored."
                , new Object[] {versionRangeString, metadata.get(ParsedPersistenceUnit.UNIT_NAME)});
          }
        }
      }
    }
    //If we have too many provider class names or incompatible version ranges specified then blow up
    
    VersionRange range = null;
    if(!!!versionRanges.isEmpty()) {
      try {
        range = combineVersionRanges(versionRanges);
      } catch (InvalidRangeCombination e) {
        Bundle bundle = parsedPersistenceUnits.iterator().next().getDefiningBundle();
        _logger.error("The bundle " + bundle.getSymbolicName() 
            + "_" + bundle.getVersion() + " specified an invalid combination of provider version ranges",  e);
        return null;
      }
    }
    
    if(ppClassNames.size() > 1)
    {
      Bundle bundle = parsedPersistenceUnits.iterator().next().getDefiningBundle();
      _logger.error("The bundle " + bundle.getSymbolicName() 
          + "_" + bundle.getVersion() + " specified more than one persistence provider: {}. "
          + "This is not supported, so no persistence units will be created for this bundle.",
          new Object[] {ppClassNames});
      return null;
    } else {
      //Get the best provider for the given filters
      String provider = (ppClassNames.isEmpty()) ?
          persistenceUnitFactory.getDefaultProviderClassName() : ppClassNames.iterator().next();
          return getBestProvider(provider, range);
    }
  }
 
  /**
   * Turn a Collection of version ranges into a single range including common overlap
   * @param versionRanges
   * @return
   * @throws InvalidRangeCombination
   */
  private VersionRange combineVersionRanges(List<VersionRange> versionRanges) throws InvalidRangeCombination {

    Version minVersion = new Version(0,0,0);
    Version maxVersion = null;
    boolean minExclusive = false;
    boolean maxExclusive = false;
    
    for(VersionRange range : versionRanges) {
      int minComparison = minVersion.compareTo(range.getMinimumVersion());
      //If minVersion is smaller then we have a new, larger, minimum
      if(minComparison < 0) {
        minVersion = range.getMinimumVersion();
        minExclusive = range.isMinimumExclusive();
      }
      //Only update if it is the same version but more restrictive
      else if(minComparison == 0 && range.isMaximumExclusive())
        minExclusive = true;
    
      if(range.isMaximumUnbounded())
        continue;
      else if (maxVersion == null) {
        maxVersion = range.getMaximumVersion();
        maxExclusive = range.isMaximumExclusive();
      } else {
        int maxComparison = maxVersion.compareTo(range.getMaximumVersion());
        
        //We have a new, lower maximum
        if(maxComparison > 0) {
          maxVersion = range.getMaximumVersion();
          maxExclusive = range.isMaximumExclusive();
          //If the maximum is the same then make sure we set the exclusivity properly
        } else if (maxComparison == 0 && range.isMaximumExclusive())
          maxExclusive = true;
      }
    }
    
    //Now check that we have valid values
    int check = (maxVersion == null) ? -1 : minVersion.compareTo(maxVersion);
    //If min is greater than max, or min is equal to max and one of the exclusive
    //flags is set then we have a problem!
    if(check > 0 || (check == 0 && (minExclusive || maxExclusive))) {
      throw new InvalidRangeCombination(minVersion, minExclusive, maxVersion, maxExclusive);
    }
    
    //Turn the Versions into a version range string
    StringBuilder rangeString = new StringBuilder();
    rangeString.append(minVersion);
    
    if(maxVersion != null) {
      rangeString.insert(0, minExclusive ? "(" : "[");
      rangeString.append(",");
      rangeString.append(maxVersion);
      rangeString.append(maxExclusive ? ")" : "]");
    }
    //Turn that string back into a VersionRange
    return ManifestHeaderProcessor.parseVersionRange(rangeString.toString());
  }

  /**
   * Locate the best provider for the given criteria
   * @param providerClass
   * @param matchingCriteria
   * @return
   */
  @SuppressWarnings("unchecked")
  private synchronized ServiceReference getBestProvider(String providerClass, VersionRange matchingCriteria)
  {
    if(!!!persistenceProviders.isEmpty()) {
      if((providerClass != null && !!!"".equals(providerClass))
          || matchingCriteria != null) {
        List<ServiceReference> refs = new ArrayList<ServiceReference>();
        for(ServiceReference reference : persistenceProviders) {
          
          if(providerClass != null && !!!providerClass.equals(
              reference.getProperty("javax.persistence.provider")))
            continue;
            
          if(matchingCriteria == null || matchingCriteria.
              matches(reference.getBundle().getVersion()))
            refs.add(reference);
        }
        
        if(!!!refs.isEmpty()) {
          //Return the "best" provider, i.e. the highest version
          return Collections.max(refs, new ProviderServiceComparator());
        } else {
          _logger.warn("There are no suitable providers for the provider class name {} and version range {}.",
              new Object[] {providerClass, matchingCriteria});
        }
      } else {
        //Return the "best" provider, i.e. the service OSGi would pick
        return (ServiceReference) Collections.max(persistenceProviders);
      }
    } else {
      _logger.warn("There are no providers available.");
    }
    return null;
  }
  
  /**
   * Sort the providers so that the highest version, highest ranked service is at the top
   */
  private static class ProviderServiceComparator implements Comparator<ServiceReference> {
    public int compare(ServiceReference object1, ServiceReference object2)
    {
      Version v1 = object1.getBundle().getVersion();
      Version v2 = object2.getBundle().getVersion();
      int res = v1.compareTo(v2);
      if (res == 0) {
        Integer rank1 = (Integer) object1.getProperty(Constants.SERVICE_RANKING);
        Integer rank2 = (Integer) object2.getProperty(Constants.SERVICE_RANKING);
        if (rank1 != null && rank2 != null)
          res = rank1.compareTo(rank2);
      }
      return res;
    }
  }
  
  /**
   * Log a warning to indicate that the Persistence units state will be destroyed
   * @param bundle
   * @param e
   */
  private void logInvalidPersistenceUnitException(Bundle bundle,
      InvalidPersistenceUnitException e) {
    _logger.warn("The persistence units for bundle " + bundle.getSymbolicName() + "_" + bundle.getVersion()
        + " became invalid and will be destroyed.", e);
  }
}
