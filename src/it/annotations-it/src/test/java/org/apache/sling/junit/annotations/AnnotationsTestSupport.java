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

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.testing.clients.osgi.OsgiConsoleClient;
import org.apache.sling.testing.paxexam.TestSupport;
import org.junit.Before;
import org.junit.ClassRule;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExamServer;
import org.ops4j.pax.exam.options.extra.VMOption;

import org.apache.sling.junit.it.impl.MyCoolServiceForTestingIT;
import org.apache.sling.junit.it.impl.MyLameServiceForTestingIT;
import static org.apache.sling.testing.paxexam.SlingOptions.slingServlets;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.apache.sling.testing.paxexam.SlingOptions.logback;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.net.URI;

public class AnnotationsTestSupport extends TestSupport {

    private final static int STARTUP_WAIT_SECONDS = 60;

    protected OsgiConsoleClient CLIENT;
    protected static int httpPort;

    @ClassRule
    public static PaxExamServer serverRule = new PaxExamServer();

    @Configuration
    public Option[] configuration() throws Exception {
        httpPort = findFreePort();
        final String vmOpt = System.getProperty("pax.vm.options");
        VMOption vmOption = null;
        if (StringUtils.isNotEmpty(vmOpt)) {
            vmOption = new VMOption(vmOpt);
        }

        final String jacocoOpt = System.getProperty("jacoco.command");
        VMOption jacocoCommand = null;
        if (StringUtils.isNotEmpty(jacocoOpt)) {
            jacocoCommand = new VMOption(jacocoOpt);
        }

        return options(
            when(vmOption != null).useOptions(vmOption),
            when(jacocoCommand != null).useOptions(jacocoCommand),

            // For some reason, Jetty starts first on port 8080 without this
            systemProperty("org.osgi.service.http.port").value(String.valueOf(httpPort)),

            serverBaseConfiguration(),
            slingServlets(),

            testBundle("bundle.filename"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.junit.core").versionAsInProject(),
            mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.webconsole.plugins.ds").version(versionResolver),

            // logging
            mavenBundle().groupId("ch.qos.logback").artifactId("logback-classic").versionAsInProject(),
            mavenBundle().groupId("ch.qos.logback").artifactId("logback-core").versionAsInProject(),
            mavenBundle().groupId("org.ops4j.pax.logging").artifactId("pax-logging-api").versionAsInProject(),
            mavenBundle().groupId("org.ops4j.pax.logging").artifactId("pax-logging-logback").versionAsInProject(),
            mavenBundle().groupId("jakarta.json").artifactId("jakarta.json-api").version("2.1.1"),
            mavenBundle().groupId("org.ow2.asm").artifactId("asm").version("9.7"),
            mavenBundle().groupId("org.ow2.asm").artifactId("asm-analysis").version("9.7"),
            mavenBundle().groupId("org.ow2.asm").artifactId("asm-commons").version("9.7"),
            mavenBundle().groupId("org.ow2.asm").artifactId("asm-util").version("9.7"),
            mavenBundle().groupId("org.ow2.asm").artifactId("asm-tree").version("9.7")
        );
    }

    @Before
    public void waitForSling() throws Exception {
        final URI url = new URI(String.format("http://localhost:%d", httpPort));
        CLIENT = new OsgiConsoleClient(url, "admin", "admin");

        final String [] waitFor = {
            MyCoolServiceForTestingIT.class.getName(),
            MyLameServiceForTestingIT.class.getName()
        };
        for(String clazz : waitFor) {
            CLIENT.waitComponentRegistered(clazz, 10 * 1000, 500);
        }
    }
}