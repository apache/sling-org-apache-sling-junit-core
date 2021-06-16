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

import org.osgi.framework.Filter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code @Service} annotation is to be used for test classes or methods annotated
 * with the {@link OSGi @OSGi} annotation. Note that tests using this annotation are
 * expected to be run within an OSGi environment. It is a repeatable annotation and can
 * be used to specify a number of different services using the {@link #value() service
 * type} and an optional {@link #filter() LDAP filter expression}.
 * <br>
 * Supported parameter types ar the service type itself for mandatory and unary references (1..1),
 * a {@code Collection} or {@code List} of the service type for optional and multiple references (0..n).
 * Currently no other cardinalities are supported.
 * <br>
 * When used on a test class, the specified services are made available for injection as parameters
 * to all of the test's methods.
 * <br>
 * When used on a test method, the specified services are made available for injection as parameters
 * to exactly that method.
 * <br>
 * When used on a method parameter, the specified service is made available for injection for exactly
 * that parameter. In this case, the {@link #value() service type} need not be specified, it can
 * be inferred from the parameter's type. However, it may still be useful to specify a filter expression.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Repeatable(Services.class)
@Inherited
public @interface Service {

    /**
     * The type of the service to be injected.
     * <br>
     * May be omitted if the annotation is used to annotate a method parameter, as the service type can
     * be inferred from the parameter's type.
     */
    Class<?> value() default Object.class;

    /**
     * An optional filter expression conforming to the LDAP filter syntax used in OSGi {@link Filter}s.
     */
    String filter() default "";
}

