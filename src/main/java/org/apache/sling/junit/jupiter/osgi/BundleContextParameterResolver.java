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

import org.apache.sling.junit.jupiter.osgi.impl.TypeBasedParameterResolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.lang.reflect.Type;
import java.util.Optional;

class BundleContextParameterResolver extends TypeBasedParameterResolver<BundleContext> {
    @Override
    protected BundleContext resolveParameter(@NotNull ParameterContext parameterContext, @NotNull ExtensionContext extensionContext, @NotNull Type resolvedParameterType) {
        return Optional.ofNullable(FrameworkUtil.getBundle(extensionContext.getRequiredTestClass()))
                .map(Bundle::getBundleContext)
                .orElseThrow(() -> new ParameterResolutionException("@OSGi and @Service annotations can only be used with tests running in an OSGi environment"));
    }
}
