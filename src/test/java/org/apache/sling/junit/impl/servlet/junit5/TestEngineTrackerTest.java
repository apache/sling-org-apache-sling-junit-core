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
package org.apache.sling.junit.impl.servlet.junit5;

import org.junit.Test;
import org.junit.platform.engine.TestEngine;
import org.junit.vintage.engine.VintageTestEngine;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.instanceOf;
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