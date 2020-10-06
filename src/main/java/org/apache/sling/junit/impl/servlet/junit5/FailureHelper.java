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
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.runner.notification.Failure;

import static org.apache.sling.junit.impl.servlet.junit5.DescriptionGenerator.toDescription;

public final class FailureHelper {

    @Nullable
    public static Failure convert(TestIdentifier testIdentifier, TestExecutionResult result) {
        return convert(testIdentifier, result.getThrowable().orElse(null));
    }

    @Nullable
    public static Failure convert(TestIdentifier testIdentifier, Throwable throwable) {
        return toDescription(testIdentifier)
                .map(d -> new Failure(d, throwable))
                .orElse(null);
    }

    @Nullable
    public static Failure convert(@NotNull TestExecutionSummary.Failure f) {
        return convert(f.getTestIdentifier(), f.getException());
    }

    private FailureHelper() {}
}
