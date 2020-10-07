package org.apache.sling.junit.impl.servlet.junit5;

import org.apache.sling.junit.impl.BundleTestsProvider;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.platform.engine.TestEngine;
import org.junit.vintage.engine.VintageTestEngine;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

import java.util.Collection;
import java.util.Hashtable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.array;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestEngineTrackerTest {

    @Test
    public void testGettingTestEngine() {
        final TestEngineTracker testEngineTracker = new TestEngineTracker(createTestBundle().getBundleContext());
        final TestEngine[] availableTestEngines = testEngineTracker.getAvailableTestEngines();
        assertThat(availableTestEngines, arrayWithSize(1));
        assertThat(availableTestEngines[0], instanceOf(VintageTestEngine.class));
        testEngineTracker.close();
    }

    private static Bundle createTestBundle() {
        final Bundle bundle = mock(Bundle.class);

        when(bundle.getSymbolicName()).thenReturn("test-bundle");
        when(bundle.getState()).thenReturn(Bundle.ACTIVE);

        final BundleWiring bundleWiring = mock(BundleWiring.class);
        when(bundleWiring.getClassLoader()).thenReturn(VintageTestEngine.class.getClassLoader());
        when(bundle.adapt(BundleWiring.class)).thenReturn(bundleWiring);

        final BundleContext bundleContext = mock(BundleContext.class);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundleContext.getBundles()).thenAnswer(m -> new Bundle[] { bundle });

        when(bundle.getBundleContext()).thenReturn(bundleContext);
        return bundle;
    }

}