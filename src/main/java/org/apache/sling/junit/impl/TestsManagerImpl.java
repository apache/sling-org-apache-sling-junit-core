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

import org.apache.sling.junit.Renderer;
import org.apache.sling.junit.RequestParser;
import org.apache.sling.junit.TestSelector;
import org.apache.sling.junit.TestsManager;
import org.apache.sling.junit.TestsProvider;
import org.apache.sling.junit.impl.servlet.junit5.JUnit5TestExecutionStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TestsManagerImpl implements TestsManager {

    private static final Logger log = LoggerFactory.getLogger(TestsManagerImpl.class);

    // Global Timeout up to which it stop waiting for bundles to be all active, default to 40 seconds.
    public static final String PROP_STARTUP_TIMEOUT_SECONDS = "sling.junit.core.SystemStartupTimeoutSeconds";

    private final int startupTimeoutSeconds = Integer.parseInt(System.getProperty(PROP_STARTUP_TIMEOUT_SECONDS, "40"));

    private volatile boolean waitForSystemStartup = true;

    boolean isReady() {
        return !waitForSystemStartup;
    }

    private BundleContext bundleContext;

    private ServiceTracker<TestsProvider, TestsProvider> testsProviderTracker;
    
    private TestExecutionStrategy executionStrategy;

    @Activate
    protected void activate(BundleContext ctx) {
        bundleContext = ctx;
        testsProviderTracker = new ServiceTracker<>(bundleContext, TestsProvider.class, null);
        testsProviderTracker.open();
        if (JUnit5TestExecutionStrategy.canLoadRequiredClasses()) {
            executionStrategy = new JUnit5TestExecutionStrategy(this, ctx);
        } else  {
            // (some) optional imports to org.junit.platform.* (JUnit5 API) are missing
            executionStrategy = new JUnit4TestExecutionStrategy(this);
        }
    }

    @Deactivate
    protected void deactivate() {
        if(testsProviderTracker != null) {
            testsProviderTracker.close();
            testsProviderTracker = null;
        }

        if (executionStrategy != null) {
            executionStrategy.close();
            executionStrategy = null;
        }

        bundleContext = null;
    }

    @NotNull
    public Class<?> getTestClass(@NotNull String testName) throws ClassNotFoundException {
        final TestsProvider provider = getTestProviders()
                .filter(p -> p.getTestNames().contains(testName))
                .findFirst()
                .orElseThrow(() -> new ClassNotFoundException("No TestsProvider found for test '" + testName + "'"));

        log.debug("Using provider {} to create test class {}", provider, testName);
        return provider.createTestClass(testName);
    }

    @Override
    public Collection<String> getTestNames(@Nullable TestSelector selector) {
        final List<String> tests = getTestProviders()
                .map(TestsProvider::getTestNames)
                .flatMap(Collection::stream)
                .sorted()
                .collect(Collectors.toList());
        final int allTestsCount = tests.size();
        if(selector == null) {
            log.debug("No TestSelector supplied, returning all {} tests", allTestsCount);
        } else {
            tests.removeIf(testName -> !selector.acceptTestName(testName));
            log.debug("{} selected {} tests out of {}", selector, tests.size(), allTestsCount);
        }
        return tests;
    }

    private Stream<TestsProvider> getTestProviders() {
        return testsProviderTracker.getTracked().values().stream();
    }

    @Override
    public void executeTests(@Nullable Collection<String> testNames, @NotNull Renderer renderer, @Nullable TestSelector selector) throws Exception {
        if (selector != null) {
            executeTests(renderer, selector);
        } else if (testNames != null){
            executeTests(renderer, new RequestParser(null) {
                @Override
                public boolean acceptTestName(String testName) {
                    return testNames.contains(testName);
                }
            });
        } else {
            executeTests(renderer, null);
        }
    }

    @Override
    public void executeTests(@NotNull Renderer renderer, @Nullable TestSelector selector) throws Exception {
        renderer.title(2, "Running tests");
        waitForSystemStartup();
        executionStrategy.execute(selector, new TestContextRunListenerWrapper(renderer.getRunListener()));
    }

    public <T> T createTestRequest(TestSelector selector,
                            BiFunction<Class<?>, String, T> methodRequestFactory,
                            Function<Class<?>[], T> classesRequestFactory) throws ClassNotFoundException {
        final T request;
        final Collection<String> testNames = getTestNames(selector);
        if (testNames.isEmpty()) {
            throw new NoTestCasesFoundException();
        }
        final String testMethodName = selector == null ? null : selector.getSelectedTestMethodName();
        if (testNames.size() == 1 && isNotBlank(testMethodName)) {
            final String className = testNames.iterator().next();
            log.debug("Running test method {} from test class {}", testMethodName, className);
            request = methodRequestFactory.apply(getTestClass(className), testMethodName);
        } else {
            if (isNotBlank(testMethodName)) {
                throw new IllegalStateException("A test method name is only supported for a single test class");
            }
            final List<Class<?>> testClasses = new ArrayList<>();
            for (String className : testNames) {
                log.debug("Running test class {}", className);
                testClasses.add(getTestClass(className));
            }
            request = classesRequestFactory.apply(testClasses.toArray(new Class[0]));
        }
        return request;
    }

    private static boolean isNotBlank(String str) {
        return str != null && str.length() > 0;
    }

    @Override
    public void listTests(@NotNull Collection<String> testNames, @NotNull Renderer renderer) {
        renderer.title(2, "Test classes");
        final String note = "The test set can be restricted using partial test names"
                + " as a suffix to this URL"
                + ", followed by the appropriate extension, like 'com.example.foo.tests.html'";
        renderer.info("note", note);
        renderer.list("testNames", testNames);
    }

    @Override
    public void clearCaches() {
        // deprecated method kept for backwards compatibility
    }

    /** Wait for all bundles to be started
     *  @return number of msec taken by this method to execute
    */
    long waitForSystemStartup() {
        long elapsedMsec = -1;
        if (waitForSystemStartup) {
            waitForSystemStartup = false;
            final Set<Bundle> bundlesToWaitFor = Stream.of(bundleContext.getBundles())
                    .filter(not(TestsManagerImpl::isActive).and(not(TestsManagerImpl::isFragment)))
                    .collect(Collectors.toSet());

            // wait max inactivityTimeout after the last bundle became active before giving up
            final long startTime = System.currentTimeMillis();
            final long startupTimeout = startTime + TimeUnit.SECONDS.toMillis(startupTimeoutSeconds);
            while (needToWait(startupTimeout, bundlesToWaitFor)) {
                log.info("Waiting for bundles to start: {}", bundlesToWaitFor);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                bundlesToWaitFor.removeIf(TestsManagerImpl::isActive);
            }

            elapsedMsec = System.currentTimeMillis() - startTime;

            if (!bundlesToWaitFor.isEmpty()) {
                log.warn("Waited {} milliseconds but the following bundles are not yet started: {}",
                    elapsedMsec, bundlesToWaitFor);
            } else {
                log.info("All bundles are active, starting to run tests.");
            }

        }

        return elapsedMsec;
    }

    static boolean needToWait(final long startupTimeout, final Collection<Bundle> bundlesToWaitFor) {
        return startupTimeout > System.currentTimeMillis() && !bundlesToWaitFor.isEmpty();
    }

    private static <T> Predicate<T> not(Predicate<T> predicate) {
        return predicate.negate();
    }

    private static boolean isFragment(final Bundle bundle) {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }

    private static boolean isActive(Bundle bundle) {
        return bundle.getState() == Bundle.ACTIVE;
    }
}
