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
package org.apache.sling.junit.annotations;

import org.apache.http.entity.StringEntity;
import org.apache.sling.testing.clients.ClientException;
import org.apache.sling.testing.clients.SlingHttpResponse;
import org.junit.Test;
import java.io.UnsupportedEncodingException;
import java.util.Collections;

import static org.apache.sling.testing.paxexam.SlingOptions.logback;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.when;


public class ReferenceIT extends AnnotationsTestSupport {
    @Test
    public void testReferenceJITest() throws ClientException, UnsupportedEncodingException {
        final SlingHttpResponse response = CLIENT.doPost("/system/sling/junit/org.apache.sling.junit.it.TestReferenceJITest.html",
                new StringEntity("some text"),
                Collections.emptyList(),
                200);
        final String [] toCheck = {
            "TEST RUN FINISHED",
            "failures:0",
            "ignored:0",
            "tests:4",
            "testTargetReferences",
            "testOsgiReferences",
        };
        for(String check : toCheck) {
            response.checkContentContains(check);
        }
    }
}
