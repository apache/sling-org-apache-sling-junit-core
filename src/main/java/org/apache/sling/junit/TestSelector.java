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

/**
 * Used by the {@link TestsManager} to select which tests to run
 */
public interface TestSelector {
    /**
     * If true, testName will be selected
     *
     * @param testName the name of the test
     * @return {@code true} if the test will be selected, {@code false} otherwise
     */
    boolean acceptTestName(String testName);

    /**
     * Returns the name of the selected test.
     *
     * @return the name of the selected test
     */
    String getSelectedTestMethodName();

    /**
     * Return the String used to select tests
     *
     * @return the string used to select the tests
     */
    String getTestSelectorString();

    /**
     * Return the extension used to render results
     *
     * @return the extension used to render the results
     */
    String getExtension();
}
