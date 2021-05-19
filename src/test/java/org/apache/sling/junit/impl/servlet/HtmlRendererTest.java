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
package org.apache.sling.junit.impl.servlet;

import org.apache.sling.junit.impl.servlet.junit5.JUnit5TestExecutionStrategy;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.runner.notification.RunListener;
import org.junit.vintage.engine.VintageTestEngine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;

public class HtmlRendererTest {

    @SuppressWarnings("unused")
    private static Stream<Arguments> testEngines() {
        return Stream.of(
                Arguments.of("junit4", new VintageTestEngine()),
                Arguments.of("jupiter", new JupiterTestEngine())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testEngines")
    public void testInvalidAssumption(String prefix, TestEngine testEngine) {
        String html = renderHtmlOutput(testEngine, ExampleTestCases.class, prefix + "FailedAssumption");
        assertThat(html, Matchers.containsString(
                String.format("<p class='ignored'><h3>TEST ABORTED</h3><b>Assumption failed: %s</b></p>",
                        ExampleTestCases.ASSUMPTION_IS_ALWAYS_INVALID)));
        assertThat(html, Matchers.containsString("<span class='testCountNonZero'>tests:1</span>"));
        assertThat(html, Matchers.containsString("<span class='abortedCountNonZero'>aborted:1</span>"));
    }

    @ParameterizedTest
    @MethodSource("testEngines")
    public void testFailure(String prefix, TestEngine testEngine) {
        final String failedAssertion = "FailedAssertion";
        String html = renderHtmlOutput(testEngine, ExampleTestCases.class, prefix + failedAssertion);
        assertThat(html, Matchers.containsString("class='failure'"));
        assertThat(html, Matchers.containsString("class='failureDetails'"));
        assertThat(html, Matchers.containsString(String.format("<h3>TEST FAILED: %s%s(%s)</h3>", prefix, failedAssertion, ExampleTestCases.class.getName())));
        assertThat(html, Matchers.containsString(ExampleTestCases.ASSERTION_ALWAYS_FAILS));
        assertThat(html, Matchers.containsString("<span class='testCountNonZero'>tests:1</span>"));
        assertThat(html, Matchers.containsString("<span class='failureCountNonZero'>failures:1</span>"));
    }

    @ParameterizedTest
    @MethodSource("testEngines")
    public void testSuccess(String prefix, TestEngine testEngine) {
        String html = renderHtmlOutput(testEngine, ExampleTestCases.class, prefix + "Success");
        assertThat(html, Matchers.containsString("<span class='testCountNonZero'>tests:1</span>"));
        assertThat(html, Matchers.containsString("<span class='failureCountZero'>failures:0</span>"));
    }

    private static String renderHtmlOutput(TestEngine testEngine, Class<ExampleTestCases> testClass, String methodName) {
        final StringWriter out = new StringWriter();
        final HtmlRenderer htmlRenderer = new HtmlRenderer();
        htmlRenderer.setWriter(new PrintWriter(out));
        runTest(testEngine, htmlRenderer, testClass, methodName);
        return out.toString();
    }

    private static void runTest(TestEngine testEngine, RunListener runListener, Class<?> testClass, String methodName) {
        final Launcher launcher = JUnit5TestExecutionStrategy.createLauncher(runListener, testEngine);
        final LauncherDiscoveryRequest request = methodName != null
                ? JUnit5TestExecutionStrategy.methodRequest(testClass, methodName)
                : JUnit5TestExecutionStrategy.classesRequest(testClass);
        launcher.execute(request);
    }

    public static class ExampleTestCases {

        public static final String ASSUMPTION_IS_ALWAYS_INVALID = "Assumption is always invalid";

        public static final String ASSERTION_ALWAYS_FAILS = "Assertion always fails";

        public static final String ASSERTION_ALWAYS_SUCCEEDS = "Assertion always succeeds";

        @org.junit.jupiter.api.Test
        public void jupiterFailedAssumption() {
            Assumptions.assumeFalse(true, ASSUMPTION_IS_ALWAYS_INVALID);
        }

        @org.junit.jupiter.api.Test
        public void jupiterFailedAssertion() {
            Assertions.fail(ASSERTION_ALWAYS_FAILS);
        }

        @org.junit.jupiter.api.Test
        public void jupiterSuccess() {
            Assertions.assertTrue(true, ASSERTION_ALWAYS_SUCCEEDS);
        }

        @org.junit.Test
        public void junit4FailedAssumption() {
            Assume.assumeFalse(ASSUMPTION_IS_ALWAYS_INVALID, true);
        }

        @org.junit.Test
        public void junit4FailedAssertion() {
            Assert.fail(ASSERTION_ALWAYS_FAILS);
        }

        @org.junit.Test
        public void junit4Success() {
            Assert.assertTrue(ASSERTION_ALWAYS_SUCCEEDS, true);
        }
    }
}