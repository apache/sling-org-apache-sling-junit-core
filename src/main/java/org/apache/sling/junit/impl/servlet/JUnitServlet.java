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
package org.apache.sling.junit.impl.servlet;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.apache.sling.junit.RendererSelector;
import org.apache.sling.junit.TestsManager;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple test runner servlet */
@SuppressWarnings("serial")
@Component(
        service = Servlet.class,
        immediate = true,
        configurationPolicy = ConfigurationPolicy.OPTIONAL,
        property = {
            Constants.SERVICE_DESCRIPTION+"=Service that gives access to JUnit test classes",
            "servlet.path=/system/sling/junit",
        }
)
@Designate(ocd = JUnitServlet.Config.class, factory = false)
public class JUnitServlet extends HttpServlet {

    // Define OSGi R6 property configuration data type object
    @ObjectClassDefinition(
            name = "Apache Sling JUnit Servlet",
            description = "A Sling JUnit Servlet that runs JUnit tests found in bundles."
    )
    @interface Config {
        // The _'s in the method names (see below) are transformed to . when the
        // OSGi property names are generated.
        // Example: max_size -> max.size, user_name_default -> user.name.default
        @AttributeDefinition(
                name = "Servlet path for JUnitServlet",
                description = "Set to empty to disable this Servlet",
                required = false
            )
        String servlet_path() default "/system/sling/junit";
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Non-null if we are registered with HttpService */
    private String servletPath;

    @Reference
    private TestsManager testsManager;

    @Reference
    private HttpService httpService;

    @Reference
    private RendererSelector rendererSelector;

    private volatile ServletProcessor processor;

    @Activate
    @Modified
    protected void activate(final ComponentContext ctx, Config cfg) throws ServletException, NamespaceException {
        servletPath = cfg.servlet_path().isEmpty() ? null : cfg.servlet_path();
        if(servletPath == null) {
            log.info("Servlet path is null, not registering with HttpService");
        } else {
            httpService.registerServlet(servletPath, this, null, null);
            this.processor = new ServletProcessor(testsManager, rendererSelector);
            log.info("Servlet registered at {}", servletPath);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext ctx) throws ServletException, NamespaceException {
        if(servletPath != null) {
            httpService.unregister(servletPath);
            log.info("Servlet unregistered from path {}", servletPath);
        }
        servletPath = null;
        this.processor = null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        this.processor.doGet(req, resp, this.servletPath);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        this.processor.doPost(req, resp);
    }


}