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

import org.jetbrains.annotations.NotNull;
import org.junit.platform.engine.TestEngine;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class TestEngineTracker implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(TestEngineTracker.class);
    
    private final BundleTracker<AtomicReference<Set<TestEngine>>> tracker;

    public TestEngineTracker(BundleContext bundleContext) {
        tracker = new BundleTracker<>(bundleContext, Bundle.ACTIVE, new Customizer());
        tracker.open();
    }

    public TestEngine[] getAvailableTestEngines() {
        return tracker.getTracked().values().stream()
                .map(AtomicReference::get)
                .flatMap(Collection::stream)
                .toArray(TestEngine[]::new);
    }

    @Override
    public void close() {
        tracker.close();
    }

    private static class Customizer implements BundleTrackerCustomizer<AtomicReference<Set<TestEngine>>> {

        @Override
        public AtomicReference<Set<TestEngine>> addingBundle(Bundle bundle, BundleEvent event) {
            return new AtomicReference<>(getTestEnginesForBundle(bundle));
        }

        @Override
        public void modifiedBundle(Bundle bundle, BundleEvent event, AtomicReference<Set<TestEngine>> testEngines) {
            testEngines.set(getTestEnginesForBundle(bundle));
        }

        @Override
        public void removedBundle(Bundle bundle, BundleEvent event, AtomicReference<Set<TestEngine>> testEngines) {
            testEngines.set(Collections.emptySet());
        }

        @NotNull
        private static Set<TestEngine> getTestEnginesForBundle(Bundle bundle) {
            final Set<TestEngine> testEngines = new HashSet<>();
            ServiceLoader
                    .load(TestEngine.class, bundle.adapt(BundleWiring.class).getClassLoader())
                    .forEach(testEngine -> {
                        LOG.info("Found TestEngine '{}' in bundle '{}'", testEngine.getId(), bundle.getSymbolicName());
                        testEngines.add(testEngine);
                    });
            return testEngines;
        }
    }
}
