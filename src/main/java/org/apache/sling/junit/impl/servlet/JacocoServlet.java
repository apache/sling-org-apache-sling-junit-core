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
import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jacoco.agent.rt.IAgent;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * This servlet exposes JaCoCo code coverage data over HTTP. See {@link #EXPLAIN} for usage information,
 * which is also available at /system/sling/jacoco after installing this servlet with the default settings.
 */
@SuppressWarnings("serial")
@Component(
        service = Servlet.class,
        immediate = true,
        configurationPolicy = ConfigurationPolicy.OPTIONAL,
        property = {
            Constants.SERVICE_DESCRIPTION+"=This servlet exposes JaCoCo (http://www.eclemma.org/jacoco) code coverage data.",
            "servlet.path=/system/sling/jacoco"
        }
    )
@Designate(ocd = JacocoServlet.Config.class, factory = false)
public class JacocoServlet extends HttpServlet {
    private static final String PARAM_SESSION_ID = ":sessionId";
    private static final String JMX_NAME = "org.jacoco:type=Runtime";
    
    public static final String EXPLAIN = 
            "This servlet exposes JaCoCo (http://www.eclemma.org/jacoco) code coverage data to HTTP clients by calling "
            + "JaCoCo's IAgent.getExecutionData(...).\n\n"
            + "POST requests reset the agent after returning the execution data, whereas GET "
            + "requests just return the data.\n"
            + "JaCoCo's session ID can be set via a " + PARAM_SESSION_ID + " request parameter.\n"
            + "The servlet returns 404 if the IAgent MBean is not available.\n\n"
            + "Please keep the JaCoCo security considerations in mind before enabling its agent: "
            + "JaCoCo's tcpserver and tcpclient modes and its JMX interface open ports that do "
            + "not require any authentication. See the JaCoCo documentation for details.\n\n"
            + "To activate JaCoCo on a Sling instance, start its JVM with the following option:\n\n"
            + "-javaagent:/path/to/jacocoagent.jar=dumponexit=false,jmx=true\n\n"
            + "The jacocoagent.jar file can be extracted from the appropriate maven artifact into the target directory "
            + "using 'mvn process-sources -P extractJacocoAgent' if you have this module's source code.\n\n"
            + "With this servlet installed, you can generate a JaCoCo coverage report "
            + "as follows (for example), from a folder that contains a pom.xml:\n\n"
            + "  curl -o target/jacoco.exec http://localhost:8080/system/sling/jacoco/exec\n"
            + "  mvn org.jacoco:jacoco-maven-plugin:report\n"
            + "  open target/site/jacoco/index.html\n\n"
            ;
    
    // Define OSGi R6 property configuration data type object
    @ObjectClassDefinition(
            name = "Apache Sling JUnit JaCoCo (http://www.eclemma.org/jacoco) Code Coverage Servlet",
            description = EXPLAIN
    )
    @interface Config {
        // The _'s in the method names (see below) are transformed to . when the
        // OSGi property names are generated.
        // Example: max_size -> max.size, user_name_default -> user.name.default
        @AttributeDefinition(
                name = "Servlet path for JacocoServlet",
                description = "Set to empty to disable",
                required = false
            )
        String servlet_path() default "/system/sling/jacoco";
    }
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Requests ending with this subpath send the jacoco data */
    public static final String EXEC_PATH = "/exec";

    /** Non-null if we are registered with HttpService */
    private String servletPath;

    @Reference
    private HttpService httpService;

    @Activate
    @Modified
    protected void activate(Config cfg) throws ServletException, NamespaceException {
        servletPath = cfg.servlet_path().isEmpty() ? null : cfg.servlet_path();
        if(servletPath == null) {
            log.info("Servlet path is null, not registering with HttpService");
        } else {
            httpService.registerServlet(servletPath, this, null, null);
            log.info("Servlet registered at {}", servletPath);
        }
    }

    @Deactivate
    protected void deactivate(Config cfg) throws ServletException, NamespaceException {
        if(servletPath != null) {
            httpService.unregister(servletPath);
            log.info("Servlet unregistered from path {}", servletPath);
        }
        servletPath = null;
    }

    /**
     * Get the jacoco execution data without resetting the agent
     * @param req the request
     * @param resp the response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if(EXEC_PATH.equals(req.getPathInfo())) {
            final IAgent agent = getAgent();
            if (agent == null) {
                final String msg = "The Jacoco agent MBean is not available\n\n";
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, msg + getUsageInfo());
            } else {
                sendJacocoData(req, resp, false);
                resp.setContentType("application/octet-stream");
            }
        } else {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(getUsageInfo());
            resp.getWriter().flush();
        }
    }

    /**
     * Get the jacoco execution data and reset the agent. Set the sessionId if :sessionId param exists.
     * @param req the request
     * @param resp the response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        sendJacocoData(req, resp, true);
    }
    
    private void sendJacocoData(HttpServletRequest req, HttpServletResponse resp, boolean resetAgent) throws IOException {
        final IAgent agent = getAgent();
        if (agent == null) {
            final String msg = "The Jacoco agent MBean is not available\n\n";
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, msg + getUsageInfo());
        } else {
            resp.setContentType("application/octet-stream");
            final String sessionId = req.getParameter(PARAM_SESSION_ID);
            log.info("Getting JaCoCo execution data, resetAgent={}", resetAgent);
            byte[] data = agent.getExecutionData(resetAgent);
            if(sessionId != null) {
                log.info("Setting JaCoCo sessionId={}", sessionId);
                agent.setSessionId(sessionId);
            }
            resp.getOutputStream().write(data);
            resp.getOutputStream().flush();
        }
    }
    
    private String getUsageInfo() {
        return new StringBuilder()
        .append("This is ")
        .append(getClass().getName())
        .append("\n\n")
        .append("To get the jacoco data, use " + servletPath + EXEC_PATH)
        .append("\n\n")
        .append(EXPLAIN)
        .toString();
    }

    /**
     * Lookup the jacoco agent mbean and return it if it exists. Return null otherwise.
     * @return jacoco agent MBean if registered, null if it is not registered
     */
    private IAgent getAgent() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName name = new ObjectName(JMX_NAME);
            if (mbs.isRegistered(name)) {
                return MBeanServerInvocationHandler.newProxyInstance(mbs, name, IAgent.class, false);
            }
        } catch (MalformedObjectNameException e) {
            log.error("[getAgent] there is a typo in the JMX_NAME constant", e);
        }

        return null;
    }
}
