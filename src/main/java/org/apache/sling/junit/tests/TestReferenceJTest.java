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
package org.apache.sling.junit.tests;

import org.apache.sling.junit.annotations.SlingAnnotationsTestRunner;
import org.apache.sling.junit.annotations.TestReference;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;


@RunWith(SlingAnnotationsTestRunner.class)
public class TestReferenceJTest {

    @TestReference(filter="(component.name=org.apache.sling.junit.tests.impl.MyCoolServiceForTesting)")
    MyService myCoolService;

    @TestReference(filter="(component.name=org.apache.sling.junit.tests.impl.MyLameServiceForTesting)")
    MyService myLameService;

    @TestReference(filter="(component.name=org.apache.sling.junit.tests.impl.MyNonExistingServiceForTesting)")
    MyService myNullService;

    @Test
    public void exampleTestReference(){
        assertNotNull(myCoolService);
        assertNotNull(myLameService);
        assertNull(myNullService);
        assertEquals("Cool Service", myCoolService.getName());
        assertEquals("Lame Service", myLameService.getName());
    }
}
