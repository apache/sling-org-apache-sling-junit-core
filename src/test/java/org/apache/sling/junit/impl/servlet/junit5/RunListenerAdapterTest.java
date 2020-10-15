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

import org.apache.sling.junit.sampletests.JUnit4SlingJUnit;
import org.junit.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RunListenerAdapterTest {

    @Test
    public void testLifecycle() throws Exception {
        RunListener runListener = mock(RunListener.class);
        RunListenerAdapter runListenerAdapter = new RunListenerAdapter(runListener);

        // start test run
        TestPlan testPlan = mock(TestPlan.class);
        runListenerAdapter.testPlanExecutionStarted(testPlan);
        verify(runListener, times(1))
                .testRunStarted(argThat(desc -> desc.getClassName().equals("classes")));

        // start test class (which is a suite)
        runListenerAdapter.executionStarted(getTestIdentifierForClass());
        verify(runListener, times(1))
                .testSuiteStarted(argThat(desc -> desc.getClassName().equals(JUnit4SlingJUnit.class.getName())));

        // start test method (success)
        runListenerAdapter.executionStarted(getTestIdentifierForMethod("testSuccessful"));
        verify(runListener, times(1)).testStarted(
                argThat(desc -> desc.isTest() && Objects.equals(desc.getMethodName(), "testSuccessful")));
        runListenerAdapter.executionFinished(getTestIdentifierForMethod("testSuccessful"), getTestExecutionResult(TestExecutionResult.Status.SUCCESSFUL));
        verify(runListener, times(1)).testFinished(
                argThat(desc -> desc.isTest() && Objects.equals(desc.getMethodName(), "testSuccessful")));

        // finish test method (success)
        
        // start test method (failure)
        runListenerAdapter.executionStarted(getTestIdentifierForMethod("testFailed"));
        verify(runListener, times(1)).testStarted(
                argThat(desc -> desc.isTest() && Objects.equals(desc.getMethodName(), "testFailed")));
        runListenerAdapter.executionFinished(getTestIdentifierForMethod("testFailed"), getTestExecutionResult(TestExecutionResult.Status.FAILED));
        verify(runListener, times(1)).testFailure(any(Failure.class));
        verify(runListener, times(1)).testFinished(
                argThat(desc -> desc.isTest() && Objects.equals(desc.getMethodName(), "testFailed")));

        // start test method (skipped)
        runListenerAdapter.executionSkipped(getTestIdentifierForMethod("testSkipped"), null);
        verify(runListener, times(1)).testIgnored(
                argThat(desc -> desc.isTest() && Objects.equals(desc.getMethodName(), "testSkipped")));

        runListenerAdapter.executionFinished(getTestIdentifierForClass(), getTestExecutionResult(TestExecutionResult.Status.FAILED));
        verify(runListener, times(1)).testSuiteFinished(
                argThat(desc -> Objects.equals(desc.getClassName(), JUnit4SlingJUnit.class.getName())));

        runListenerAdapter.testPlanExecutionFinished(testPlan);
        verify(runListener, times(1)).testRunFinished(
                argThat(r ->
                        !r.wasSuccessful()
                        && r.getRunCount() == 2
                        && r.getFailureCount() == 1
                        && r.getIgnoreCount() == 1
                        && r.getAssumptionFailureCount() == 0
                        && r.getRunTime() > 0));
        
        verifyNoMoreInteractions(runListener);
    }

    public TestExecutionResult getTestExecutionResult(TestExecutionResult.Status status) {
        final TestExecutionResult result = mock(TestExecutionResult.class);
        when(result.getStatus()).thenReturn(status);
        return result;
    }

    public TestIdentifier getTestIdentifierForClass() {
        TestDescriptor testDescriptor = mock(TestDescriptor.class);
        when(testDescriptor.getUniqueId()).thenReturn(mock(UniqueId.class));
        when(testDescriptor.getType()).thenReturn(TestDescriptor.Type.CONTAINER);
        when(testDescriptor.isTest()).thenReturn(false);
        when(testDescriptor.getSource()).thenReturn(Optional.of(ClassSource.from(JUnit4SlingJUnit.class)));
        when(testDescriptor.getChildren()).thenAnswer(m -> new LinkedHashSet<>(asList(
                getTestIdentifierForMethod("testSuccessful"),
                getTestIdentifierForMethod("testFailed"),
                getTestIdentifierForMethod("testSkipped"))));
        return TestIdentifier.from(testDescriptor);
    }

    public TestIdentifier getTestIdentifierForMethod(String methodName) {
        TestDescriptor testDescriptor = mock(TestDescriptor.class);
        when(testDescriptor.getUniqueId()).thenReturn(mock(UniqueId.class));
        when(testDescriptor.getType()).thenReturn(TestDescriptor.Type.TEST);
        when(testDescriptor.isTest()).thenReturn(true);
        when(testDescriptor.getSource()).thenReturn(Optional.of(
                MethodSource.from(JUnit4SlingJUnit.class.getName(), methodName)));
        return TestIdentifier.from(testDescriptor);
    }

}