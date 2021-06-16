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
import org.jetbrains.annotations.Nullable;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

/**
 * Utilities for running tests via the JUnit Platform. I.e. depending on the supplied {@link TestEngine}(s),
 * it is possible to run JUnit3, JUnit4 or Jupiter based tests using {@code TestEngines} supplied by
 * the JUnit team. No issues are expected running bespoke {@code TestEngines}.
 */
public final class JUnitPlatformHelper {

    /**
     * Execute a test class (if {@code testMethodName == null}) or a single test method with a specified {@code TestEngine}.
     * All provided {@code TestExecutionListener}s are registered to be notified of the test's execution.
     *
     * @param testEngine  a {@code TestEngine} instance
     * @param testClass   a test class that can be executed by the given {@code TestEngine}
     * @param testMethodName  the name of a test method in the given test class or null to run all test methods
     * @param listeners   any number of {@code TestExecutionListener}s that should be notified
     */
    public static void executeTest(@NotNull TestEngine testEngine, @NotNull Class<?> testClass, @Nullable String testMethodName, @NotNull TestExecutionListener... listeners) {
        final Launcher launcher = JUnitPlatformHelper.createLauncher(testEngine);
        final LauncherDiscoveryRequest request = testMethodName != null
                ? JUnitPlatformHelper.methodRequest(testClass, testMethodName)
                : JUnitPlatformHelper.classesRequest(testClass);
        launcher.execute(request, listeners);
    }

    /**
     * Utility method to create a {@link Launcher} for the given {@code TestEngines} only, without
     * any automatically registered {@code TestEngines} or {@code TestExecutionListeners}.
     *
     * @param testEngines The test engines available to the {@code Launcher} instance.
     * @return A JUnit Platform {@code Launcher} instance.
     */
    @NotNull
    public static Launcher createLauncher(TestEngine... testEngines) {
        return LauncherFactory.create(LauncherConfig.builder()
                .enableTestEngineAutoRegistration(false)
                .enableTestExecutionListenerAutoRegistration(false)
                .addTestEngines(testEngines)
                .build());
    }

    /**
     * Utility to create a {@link LauncherDiscoveryRequest} for a particular test method, specified by the
     * test class and the test method's name. If multiple overloaded test methods with different parameters
     * exist, they would all be executed.
     *
     * @param testClass   a test class
     * @param testMethodName  the name of a test method in the given test class or null to run all test methods
     * @return a {@code LauncherDiscoveryRequest} representing the specified test method.
     */
    @NotNull
    public static LauncherDiscoveryRequest methodRequest(Class<?> testClass, String testMethodName) {
        final LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
        ReflectionUtils.findMethods(testClass, method -> Objects.equals(method.getName(), testMethodName)).stream()
                .map(method -> selectMethod(testClass, method))
                .forEach(requestBuilder::selectors);
        return requestBuilder.build();
    }


    /**
     * Utility to create a {@link LauncherDiscoveryRequest} for all test methods of the specified test class(es).
     *
     * @param testClasses   a number of test classes
     * @return a {@code LauncherDiscoveryRequest} representing the specified test classes.
     */
    @NotNull
    public static LauncherDiscoveryRequest classesRequest(Class<?>... testClasses) {
        final DiscoverySelector[] selectors = Stream.of(testClasses)
                .map(DiscoverySelectors::selectClass)
                .toArray(DiscoverySelector[]::new);
        return LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .build();
    }

    private JUnitPlatformHelper() {
        // no instances
    }
}
