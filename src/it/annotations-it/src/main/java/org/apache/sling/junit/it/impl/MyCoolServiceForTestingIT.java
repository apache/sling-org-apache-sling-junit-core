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
package org.apache.sling.junit.it.impl;

import org.apache.sling.junit.it.MyServiceIT;
import org.osgi.service.component.annotations.Component;

@Component(
        service = MyServiceIT.class
)
public class MyCoolServiceForTestingIT implements MyServiceIT {

    /**
     * @return Name of the Service which is used to discover the Service by the User
     **/
    @Override
    public String getName() {
        return "Cool Service";
    }

    /**
     * @return Description of the Service
     **/
    @Override
    public String getDescription() {
        return "My Cool Service is for testing @TestReference for in running JUnit tests within Sling";
    }
}