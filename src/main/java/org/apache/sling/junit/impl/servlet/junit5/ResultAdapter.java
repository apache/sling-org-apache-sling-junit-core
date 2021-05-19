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

import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ResultAdapter extends Result {
    
    private final transient TestExecutionSummary summary;

    public ResultAdapter(TestExecutionSummary summary) {
        this.summary = summary;
    }

    @Override
    public int getRunCount() {
        return (int) summary.getTestsStartedCount();
    }

    @Override
    public int getFailureCount() {
        return (int) summary.getTestsFailedCount();
    }

    @Override
    public long getRunTime() {
        return summary.getTimeFinished() - summary.getTimeStarted();
    }

    @Override
    public List<Failure> getFailures() {
        return summary.getFailures().stream()
                .map(FailureHelper::convert)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public int getIgnoreCount() {
        return (int) summary.getTestsSkippedCount();
    }

    @Override
    public int getAssumptionFailureCount() {
        return (int) summary.getTestsAbortedCount();
    }

    @Override
    public boolean wasSuccessful() {
        return summary.getTestsFailedCount() == 0;
    }

    @Override
    public RunListener createListener() {
        throw new UnsupportedOperationException("createListener is not implemented");
    }
}
