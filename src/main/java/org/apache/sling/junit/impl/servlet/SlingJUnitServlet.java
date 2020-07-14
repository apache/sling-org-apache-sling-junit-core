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

import org.apache.sling.junit.RendererSelector;
import org.apache.sling.junit.TestSelector;
import org.apache.sling.junit.TestsManager;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.NamespaceException;

/** Alternate entry point for testing, that uses
 *  the Sling request/response cycle.
 *
 *  Can be used to run tests in an environment that
 *  more closely matches how Sling components run,
 *  but requires Sling - so we keep both servlets
 *  in order for this module to be reusable without
 *  Sling.
 *
 *  This servlet is registered with a specific resource
 *  type, to call it a Sling resource must be created
 *  with this resource type.
 */
@SuppressWarnings("serial")
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "sling/junit/testing",
        extensions = "junit",
        methods = {"GET", "POST"}
)
public class SlingJUnitServlet extends HttpServlet {

    public static final String EXTENSION = ".junit";

    @Reference
    private TestsManager testsManager;

    @Reference
    private RendererSelector rendererSelector;

    private volatile ServletProcessor processor;

    @Activate
    protected void activate(final ComponentContext ctx) {
        this.processor = new ServletProcessor(testsManager, rendererSelector) {
            @Override
            protected String getTestSelectionPath(HttpServletRequest request) {
                // PathInfo contains the path to our resource, followed
                // by the .junit extension - cut up to that
                String result = request.getPathInfo();
                final int pos = result.indexOf(EXTENSION);
                if(pos >= 0) {
                    result = result.substring(pos + EXTENSION.length());
                }
                return result;
            }

            /** Return path to which to POST to execute specified test */
            @Override
            protected String getTestExecutionPath(HttpServletRequest request, TestSelector selector, String extension) {
                String selectedTestMethodName = selector.getSelectedTestMethodName();
                String methodStr = "";
                if (selectedTestMethodName != null && !"".equals(selectedTestMethodName)) {
                    methodStr = "/" + selectedTestMethodName;
                }
                return  "./"
                + selector.getTestSelectorString()
                + methodStr
                + "."
                + extension;
            }
        };
    }

    @Deactivate
    protected void deactivate(ComponentContext ctx) {
        this.processor = null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        this.processor.doGet(req, resp, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        this.processor.doPost(req, resp);
    }
    }
