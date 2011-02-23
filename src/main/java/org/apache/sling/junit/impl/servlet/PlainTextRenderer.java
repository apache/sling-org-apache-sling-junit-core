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
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.junit.Renderer;
import org.apache.sling.junit.RequestParser;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

@Component(immediate=false)
@Service
/** Plain text renderer */
public class PlainTextRenderer extends RunListener implements Renderer {
    private PrintWriter output;
    
    /** @inheritDoc */
    public boolean appliesTo(RequestParser p) {
        return "txt".equals(p.getExtension());
    }

    /** @inheritDoc */
    public void setup(HttpServletResponse response, String pageTitle) throws IOException, UnsupportedEncodingException {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        output = response.getWriter();
        title(1, pageTitle);
    }
    
    /** @inheritDoc */
    public void cleanup() {
    }

    /** @inheritDoc */
    public void info(String cssClass, String str) {
        output.println(str);
    }
    
    /** @inheritDoc */
    public void list(String cssClass, List<String> data) {
        for(String str : data) {
            output.println(str);
        }
    }
    
    /** @inheritDoc */
    public void title(int level, String title) {
        output.print(title);
        output.println(" ****");
    }
    
    /** @inheritDoc */
    public RunListener getRunListener() {
        return this;
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        super.testFailure(failure);
        output.println("FAILURE " + failure);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        super.testFinished(description);
        output.println("FINISHED " + description);
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        super.testIgnored(description);
        output.println("IGNORED " + description);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        super.testRunFinished(result);
        output.println("TEST RUN FINISHED: "
                + "tests:" + result.getRunCount()
                + ", failures:" + result.getFailureCount()
                + ", ignored:" + result.getIgnoreCount()
        );
    }

    @Override
    public void testRunStarted(Description description)
            throws Exception {
        super.testRunStarted(description);
    }

    @Override
    public void testStarted(Description description) throws Exception {
        super.testStarted(description);
    }
}