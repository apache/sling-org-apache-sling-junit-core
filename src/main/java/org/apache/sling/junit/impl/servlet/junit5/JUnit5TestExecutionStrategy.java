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

import org.apache.sling.junit.TestSelector;
import org.apache.sling.junit.impl.TestExecutionStrategy;
import org.apache.sling.junit.impl.TestsManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.runner.notification.RunListener;
import org.osgi.framework.BundleContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

public class JUnit5TestExecutionStrategy implements TestExecutionStrategy {

    // we assume that if we can load these two classes, the setup is ok for junit 5
    private static final List<String> REQUIRED_CLASSES = Collections.unmodifiableList(Arrays.asList(
            "org.junit.platform.engine.TestEngine",
            "org.junit.platform.launcher.Launcher"
    ));

    public static boolean canLoadRequiredClasses() {
        final ClassLoader classLoader = JUnit5TestExecutionStrategy.class.getClassLoader();
        return REQUIRED_CLASSES.stream()
                .allMatch(name -> {
                    String path = name.replace('.', '/').concat(".class");
                    return classLoader.getResource(path) != null;
                });
    }

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
    public void execute(TestSelector selector, RunListener runListener) throws Exception {
        Launcher launcher = createLauncher(runListener, testEngineTracker.getAvailableTestEngines());
        final LauncherDiscoveryRequest request = testsManager.createTestRequest(selector,
                JUnit5TestExecutionStrategy::methodRequest,
                JUnit5TestExecutionStrategy::classesRequest);
        launcher.execute(request);
    }

    @NotNull
    public static Launcher createLauncher(RunListener runListener, TestEngine... availableTestEngines) {
        return LauncherFactory.create(
                LauncherConfig.builder()
                        .addTestEngines(availableTestEngines)
                        .addTestExecutionListeners(new RunListenerAdapter(runListener))
                        .enableTestEngineAutoRegistration(false)
                        .enableTestExecutionListenerAutoRegistration(false)
                        .build()
        );
    }

    @NotNull
    public static LauncherDiscoveryRequest methodRequest(Class<?> testClass, String testMethodName) {
        return LauncherDiscoveryRequestBuilder.request()
                .selectors(selectMethod(testClass, testMethodName))
                .build();
    }

    @NotNull
    public static LauncherDiscoveryRequest classesRequest(Class<?>... testClasses) {
        final DiscoverySelector[] selectors = Stream.of(testClasses)
                .map(DiscoverySelectors::selectClass)
                .toArray(DiscoverySelector[]::new);
        return LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .build();
    }
}
