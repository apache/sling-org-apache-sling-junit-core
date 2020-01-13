/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.junit.impl;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.sling.junit.Activator;
import org.apache.sling.junit.Renderer;
import org.apache.sling.junit.SlingTestContextProvider;
import org.apache.sling.junit.TestSelector;
import org.apache.sling.junit.TestsManager;
import org.apache.sling.junit.TestsProvider;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = TestsManager.class,
    immediate = true,
    configurationPolicy = ConfigurationPolicy.OPTIONAL,
    property = {
        Constants.SERVICE_DESCRIPTION+"=Service that gives access to JUnit test classes"
    }
)
//The new OSGi R6 property configuration declaration syntax.
@Designate(ocd = TestsManagerImpl.Config.class, factory=false)
public class TestsManagerImpl implements TestsManager {

    // Define OSGi R6 property configuration data type object
    @ObjectClassDefinition(
            name = "Apache Sling JUnit Tests Manager Service",
            description = "Service that gives access to JUnit test classes."
    )
    @interface Config {
        // The _'s in the method names (see below) are transformed to . when the
        // OSGi property names are generated.
        // Example: max_size -> max.size, user_name_default -> user.name.default
        @AttributeDefinition(
                name = "Wait for system startup.",
                description = "Wait for system start up.  Otherwise abort upon detecting inactive bundles."
            )
        boolean wait_for_system_startup() default true;

        @AttributeDefinition(
                name = "System start up inactivity timeout seconds.",
                description = "Seconds to wait for all inactive bundles to startup before error out."
            )
        int wait_seconds() default DEFAULT_SYSTEM_STARTUP_INACTIVITY_TIMEOUT_SECONDS;

        @AttributeDefinition(
                name = "Ignore offline bundles (Symbolic Names)",
                description = "Do not wait for these bundles.",
                required = false,
                cardinality = 500
            )
        String[] ignore_bundles() default {};
    }

	private static final Logger log = LoggerFactory.getLogger(TestsManagerImpl.class);


    // the inactivity timeout is the maximum time after the last bundle became active
    // before waiting for more bundles to become active should be aborted
    private static final int DEFAULT_SYSTEM_STARTUP_INACTIVITY_TIMEOUT_SECONDS = 10;

    private boolean waitForSystemStartup = true; 
    private boolean newInactiveBundleFound = true; 
    private int waitSystemStartupSeconds = DEFAULT_SYSTEM_STARTUP_INACTIVITY_TIMEOUT_SECONDS;
    private ConcurrentMap<String, Boolean> ignoreBundles = new ConcurrentHashMap<String, Boolean>();
    private ConcurrentMap<String, Bundle> bundlesToWaitFor = new ConcurrentHashMap<String, Bundle>();

    private ServiceTracker<TestsProvider,TestsProvider> tracker;

    private int lastTrackingCount = -1;

    private BundleContext bundleContext;
    
    // List of providers
    private List<TestsProvider> providers = new ArrayList<TestsProvider>();
    
    // Map of test names to their provider's PID
    private Map<String, String> tests = new ConcurrentHashMap<String, String>();
    
    // Last-modified values for each provider
    private Map<String, Long> lastModified = new HashMap<String, Long>();
    
    @Activate
    protected void activate(ComponentContext ctx, Config cfg) {
        bundleContext = ctx.getBundleContext();
        tracker = new ServiceTracker<TestsProvider,TestsProvider>(bundleContext, TestsProvider.class.getName(), null);
        tracker.open();
        log.debug("Ignore offline bundles (Symbolic Names): {}", Arrays.asList(cfg.ignore_bundles()));
        for( String bundle : cfg.ignore_bundles() ) {
            ignoreBundles.put(bundle, true);
        }
        waitSystemStartupSeconds = cfg.wait_seconds();
        log.debug("System startup wait seconds: {}", waitSystemStartupSeconds);
        waitForSystemStartup = cfg.wait_for_system_startup();
        log.debug("Wait for system startup: {}", waitForSystemStartup);
    }

    @Modified
    protected void modified(ComponentContext ctx, Config cfg) {
        clearCaches();
        deactivate(ctx);
        activate(ctx, cfg);
    }

    @Deactivate
    protected void deactivate(ComponentContext ctx) {
        if(tracker != null) {
            tracker.close();
        }
        tracker = null;
        bundleContext = null;
        newInactiveBundleFound = true;
    }
    
    public void clearCaches() {
        log.debug("Clearing internal caches");
        lastModified.clear();
        lastTrackingCount = -1;
        bundlesToWaitFor.clear();
        ignoreBundles.clear();
        newInactiveBundleFound = false;
    }
    
    public Class<?> getTestClass(String testName) throws ClassNotFoundException {
        maybeUpdateProviders();

        // find TestsProvider that can instantiate testName
        final String providerPid = tests.get(testName);
        if(providerPid == null) {
            throw new IllegalStateException("Provider PID not found for test " + testName);
        }
        TestsProvider provider = null;
        for(TestsProvider p : providers) {
            if(p.getServicePid().equals(providerPid)) {
                provider = p;
                break;
            }
        }
        
        if(provider == null) {
            throw new IllegalStateException("No TestsProvider found for PID " + providerPid);
        }

        log.debug("Using provider {} to create test class {}", provider, testName);
        return provider.createTestClass(testName);
    }

    public Collection<String> getTestNames(TestSelector selector) {
        maybeUpdateProviders();
        
        // If any provider has changes, reload the whole list
        // of test names (to keep things simple)
        boolean reload = false;
        for(TestsProvider p : providers) {
            final Long lastMod = lastModified.get(p.getServicePid());
            if(lastMod == null || lastMod.longValue() != p.lastModified()) {
                reload = true;
                log.debug("{} updated, will reload test names from all providers", p);
                break;
            }
        }
        
        if(reload) {
            tests.clear();
            for(TestsProvider p : providers) {
                final String pid = p.getServicePid();
                if(pid == null) {
                    log.warn("{} has null PID, ignored", p);
                    continue;
                }
                lastModified.put(pid, new Long(p.lastModified()));
                final List<String> names = p.getTestNames(); 
                for(String name : names) {
                    tests.put(name, pid);
                }
                log.debug("Added {} test names from provider {}", names.size(), p);
            }
            log.info("Test names reloaded, total {} names from {} providers", tests.size(), providers.size());
        }
        
        final Collection<String> allTests = tests.keySet();
        if(selector == null) {
            log.debug("No TestSelector supplied, returning all {} tests", allTests.size());
            return allTests;
        } else {
            final List<String> result = new LinkedList<String>();
            for(String test : allTests) {
                if(selector.acceptTestName(test)) {
                    result.add(test);
                }
            }
            log.debug("{} selected {} tests out of {}", new Object[] { selector, result.size(), allTests.size() });
            return result;
        }
    }
    
    /** Update our list of providers if tracker changed */
    private void maybeUpdateProviders() {
        if(tracker.getTrackingCount() != lastTrackingCount) {
            // List of providers changed, need to reload everything
            lastModified.clear();
            List<TestsProvider> newList = new ArrayList<TestsProvider>();
            for(ServiceReference<TestsProvider> ref : tracker.getServiceReferences()) {
                newList.add((TestsProvider)bundleContext.getService(ref));
            }
            synchronized (providers) {
                providers.clear();
                providers.addAll(newList);
            }
            log.info("Updated list of TestsProvider: {}", providers);
        }
        lastTrackingCount = tracker.getTrackingCount();
    }

    public void executeTests(Collection<String> testNames, Renderer renderer, TestSelector selector) throws Exception {
        renderer.title(2, "Running tests");

        Exception startupFailure = null;
        try {
            waitForSystemStartup();
        } catch ( Exception e ) {
            // for returning meaningful message to Teleporter client instead 
            // of HTTP 500.
            startupFailure = e;
        }

        final JUnitCore junit = new JUnitCore();
        
        // Create a test context if we don't have one yet
        final boolean createContext =  !SlingTestContextProvider.hasContext();
        if(createContext) {
            SlingTestContextProvider.createContext();
        }
        
        try {
            junit.addListener(new TestContextRunListenerWrapper(renderer.getRunListener(), startupFailure));
            for(String className : testNames) {
                renderer.title(3, className);
                
                // If we have a test context, clear its output metadata
                if(SlingTestContextProvider.hasContext()) {
                    SlingTestContextProvider.getContext().output().clear();
                }
                
                final String testMethodName = selector == null ? null : selector.getSelectedTestMethodName();
                if(testMethodName != null && testMethodName.length() > 0) {
                    log.debug("Running test method {} from test class {}", testMethodName, className);
                    junit.run(Request.method(getTestClass(className), testMethodName));
                } else {
                    log.debug("Running test class {}", className);
                    junit.run(getTestClass(className));
                }
            }
        } finally {
            if(createContext) {
                SlingTestContextProvider.deleteContext();
            }
        }
    }

    public void listTests(Collection<String> testNames, Renderer renderer) throws Exception {
        renderer.title(2, "Test classes");
        final String note = "The test set can be restricted using partial test names"
                + " as a suffix to this URL"
                + ", followed by the appropriate extension, like 'com.example.foo.tests.html'";
        renderer.info("note", note);
        renderer.list("testNames", testNames);
    }


    private synchronized void waitForSystemStartup() {

        // always detect inactive bundles
        final BundleContext bundleContext = Activator.getBundleContext();
        for (final Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getState() != Bundle.ACTIVE && !isFragment(bundle)) {
                log.debug("Bundle {} is not active.", bundle.getSymbolicName() );
                if( ! ignoreBundles.containsKey( bundle.getSymbolicName() ) ) {
                    if( ! bundlesToWaitFor.containsKey(bundle.getSymbolicName() ) ) {
                        bundlesToWaitFor.put(bundle.getSymbolicName(), bundle);
                        bundle.getSymbolicName();
                        newInactiveBundleFound = true;
                    }
                } else {
                    log.debug("Not waiting for Bundle {} to become active.", bundle.getSymbolicName() );
                }
            }
        }

        if (waitForSystemStartup && newInactiveBundleFound ) {

            // Only wait for newly detected inactive bundles.  Inactive bundles
            // usually stays inactive until manual intervention was made.  There 
        	// is no need to wait for bundles that have already failed.
            newInactiveBundleFound = false;

            // wait max inactivityTimeout after the last bundle became active before giving up
            long inactivityTimeout = TimeUnit.SECONDS.toMillis(waitSystemStartupSeconds);
            long lastChange = System.currentTimeMillis();
            while (!(bundlesToWaitFor.isEmpty() || (lastChange + inactivityTimeout < System.currentTimeMillis()))) {
                log.info("Waiting for the following bundles to start: {}", bundlesToWaitFor);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Iterator<Map.Entry<String,Bundle>> bundles = bundlesToWaitFor.entrySet().iterator();
                while (bundles.hasNext()) {
                    Map.Entry<String,Bundle> bundle = bundles.next();
                    if (bundle.getValue().getState() == Bundle.ACTIVE) {
                        bundles.remove();
                        log.debug("Bundle {} has become active", bundle.getValue().getSymbolicName());
                    }
                }
            }
        } else {
            // only detect inactive bundles 
            Iterator<Map.Entry<String,Bundle>> bundles = bundlesToWaitFor.entrySet().iterator();
            while (bundles.hasNext()) {
                Map.Entry<String,Bundle> bundle = bundles.next();
                if (bundle.getValue().getState() == Bundle.ACTIVE) {
                    bundles.remove();
                    log.debug("Bundle {} has become active", bundle.getValue().getSymbolicName());
                }
            }
        }

        if (!bundlesToWaitFor.isEmpty()) {
            if( waitForSystemStartup ) {
                log.warn("Waited {} seconds but the following bundles are not yet started: {}",
                         waitSystemStartupSeconds, bundlesToWaitFor.keySet());
            } else {
                log.warn("Following bundles are not yet started: {}",
                         bundlesToWaitFor.keySet());
            }
            throw new ServiceException(
                String.format("Inactive bundles %s detected.  Cannot run tests until inactive bundles "+
                              "are recovered or added to Apache Sling JUnit Tests Manager Service "+
                		      "'ignore.bundles' list.", bundlesToWaitFor.keySet() ),
                ServiceException.SUBCLASSED
            );
        } else {
            log.info("All bundles are active, starting to run tests.");
        }
    }

    private static boolean isFragment(final Bundle bundle) {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }
}