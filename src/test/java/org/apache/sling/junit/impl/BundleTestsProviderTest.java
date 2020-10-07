package org.apache.sling.junit.impl;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BundleTestsProviderTest {

    @Test
    public void testGettingTestNamesAndClasses() throws ClassNotFoundException {

        final ArrayList<Object> allTestClasses = new ArrayList<>();

        final List<Bundle> testBundles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {

            final List<String> testClasses = asList(
                    "org.apache.sling.junit.testbundle" + i + ".ASlingTest",
                    "org.apache.sling.junit.testbundle" + i + ".impl.ANestedSlingTest"
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

            testBundles.add(createTestBundle(
                    "test-bundle-" + i,
                    "org.apache.sling.junit.testbundle" + i + ".*SlingTest",
                    classes
            ));
        }

        final BundleContext bundleContext = mock(BundleContext.class);
        when(bundleContext.getBundles()).thenAnswer(m -> testBundles.toArray(new Bundle[0]));

        final BundleTestsProvider bundleTestsProvider = new BundleTestsProvider();
        bundleTestsProvider.activate(bundleContext);

        final List<String> testNames = bundleTestsProvider.getTestNames();

        assertThat("found all tests", testNames, Matchers.containsInAnyOrder(allTestClasses.toArray(new String[0])));

        for (String testName : testNames) {
            assertThat("can load class " + testName, bundleTestsProvider.createTestClass(testName), Matchers.isA(Class.class));
        }
    }

    private static Bundle createTestBundle(String symbolicName, String testRegexp, Collection<String> classes) throws ClassNotFoundException {
        final Bundle bundle = mock(Bundle.class);

        when(bundle.getSymbolicName()).thenReturn(symbolicName);
        when(bundle.getState()).thenReturn(Bundle.ACTIVE);

        // we just return the Object.class instead of a real class - we're not doing anything with it
        when(bundle.loadClass(argThat(classes::contains))).then(m -> Object.class);
        assertThat("cannot load just any class", bundle.loadClass("any.class.Name"), nullValue());

        final Hashtable<String, String> headers = new Hashtable<>();
        headers.put(BundleTestsProvider.SLING_TEST_REGEXP, testRegexp);
        when(bundle.getHeaders()).thenReturn(headers);

        when(bundle.findEntries("", "*.class", true)).thenReturn(classesAsResourceEnumeration(classes));

        final BundleContext bundleContext = mock(BundleContext.class);
        when(bundleContext.getBundle()).thenReturn(bundle);

        when(bundle.getBundleContext()).thenReturn(bundleContext);
        return bundle;
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
                        return new URL("http://pseudo:80" + file);
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