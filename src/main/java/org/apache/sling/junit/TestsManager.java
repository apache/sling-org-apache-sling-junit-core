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
package org.apache.sling.junit;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Service that gives access to JUnit test classes
 */
@ProviderType
public interface TestsManager {
    /**
     * Return the names of available tests
     *
     * @param selector if null, returns all available tests.
     * @return the name of the tests
     */
    Collection<String> getTestNames(@Nullable TestSelector selector);

    /**
     * Instantiate test class for specified test
     *
     * @param testName the test class
     * @return an instance of the class
     * @throws ClassNotFoundException if a class for {@code testName} cannot be found
     */
    Class<?> getTestClass(@NotNull String testName) throws ClassNotFoundException;

    /**
     * List tests using supplied Renderer - does NOT call setup or cleanup on renderer.
     *
     * @param testNames the tests to list
     * @param renderer  the renderer to use
     * @throws Exception if any error occurs
     */
    void listTests(@NotNull Collection<String> testNames, @NotNull Renderer renderer) throws Exception;

    /**
     * Execute tests and report results using supplied Renderer - does NOT call setup or cleanup on renderer.
     *
     * @param renderer  the renderer to use for the reporting
     * @param selector  the selector used to select tests and test methods; all tests are executed if this is null
     * @throws NoTestCasesFoundException if no tests matching the selector are available
     * @throws Exception if an error occurs
     */
    void executeTests(@NotNull Renderer renderer, @Nullable TestSelector selector) throws NoTestCasesFoundException, Exception;

    /**
     * Execute tests and report results using supplied Renderer - does NOT call setup or cleanup on renderer.
     *
     * @param testNames the tests
     * @param renderer  the renderer to use for the reporting
     * @param selector  the selector used to select tests and test methods (it can be {@code null})
     * @throws Exception if any error occurs
     *
     * @deprecated use {@link #executeTests(Renderer, TestSelector)} instead
     */
    @Deprecated
    void executeTests(@Nullable Collection<String> testNames, @NotNull Renderer renderer, @Nullable TestSelector selector)
            throws Exception;

    /**
     * Clear our internal caches. Useful in automated testing, to make sure changes introduced by recent uploads or configuration or bundles
     * changes are taken into account immediately.
     *
     * @deprecated Caches have been removed.
     */
    @Deprecated
    void clearCaches();

    class NoTestCasesFoundException extends RuntimeException {}
}
