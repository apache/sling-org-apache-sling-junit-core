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
package org.apache.sling.junit.sampletests;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pseudo test-case class executed tests in {@link org.apache.sling.junit.impl.TestsManagerImplTest}.
 *
 * Name does not match normal JUnit patterns in order NOT to be included in the normal build's tests.
 */
public class JUnit4SlingJUnit {

    @Test
    public void testSuccessful() {
        assertTrue(true);
    }

    @Test @Ignore("skipped for testing")
    public void testSkipped() {
        assertTrue(true);
    }

    @Test
    public void testFailed() {
        fail();
    }
}
