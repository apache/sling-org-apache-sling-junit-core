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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.junit.Renderer;
import org.apache.sling.junit.RendererSelector;
import org.apache.sling.junit.RequestParser;
import org.apache.sling.junit.TestSelector;
import org.apache.sling.junit.TestsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServletProcessor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String CSS = "junit.css";
    public static final String FORCE_RELOAD_PARAM = "forceReload";

    private final TestsManager testsManager;

    private final RendererSelector rendererSelector;

    public ServletProcessor(final TestsManager testsManager,
            final RendererSelector rendererSelector) {
        this.testsManager = testsManager;
        this.rendererSelector = rendererSelector;
    }

    private void sendCss(HttpServletResponse response) throws IOException {
        final InputStream str = getClass().getResourceAsStream("/" + CSS);
        if(str == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, CSS);
        } else {
            response.setContentType("text/css");
            final OutputStream out = response.getOutputStream();
            final byte[] buffer = new byte[16384];
            int count = 0;
            while( (count = str.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            out.flush();
        }
    }

    private void logForceReloadOptionDeprecation(HttpServletRequest request) {
        final String forceReloadParam = request.getParameter(FORCE_RELOAD_PARAM);
        if (forceReloadParam != null) {
            log.info("{} option is no longer necessary and its use is therefore deprecated", FORCE_RELOAD_PARAM);
        }
    }

    /** GET request lists available tests */
    public void doGet(final HttpServletRequest request, final HttpServletResponse response, final String servletPath)
    throws ServletException, IOException {
        logForceReloadOptionDeprecation(request);

        // Redirect to / if called without it, and serve CSS if requested
        {
            final String pi = request.getPathInfo();
            if(pi == null) {
                response.sendRedirect(request.getContextPath() + servletPath + "/");
            } else if(pi.endsWith(CSS)) {
                sendCss(response);
                return;
            }
        }

        final TestSelector selector = getTestSelector(request);
        final Collection<String> testNames = testsManager.getTestNames(selector);

        // 404 if no tests found
        if(testNames.isEmpty()) {
            final String msg = 
            "WARNING: no test classes found for selector " + selector
            + ", check the requirements of the active " +
            "TestsProvider services for how to supply tests.";
            response.sendError(HttpServletResponse.SC_NOT_FOUND, msg);
            return;
        }
        
        final Renderer renderer = rendererSelector.getRenderer(selector);
        if(renderer == null) {
            throw new ServletException("No Renderer found for " + selector);
        }
        log.debug("GET request: {}", selector);

        renderer.setup(response, getClass().getSimpleName());
        renderer.info("info", "Test selector: " + selector);
        try {
            testsManager.listTests(testNames, renderer);
            final String postPath = getTestExecutionPath(request, selector, renderer.getExtension());
            renderer.link("Execute these tests", postPath, "POST");
        } catch(Exception e) {
            throw new ServletException(e);
        }
        renderer.cleanup();
    }

    /** POST request executes tests */
    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        logForceReloadOptionDeprecation(request);

        final TestSelector selector = getTestSelector(request);
        log.info("POST request, executing tests: {}", selector);

        final Renderer renderer = rendererSelector.getRenderer(selector);
        if(renderer == null) {
            throw new ServletException("No Renderer found for " + selector);
        }
        renderer.setup(response, getClass().getSimpleName());

        try {
            testsManager.executeTests(renderer, selector);
        } catch(TestsManager.NoTestCasesFoundException e) {
            response.sendError(
                    HttpServletResponse.SC_NOT_FOUND,
                    "No tests found for " + selector);
            return;
        } catch (Exception e) {
            throw new ServletException(e);
        }

        renderer.cleanup();
    }

    /** Return a TestSelector for supplied request */
    private TestSelector getTestSelector(HttpServletRequest request) {
        return new RequestParser(getTestSelectionPath(request));
    }

    /** Return subpath to use for selecting tests */
    protected String getTestSelectionPath(HttpServletRequest request) {
        return request.getPathInfo();
    }

    /** Return path to which to POST to execute specified test */
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
}