/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.junit.jupiter.osgi;

import org.apache.sling.junit.jupiter.osgi.impl.BundleContextParameterResolver;
import org.apache.sling.junit.jupiter.osgi.impl.BundleParameterResolver;
import org.apache.sling.junit.jupiter.osgi.impl.ServiceParameterResolver;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * OSGi test annotation, for running unit tests within OSGi frameworks. The annotation supports
 * injecting {@link Bundle}, {@link BundleContext} and service instances in conjunction with the
 * {@link Service @Service} annotation. The annotation can be used on test classes or on individual
 * test methods. If used on test classes injection of constructor parameters is supported in addition
 * to injection of method parameters.
 * <br>
 * Note: the implementation relies on calling {@link FrameworkUtil#getBundle(Class)} with the test class
 * in order to gain access to the world of OSGi.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith({
        BundleParameterResolver.class,
        BundleContextParameterResolver.class,
        ServiceParameterResolver.class
})
@Inherited
public @interface OSGi {}
