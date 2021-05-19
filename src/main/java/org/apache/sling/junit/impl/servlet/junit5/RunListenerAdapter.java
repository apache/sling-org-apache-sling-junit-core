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

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import java.util.function.Consumer;

import static org.apache.sling.junit.impl.servlet.junit5.DescriptionGenerator.toDescription;

public class RunListenerAdapter implements TestExecutionListener {

    private final RunListener runListener;
    
    private final SummaryGeneratingListener summarizer;

    public RunListenerAdapter(RunListener runListener) {
        this.runListener = runListener;
        this.summarizer = new SummaryGeneratingListener();
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        summarizer.testPlanExecutionStarted(testPlan);
        try {
            runListener.testRunStarted(Description.createSuiteDescription("classes"));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        summarizer.testPlanExecutionFinished(testPlan);

        final TestExecutionSummary summary = summarizer.getSummary();

        final Result result = new ResultAdapter(summary);

        try {
            runListener.testRunFinished(result);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        summarizer.executionStarted(testIdentifier);
        if (testIdentifier.isTest()) {
            withDescription(testIdentifier, runListener::testStarted);
        } else {
            withDescription(testIdentifier, runListener::testSuiteStarted);
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        summarizer.executionSkipped(testIdentifier, reason);
        if (testIdentifier.isTest()) {
            withDescription(testIdentifier, runListener::testIgnored);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        summarizer.executionFinished(testIdentifier, testExecutionResult);
        if (testIdentifier.isTest()) {
            try {
                switch (testExecutionResult.getStatus()) {
                    case FAILED:
                        runListener.testFailure(FailureHelper.convert(testIdentifier, testExecutionResult.getThrowable().orElse(null)));
                        break;
                    case ABORTED:
                        runListener.testAssumptionFailure(FailureHelper.convert(testIdentifier, testExecutionResult.getThrowable().orElse(null)));
                        break;
                    case SUCCESSFUL:
                        break;
                }
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            withDescription(testIdentifier, runListener::testFinished);
        } else {
            withDescription(testIdentifier, runListener::testSuiteFinished);

        }
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
        summarizer.dynamicTestRegistered(testIdentifier);
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        summarizer.reportingEntryPublished(testIdentifier, entry);
    }

    private static void withDescription(TestIdentifier testIdentifier, ExceptionHandlingConsumer<Description, Exception> action) {
        toDescription(testIdentifier).ifPresent(action);
    }

    private interface ExceptionHandlingConsumer<S, E extends Exception> extends Consumer<S> {
        @Override
        default void accept(S s) {
            try {
                acceptAndThrow(s);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        void acceptAndThrow(S s) throws E;
    }
}
