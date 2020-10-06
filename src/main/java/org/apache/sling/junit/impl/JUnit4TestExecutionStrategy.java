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

import org.apache.sling.junit.Renderer;
import org.apache.sling.junit.SlingTestContextProvider;
import org.apache.sling.junit.TestSelector;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class JUnit4TestExecutionStrategy implements TestExecutionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(JUnit4TestExecutionStrategy.class);

    private final TestsManagerImpl testsManager;

    public JUnit4TestExecutionStrategy(TestsManagerImpl testsManager) {
        this.testsManager = testsManager;
    }

    @Override
    public void execute(Renderer renderer, Collection<String> testNames, TestSelector selector) throws Exception {
        final JUnitCore junit = new JUnitCore();
        junit.addListener(new TestContextRunListenerWrapper(renderer.getRunListener()));
        for(String className : testNames) {
            renderer.title(3, className);

            // If we have a test context, clear its output metadata
            if(SlingTestContextProvider.hasContext()) {
                SlingTestContextProvider.getContext().output().clear();
            }

            final String testMethodName = selector == null ? null : selector.getSelectedTestMethodName();
            if(testMethodName != null && testMethodName.length() > 0) {
                LOG.debug("Running test method {} from test class {}", testMethodName, className);
                junit.run(Request.method(testsManager.getTestClass(className), testMethodName));
            } else {
                LOG.debug("Running test class {}", className);
                junit.run(testsManager.getTestClass(className));
            }
        }
    }

    @Override
    public void close() {
        // nothing to do
    }
}
