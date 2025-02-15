package org.apache.aries.subsystem.ctt.itests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.osgi.framework.namespace.BundleNamespace.BUNDLE_NAMESPACE;
import static org.osgi.framework.namespace.PackageNamespace.PACKAGE_NAMESPACE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.subsystem.itests.SubsystemTest;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.subsystem.Subsystem;

/*
 * A set of tests to cover OSGi Subsystems CTT section 4, "Subsystem Dependency Tests"
 * This is going to look a bit like ProvisionPolicyTest with a bit of 
 * DependencyLifecycle thrown in. 
 * 
 * 	- The following bundles are used for the tests
	- Bundle A that export package x
	- Bundle B that provides capability y
	- Bundle C that imports package x
	- Bundle D that requires bundle A
	- Bundle E that requires capability y
	- Bundle F that export package x
	- Bundle G that provides capability y
	- The following repositories are defined
	- Repository R1
	  - Bundle A
	  - Bundle B
	  - Bundle C
	  - Bundle D
	  - Bundle E
	  - Bundle F
	  - Bundle G
	- Repository R2
	  - Bundle A
	  - Bundle B
	  - Bundle C
	  - Bundle D
	  - Bundle E
 */

@RunWith(JUnit4TestRunner.class)
public abstract class SubsystemDependencyTestBase extends SubsystemTest 
{
	protected static String BUNDLE_A = "sdt_bundle.a.jar";
	protected static String BUNDLE_B = "sdt_bundle.b.jar";
	protected static String BUNDLE_C = "sdt_bundle.c.jar";
	protected static String BUNDLE_D = "sdt_bundle.d.jar";
	protected static String BUNDLE_E = "sdt_bundle.e.jar";
	protected static String BUNDLE_F = "sdt_bundle.f.jar";
	protected static String BUNDLE_G = "sdt_bundle.g.jar";

	private static boolean _staticResourcesCreated = false;
	@Before
	public void setUp() throws Exception
	{
		super.setUp();
		
		// We'd like to do this in an @BeforeClass method, but files written in @BeforeClass
		// go into the project's target/ directory whereas those written in @Before go into 
		// paxexam's temp directory, which is where they're needed. 
		if (!_staticResourcesCreated) { 
			createBundleA();
			createBundleB();
			createBundleC();
			createBundleD();
			createBundleE();
			createBundleF();
			createBundleG();
			_staticResourcesCreated = true;
		}
	}
	
	private static void createBundleA() throws Exception
	{ 
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(Constants.BUNDLE_VERSION, "1.0.0");
		headers.put(Constants.EXPORT_PACKAGE, "x");
		createBundle(BUNDLE_A, headers);
	}
	
	private static void createBundleB() throws Exception
	{
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(Constants.BUNDLE_VERSION, "1.0.0");
		headers.put(Constants.PROVIDE_CAPABILITY, "y;y=randomNamespace"); // TODO: see comment below about bug=true
		createBundle(BUNDLE_B, headers);
	}
	
	private static void createBundleC() throws Exception
	{
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(Constants.BUNDLE_VERSION, "1.0.0");
		headers.put(Constants.IMPORT_PACKAGE, "x");
		createBundle(BUNDLE_C, headers);
	}
	
	private static void createBundleD() throws Exception
	{
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(Constants.BUNDLE_VERSION, "1.0.0");
		headers.put(Constants.REQUIRE_BUNDLE, BUNDLE_A);
		createBundle(BUNDLE_D, headers);
	}
	
	private static void createBundleE() throws Exception 
	{
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(Constants.BUNDLE_VERSION, "1.0.0");
		headers.put(Constants.REQUIRE_CAPABILITY, "y");
		// TODO:
		/*
		 * According to the OSGi Core Release 5 spec section 3.3.6 page 35, 
		 *   "A filter is optional, if no filter directive is specified the requirement always matches."
		 *  
		 * If omitted, we first get an NPE in DependencyCalculator.MissingCapability.initializeAttributes(). 
		 * If that's fixed, we get exceptions of the form, 
		 * 
		 *  Caused by: java.lang.IllegalArgumentException: The filter must not be null.
		 *    at org.eclipse.equinox.internal.region.StandardRegionFilterBuilder.allow(StandardRegionFilterBuilder.java:49)
		 *    at org.apache.aries.subsystem.core.internal.SubsystemResource.setImportIsolationPolicy(SubsystemResource.java:655)
	     * 
	     * This looks to be an Equinox defect - at least in the level of 3.8.0 currently being used by these tests. 
		 */
		createBundle(BUNDLE_E, headers);
	}

	private static void createBundleF() throws Exception 
	{
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(Constants.BUNDLE_VERSION, "1.0.0");
		headers.put(Constants.EXPORT_PACKAGE, "x");
		createBundle(BUNDLE_F, headers);
	}
	
	private static void createBundleG() throws Exception 
	{
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(Constants.BUNDLE_VERSION, "1.0.0");
		headers.put(Constants.PROVIDE_CAPABILITY, "y;y=randomNamespace");      // TODO: see comment above about bug=true
		createBundle(BUNDLE_G, headers);
	}
	
	protected void registerRepositoryR1() throws Exception
	{ 
		registerRepositoryService(BUNDLE_A, BUNDLE_B, 
				BUNDLE_C, BUNDLE_D, BUNDLE_E, BUNDLE_F, BUNDLE_G);
	}
	
	protected void registerRepositoryR2() throws Exception
	{
		registerRepositoryService(BUNDLE_A, BUNDLE_B, 
				BUNDLE_C, BUNDLE_D, BUNDLE_E);
	}
	
	/**
	 *  - Verify that bundles C, D and E in subsystem s wire to A->x, A, B->y respectively
	 */
	protected void checkBundlesCDandEWiredToAandB (Subsystem s) 
	{
		verifySinglePackageWiring (s, BUNDLE_C, "x", BUNDLE_A);
		verifyRequireBundleWiring (s, BUNDLE_D, BUNDLE_A);
		verifyCapabilityWiring (s, BUNDLE_E, "y", BUNDLE_B);
	}

	/**
	 * Check that wiredBundleName in subsystem s is wired to a single package, 
	 * expectedPackage, from expectedProvidingBundle
	 * @param s
	 * @param wiredBundleName
	 * @param expectedPackage
	 * @param expectedProvidingBundle
	 */
	protected void verifySinglePackageWiring (Subsystem s, String wiredBundleName, String expectedPackage, String expectedProvidingBundle)
	{
		Bundle wiredBundle = getBundle(s, wiredBundleName);
		assertNotNull ("Bundle not found", wiredBundleName);

		BundleWiring wiring = wiredBundle.adapt(BundleWiring.class);
		List<BundleWire> wiredPackages = wiring.getRequiredWires(PACKAGE_NAMESPACE);
		assertEquals ("Only one package expected", 1, wiredPackages.size());
		
		String packageName = (String) 
			wiredPackages.get(0).getCapability().getAttributes().get(PACKAGE_NAMESPACE);
		assertEquals ("Wrong package found", expectedPackage, packageName);
		
		String providingBundle = wiredPackages.get(0).getProvider().getSymbolicName();
		assertEquals ("Package provided by wrong bundle", expectedProvidingBundle, providingBundle);
	}
	
	/**
	 * Verify that the Require-Bundle of wiredBundleName in subsystem s is met by a wire
	 * to expectedProvidingBundleName
	 * @param s
	 * @param wiredBundleName
	 * @param expectedProvidingBundleName
	 */
	protected void verifyRequireBundleWiring (Subsystem s, String wiredBundleName, String expectedProvidingBundleName)
	{
		Bundle wiredBundle = getBundle(s, BUNDLE_D);
		assertNotNull ("Target bundle " + wiredBundleName + " not found", wiredBundle);
	
		BundleWiring wiring = wiredBundle.adapt(BundleWiring.class);
		List<BundleWire> wiredBundles = wiring.getRequiredWires(BUNDLE_NAMESPACE);
		assertEquals ("Only one bundle expected", 1, wiredBundles.size());
	
		String requiredBundleName = (String)
			wiredBundles.get(0).getCapability().getAttributes().get(BUNDLE_NAMESPACE);
		assertEquals ("Wrong bundle requirement", BUNDLE_A, requiredBundleName);
	
		String providingBundle = wiredBundles.get(0).getProvider().getSymbolicName();
		assertEquals ("Wrong bundle provider", expectedProvidingBundleName, providingBundle);
	}
	
	/**
	 * Verify that a bundle with wiredBundleName imports a single capability in namespace
	 * from expectedProvidingBundleName
	 * @param s
	 * @param wiredBundleName
	 * @param namespace
	 * @param expectedProvidingBundleName
	 */
	protected void verifyCapabilityWiring (Subsystem s, String wiredBundleName, 
			String namespace, String expectedProvidingBundleName)
	{
		Bundle wiredBundle = getBundle(s, wiredBundleName);
		assertNotNull ("Targt bundle " + wiredBundleName + " not found", wiredBundleName);
		
		BundleWiring wiring = wiredBundle.adapt(BundleWiring.class);
		List<BundleWire> wiredProviders = wiring.getRequiredWires(namespace);
		assertEquals("Only one wire for capability namespace " + namespace +" expected", 
				1, wiredProviders.size());
		
		String capabilityNamespace = (String)
			wiredProviders.get(0).getCapability().getNamespace();
		assertEquals ("Wrong namespace", namespace, capabilityNamespace);
		
		String providingBundle = wiredProviders.get(0).getProvider().getSymbolicName();
		assertEquals ("Wrong bundle provider", expectedProvidingBundleName, providingBundle);
	}

	/**
	 * Verify that bundles with names bundleNames are installed into the subsystem with subsystemName
	 * and bundle context bc
	 * @param bc
	 * @param subsystemName
	 * @param bundleNames
	 */
	protected void verifyBundlesInstalled (BundleContext bc, String subsystemName, String ... bundleNames)
	{
		for (String bundleName: bundleNames) {
			boolean bundleFound = false;
			inner: for (Bundle b: bc.getBundles()) { 
				if (b.getSymbolicName().equals(bundleName)) { 
					bundleFound = true;
					break inner;
				}
			}
			assertTrue ("Bundle " + bundleName + " not found in subsystem " + subsystemName, bundleFound);
		}
	}
}
