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

/**
 * The cardinality of a service being injected is controlled via the type of the injected
 * parameter and additionally via the {@link Service @Service} annotation's {@code cardinality}
 * attribute.
 * <br>
 * The cardinality can be either {@code OPTIONAL} or {@code MANDATORY}. {@code OPTIONAL}
 * does not require the presence of a service, whereas {@code MANDATORY} requires at
 * least one service to be present, otherwise an exception is thrown.
 * <br>
 * The other aspect of cardinality, namely whether a single service or multiple services
 * should be injected, is controlled via the type of the annotated field. For single service
 * injection, the field's type is expected to be the type of the injected service. For multiple
 * service injection, the field's type is expected to be a {@link java.util.Collection} or
 * {@link java.util.List} with the type of the injected service as its generic type-argument.
 * <br>
 * Any further constraints, i.e. checking for an exact number or a range of services must be
 * done via assertions.
 *
 * @see Service#cardinality()
 */
public enum ServiceCardinality {
    /**
     * For unary service injection, {@code AUTO} defaults to {@code MANDATORY}.
     * Whereas for multiple service injection, {@code AUTO} defaults to {@code OPTIONAL}.
     */
    AUTO,

    /**
     * If no service is present, {@code null} is injected for unary service injection, and an empty
     * {@code List} is injected for multiple service injection.
     */
    OPTIONAL,

    /**
     * At least one service must be present, otherwise a {@code ParameterResolutionException} is thrown.
     */
    MANDATORY
}
