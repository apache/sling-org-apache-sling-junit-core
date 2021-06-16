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
package org.apache.sling.junit.jupiter.osgi.impl;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

import static org.apache.sling.junit.jupiter.osgi.impl.ReflectionHelper.determineTypeArguments;

/**
 * Abstract {@link ParameterResolver} class that resolves any type-arguments in the parameter's type
 * to their actual type and provides this {@code resolvedParameterType} to the abstract methods
 * {@link #supportsParameter(ParameterContext, ExtensionContext, Type)} and
 * {@link #resolveParameter(ParameterContext, ExtensionContext, Type)}.
 */
public abstract class AbstractTypeBasedParameterResolver implements ParameterResolver {

    protected abstract boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext, Type resolvedParameterType);

    protected abstract Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext, Type resolvedParameterType);

    @Override
    public final boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        final Type type = getTypeOfParameter(parameterContext, extensionContext);
        return supportsParameter(parameterContext, extensionContext, type);
    }

    @Override
    public final Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        final Type typeOfParameter = getTypeOfParameter(parameterContext, extensionContext);
        return resolveParameter(parameterContext, extensionContext, typeOfParameter);
    }

    @NotNull
    private static Type getTypeOfParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Type type = parameterContext.getParameter().getParameterizedType();
        if (type instanceof TypeVariable) {
            final Map<TypeVariable<?>, Type> typeVariableTypeMap = determineTypeArguments(extensionContext.getRequiredTestClass());
            return typeVariableTypeMap.getOrDefault((TypeVariable<?>) type, type);
        } else {
            return type;
        }
    }
}
