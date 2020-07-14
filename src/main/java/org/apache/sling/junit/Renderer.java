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

import java.io.IOException;
import java.util.Collection;

import javax.servlet.http.HttpServletResponse;

import org.junit.runner.notification.RunListener;

/**
 * Renderer for our servlet output. Should not be used directly for rendering as it leads to non-reentrant renderers. Use only via {@link
 * RendererFactory} as {@link org.apache.sling.junit.impl.RendererSelectorImpl} does
 */
public interface Renderer {
    /**
     * True if this renderer applies to supplied request
     *
     * @param selector a test selector
     * @return {@code true} if this renderer applied to the provided selector, {@code false} otherwise
     **/
    boolean appliesTo(TestSelector selector);

    /**
     * Called first to setup rendering
     *
     * @param response  the http response
     * @param pageTitle the page title
     * @throws IOException if an error occurs
     **/
    void setup(HttpServletResponse response, String pageTitle) throws IOException;

    /**
     * Called once rendering is done
     */
    void cleanup();

    /**
     * Render a list of things
     *
     * @param role describes the role of the list, must be a valid CSS class value
     * @param data the list to render
     */
    void list(String role, Collection<String> data);

    /**
     * Render general information
     *
     * @param role describes the role of the list, must be a valid CSS class value
     * @param info the general information
     */
    void info(String role, String info);

    /**
     * Render a title of a specified hierarchical level
     *
     * @param level the level
     * @param title the title
     */
    void title(int level, String title);

    /**
     * Render a link to specified URL using specified HTTP method
     *
     * @param info   the info
     * @param url    the link to render
     * @param method the HTTP method to use
     */
    void link(String info, String url, String method);

    /**
     * Provide a RunListener for JUnit tests
     *
     * @return the run listener
     */
    RunListener getRunListener();

    /**
     * Return the extension that triggers this renderer
     *
     * @return the extension that triggers this renderer
     */
    String getExtension();
}
