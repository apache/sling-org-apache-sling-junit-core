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

import org.apache.sling.junit.Renderer;
import org.apache.sling.junit.SlingTestContextProvider;
import org.apache.sling.junit.TestSelector;
import org.apache.sling.junit.impl.TestContextRunListenerWrapper;
import org.apache.sling.junit.impl.TestExecutionStrategy;
import org.apache.sling.junit.impl.TestsManagerImpl;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

public class JUnit5TestExecutionStrategy implements TestExecutionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(JUnit5TestExecutionStrategy.class);

    private final TestsManagerImpl testsManager;

    private final TestEngineTracker testEngineTracker;

    public JUnit5TestExecutionStrategy(TestsManagerImpl testsManager, BundleContext ctx) {
        this.testsManager = testsManager;
        testEngineTracker = new TestEngineTracker(ctx);
    }

    @Override
    public void close() {
        testEngineTracker.close();
    }

    @Override
    public void execute(Renderer renderer, Collection<String> testNames, TestSelector selector) throws Exception {
        TestExecutionListener listener = new TestExecutionListener() {
            @Override
            public void executionStarted(TestIdentifier testIdentifier) {
                testIdentifier.getSource().ifPresent(src -> {
                    if (src instanceof ClassSource) {
                        final String className = ((ClassSource) src).getClassName();
                        renderer.title(3, className);
                    }
                });

                // If we have a test context, clear its output metadata
                if (SlingTestContextProvider.hasContext()) {
                    SlingTestContextProvider.getContext().output().clear();
                }
            }
        };
        Launcher launcher = LauncherFactory.create(
                LauncherConfig.builder()
                        .addTestEngines(testEngineTracker.getAvailableTestEngines())
                        .addTestExecutionListeners(listener, new RunListenerAdapter(new TestContextRunListenerWrapper(renderer.getRunListener())))
                        .enableTestEngineAutoRegistration(false)
                        .enableTestExecutionListenerAutoRegistration(false)
                        .build()
        );

        final LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
        if (testNames.size() == 1) {
            final String className = testNames.iterator().next();
            final Class<?> testClass = testsManager.getTestClass(className);
            final String testMethodName = selector == null ? null : selector.getSelectedTestMethodName();
            if (testMethodName != null && testMethodName.length() > 0) {
                LOG.debug("Running test method {} from test class {}", testMethodName, className);
                requestBuilder.selectors(selectMethod(testClass, testMethodName));
            } else {
                LOG.debug("Running test class {}", className);
                requestBuilder.selectors(selectClass(testClass));
            }
        } else {
            final List<ClassSelector> testSelectors = testNames.stream()
                    .map(className -> {
                        try {
                            return testsManager.getTestClass(className);
                        } catch (ClassNotFoundException e) {
                            LOG.warn("Failed to find test class '{}'", className);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .map(DiscoverySelectors::selectClass)
                    .collect(Collectors.toList());

            requestBuilder.selectors(testSelectors);
        }

        launcher.execute(requestBuilder.build());

    }
}
