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
package org.apache.sling.junit.impl;

import org.apache.sling.junit.TestSelector;
import org.apache.sling.junit.sampletests.JUnit4SlingJUnit;
import org.junit.Test;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class JUnit4TestExecutionStrategyTest {

    @Test
    public void testExecution() throws Exception {
        final Request request = Request.method(JUnit4SlingJUnit.class, "testSuccessful");
        final TestsManagerImpl testsManager = mock(TestsManagerImpl.class);
        when(testsManager.createTestRequest(any(), any(), any())).thenReturn(request);
        final JUnit4TestExecutionStrategy strategy = new JUnit4TestExecutionStrategy(testsManager);
        final RunListener runListener = mock(RunListener.class);
        strategy.execute(mock(TestSelector.class), runListener);
        verify(runListener, times(1))
                .testRunStarted(any());
        verify(runListener, times(1))
                .testSuiteStarted(argThat(desc -> Objects.equals(desc.getClassName(), JUnit4SlingJUnit.class.getName())));
        verify(runListener, times(1))
                .testStarted(argThat(desc -> Objects.equals(desc.getMethodName(), "testSuccessful")));
        verify(runListener, times(1))
                .testFinished(argThat(desc -> Objects.equals(desc.getMethodName(), "testSuccessful")));
        verify(runListener, times(1))
                .testSuiteFinished(argThat(desc -> Objects.equals(desc.getClassName(), JUnit4SlingJUnit.class.getName())));
        verify(runListener, times(1))
                .testRunFinished(argThat(Result::wasSuccessful));
        verifyNoMoreInteractions(runListener);
        
        strategy.close();
    }
}