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
import org.apache.sling.junit.TestsManager;
import org.apache.sling.junit.TestsProvider;
import org.apache.sling.junit.impl.servlet.PlainTextRenderer;
import org.apache.sling.junit.sampletests.JUnit4SlingJUnit;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.vintage.engine.VintageTestEngine;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleWiring;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validate waitForSystemStartup method, along with private some implementations.
 */
public class TestsManagerImplTest {

    private static final int SYSTEM_STARTUP_SECONDS = 2;

    private Set<Bundle> mockBundles = new HashSet<>();

    static {
        // Set a short timeout so our tests can run faster
        System.setProperty("sling.junit.core.SystemStartupTimeoutSeconds", String.valueOf(SYSTEM_STARTUP_SECONDS));
    }

    /**
     * case if needToWait should return true, mainly it still have some bundles in the list to wait, and global timeout didn't pass.
     */
    @Test
    public void needToWaitPositiveNotEmptyListNotGloballyTimeout() {
        long startupTimeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5 * SYSTEM_STARTUP_SECONDS);
        final Set<Bundle> bundlesToWaitFor = new HashSet<>(singletonList(mock(Bundle.class)));
        assertTrue(TestsManagerImpl.needToWait(startupTimeout, bundlesToWaitFor));
    }

    /**
     * case if needToWait should return false, when for example it reached the global timeout limit.
     */
    @Test
    public void needToWaitNegativeForstartupTimeout() {
        long lastChange = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS / 2);
        long startupTimeout = lastChange - TimeUnit.SECONDS.toMillis(1);
        assertFalse(TestsManagerImpl.needToWait(startupTimeout, emptySet()));
    }

    /**
     * case if needToWait should return false, when for example it reached the global timeout limit.
     */
    @Test
    public void needToWaitNegativeForEmptyList() {
        long lastChange = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS / 2);
        long startupTimeout = lastChange + TimeUnit.SECONDS.toMillis(10);
        assertFalse(TestsManagerImpl.needToWait(startupTimeout, emptySet()));
    }

    @Test
    public void waitForSystemStartupTimeout() {
        BundleContext bundleContext = setupBundleContext(Bundle.INSTALLED);
        TestsManagerImpl testsManager = new TestsManagerImpl();
        testsManager.activate(bundleContext);

        assertFalse(testsManager.isReady());

        final long elapsed = testsManager.waitForSystemStartup();
        assertTrue(elapsed > TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS));
        assertTrue(elapsed < TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS + 1));
        assertTrue(testsManager.isReady());

        // second call is instantaneous
        assertTrue(10 > testsManager.waitForSystemStartup());

        testsManager.deactivate();
    }

    @Test
    public void waitForSystemStartupAllActiveBundles() {
        BundleContext bundleContext = setupBundleContext(Bundle.ACTIVE);
        TestsManagerImpl testsManager = new TestsManagerImpl();
        testsManager.activate(bundleContext);

        assertFalse(testsManager.isReady());

        final long elapsed = testsManager.waitForSystemStartup();
        assertTrue(elapsed < TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS));
        assertTrue(testsManager.isReady());

        testsManager.deactivate();
    }

    @Test
    public void testDeactivateBeforeActivateIgnored() {
        try {
            new TestsManagerImpl().deactivate();
        } catch (Exception e) {
            fail("deactivate before activate should be a no-op");
        }
    }

    private BundleContext setupBundleContext(int state) {
        final Bundle bundle = mock(Bundle.class);
        when(bundle.getSymbolicName()).thenReturn("mocked-bundle");
        when(bundle.getState()).thenReturn(state);
        when(bundle.adapt(BundleWiring.class)).thenReturn(mock(BundleWiring.class));
        when(bundle.getHeaders()).thenReturn(new Hashtable<>());

        final BundleContext bundleContext = mock(BundleContext.class);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundleContext.getBundles()).thenAnswer(m -> new Bundle[]{bundle});

        when(bundle.getBundleContext()).thenReturn(bundleContext);
        return bundleContext;
    }


    @Test
    public void testGettingTestNamesAndClassesAndExecution() throws Exception {

        final ArrayList<String> allTestClasses = new ArrayList<>();

        for (int i = 0; i < 5; i++) {

            final List<String> testClasses = asList(
                    "org.apache.sling.junit.testbundle" + i + ".ASlingJUnit",
                    "org.apache.sling.junit.testbundle" + i + ".impl.ANestedSlingJUnit"
            );

            allTestClasses.addAll(testClasses);

            final List<String> nonTestClasses = asList(
                    "org.apache.sling.junit.testbundle" + i + ".NotATest",
                    "org.apache.sling.junit.testbundle" + i + ".impl.AlsoNotATest",
                    "org.apache.sling.junit.testbundle" + i + ".CompletelyUnrelated"
            );

            final List<String> classes = new ArrayList<>();
            classes.addAll(testClasses);
            classes.addAll(nonTestClasses);
            classes.sort(Comparator.naturalOrder());

            createTestBundle(
                    "test-bundle-" + i,
                    "org.apache.sling.junit.testbundle" + i + ".*SlingJUnit",
                    classes
            );
        }

        createTestBundle("test-bundle-no-tests", "org.apache.sling.junit.notests.*SlingJUnit", emptyList());
        createTestBundle("test-bundle-invalid-regexp", "[a-z", emptyList());
        createTestBundle("test-bundle-no-regexp", null, emptyList());

        final Bundle junitBundle = createJUnitBundleMock("junit-bundle", Bundle.ACTIVE);
        addBundleWiring(junitBundle, VintageTestEngine.class.getClassLoader());
        final BundleContext bundleContext = junitBundle.getBundleContext();
        final BundleTestsProvider bundleTestsProvider =
                activateAndRegister(bundleContext, TestsProvider.class, new BundleTestsProvider(), BundleTestsProvider::activate);
        final TestsManagerImpl testsManager =
                activateAndRegister(bundleContext, TestsManager.class, new TestsManagerImpl(), TestsManagerImpl::activate);

        final RequestParser selector = new RequestParser(null);
        final Collection<String> testNames = testsManager.getTestNames(selector);

        assertThat("should find all tests", testNames, Matchers.containsInAnyOrder(allTestClasses.toArray(new String[0])));

        for (String testName : testNames) {
            assertThat("should be able to load class " + testName, testsManager.getTestClass(testName), Matchers.isA(Class.class));
        }

        try {
            testsManager.getTestClass("a.class.that.does.not.Exist");
            fail("should not load non-existant test class");
        } catch (ClassNotFoundException e) {
            // expected
        }

        testsManager.executeTests(createRenderer(), selector);

        testsManager.deactivate();
        bundleTestsProvider.deactivate();
    }

    private <T> T activateAndRegister(BundleContext bundleContext, Class<? super T> interfaze, T service, BiConsumer<T, BundleContext> activator)
            throws InvalidSyntaxException {
        activator.accept(service, bundleContext);
        registerService(bundleContext, service, interfaze);
        return service;
    }

    private static Renderer createRenderer() throws IOException {
        final PlainTextRenderer renderer = new PlainTextRenderer();
        final HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));
        renderer.setup(response, "Test");
        return renderer;
    }

    @NotNull
    private Bundle createJUnitBundleMock(String symbolicName, int state) {
        final Bundle bundle = mock(Bundle.class);
        when(bundle.getSymbolicName()).thenReturn(symbolicName);
        when(bundle.getState()).thenReturn(state);
        when(bundle.getHeaders()).thenReturn(new Hashtable<>());

        final BundleContext bundleContext = mock(BundleContext.class);
        when(bundleContext.getBundle()).thenReturn(bundle);

        when(bundle.getBundleContext()).thenReturn(bundleContext);

        when(bundleContext.getBundles())
                .thenAnswer(m -> mockBundles.toArray(new Bundle[0]));

        mockBundles.add(bundle);

        return bundle;
    }

    private void createTestBundle(String symbolicName, String testRegexp, Collection<String> classes)
            throws ClassNotFoundException, IOException {
        final Bundle bundle = createJUnitBundleMock(symbolicName, Bundle.ACTIVE);

        when(bundle.findEntries("", "*.class", true)).thenAnswer(m -> classesAsResourceEnumeration(classes));

        // we just return the Object.class instead of a real class - we're not doing anything with it
        when(bundle.loadClass(argThat(classes::contains))).then(m -> JUnit4SlingJUnit.class);
        assertThat("cannot load just any class", bundle.loadClass("any.class.Name"), nullValue());

        if (testRegexp != null) {
            bundle.getHeaders().put(BundleTestsProvider.SLING_TEST_REGEXP, testRegexp);
        }

        final ClassLoader classLoader = mock(ClassLoader.class);
        when(classLoader.getResources(any())).thenAnswer(m -> Collections.emptyEnumeration());
        addBundleWiring(bundle, classLoader);
    }

    private static <T> void registerService(BundleContext bundleContext, T service, Class<? super T> interfaze) throws InvalidSyntaxException {
        @SuppressWarnings("unchecked") final ServiceReference<T> serviceReference = (ServiceReference<T>) mock(ServiceReference.class);
        final Set<ServiceReference<T>> references = Collections.singleton(serviceReference);
        when(bundleContext.getServiceReferences(interfaze, null)).thenAnswer(m -> references);
        when(bundleContext.getServiceReferences(interfaze.getName(), null))
                .thenAnswer(m -> references.toArray(new ServiceReference[0]));
        when(bundleContext.getServiceReference(interfaze)).thenAnswer(m -> serviceReference);
        when(bundleContext.getServiceReference(interfaze.getName())).thenAnswer(m -> serviceReference);
        when(bundleContext.getService(serviceReference)).thenReturn(service);
    }

    private static void addBundleWiring(Bundle bundle, ClassLoader classLoader) {
        final BundleWiring bundleWiring = mock(BundleWiring.class);
        when(bundleWiring.getClassLoader()).thenReturn(classLoader);
        when(bundle.adapt(BundleWiring.class)).thenReturn(bundleWiring);
    }

    private static Enumeration<URL> classesAsResourceEnumeration(Collection<String> classes) {
        final List<URL> resources = classes.stream()
                .map(clazz -> '/' + clazz.replace('.', '/') + ".class")
                .map(file -> {
                    try {
                        // In Apache Felix URLs look like this:
                        //     bundle://<random-bundle-identifier>:0/org/path/to/ClassName.class
                        // However, the "bundle" protocol causes an exception
                        // and in any case, only the URL's file part is used.
                        return new URL("file://pseudo:80" + file);
                    } catch (MalformedURLException e) {
                        fail(e.getMessage());
                    }
                    // cannot be reached because "fail()" throws an AssertionError
                    return null;
                })
                .collect(Collectors.toList());
        return Collections.enumeration(resources);
    }
}