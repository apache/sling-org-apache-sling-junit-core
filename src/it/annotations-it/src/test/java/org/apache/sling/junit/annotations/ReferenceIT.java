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
import org.apache.http.entity.StringEntity;
import org.apache.sling.junit.it.impl.MyLameServiceForTestingIT;
import org.apache.sling.testing.clients.ClientException;
import org.apache.sling.testing.clients.SlingHttpResponse;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.apache.sling.testing.clients.osgi.OsgiConsoleClient;

import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Collections;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExamServer;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.apache.sling.testing.paxexam.SlingOptions;
import static org.apache.sling.testing.paxexam.SlingOptions.logback;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.when;
import org.apache.sling.testing.paxexam.TestSupport;


public class ReferenceIT extends TestSupport {
    protected static int httpPort;
    protected static OsgiConsoleClient CLIENT;
    private final static int STARTUP_WAIT_SECONDS = 30;

    @ClassRule
    public static PaxExamServer serverRule = new PaxExamServer() {
        @Override
        protected void before() throws Exception {
            // Use a different port for each OSGi framework instance
            // that's started - they can overlap if the previous one
            // is not fully stopped when the next one starts.
            setHttpPort();
            super.before();
        }
    };

    @Configuration
    public Option[] configuration() throws Exception {

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

        final String workingDirectory = workingDirectory();

        // Need recent commons.johnzon for the osgi.contract=JavaJSONP capability
        SlingOptions.versionResolver.setVersion("org.apache.sling", "org.apache.sling.commons.johnzon", "1.2.6");

        return composite(
                // TODO not sure why the below list of bundles is different from
                // running tests with PaxExam.class (SLING-9929)
                //super.baseConfiguration(),

                when(vmOption != null).useOptions(vmOption),
                when(jacocoCommand != null).useOptions(jacocoCommand),

                // For some reason, Jetty starts first on port 8080 without this
                systemProperty("org.osgi.service.http.port").value(String.valueOf(httpPort)),

                slingQuickstartOakTar(workingDirectory, httpPort),

                testBundle("bundle.filename"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.junit.core").versionAsInProject(),

                logback(),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.log").version("1.2.4"),
                mavenBundle().groupId("log4j").artifactId("log4j").version("1.2.17"),
                mavenBundle().groupId("org.apache.aries.spifly").artifactId("org.apache.aries.spifly.dynamic.framework.extension").version("1.3.2"),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.webconsole.plugins.ds").version("2.1.0")
        ).getOptions();
    }

    @BeforeClass
    public static void waitForSling() throws Exception {
        final URI url = new URI(String.format("http://localhost:%d", httpPort));
        CLIENT = new OsgiConsoleClient(url, "admin", "admin");
        
        CLIENT.waitExists("/", STARTUP_WAIT_SECONDS * 1000, 500);

        CLIENT.waitComponentRegistered(MyLameServiceForTestingIT.class.getName(), 10 * 1000, 500);

        // Verify stable status for a bit
        for(int i=0; i < 10 ; i++) {
            CLIENT.waitComponentRegistered(MyLameServiceForTestingIT.class.getName(), 1000, 100);
            Thread.sleep(100);
        }
    }

    static void setHttpPort() {
        try {
            final ServerSocket serverSocket = new ServerSocket(0);
            httpPort = serverSocket.getLocalPort();
            serverSocket.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testReferenceJITest() throws ClientException, UnsupportedEncodingException {
        SlingHttpResponse response = CLIENT.doPost("/system/sling/junit/org.apache.sling.junit.it.TestReferenceJITest.html",
                new StringEntity("some text"),
                Collections.emptyList(),
                200);
        response.checkContentContains("TEST RUN FINISHED");
        response.checkContentContains("failures:0");
        response.checkContentContains("ignored:0");
        response.checkContentContains("tests:4");
    }
}