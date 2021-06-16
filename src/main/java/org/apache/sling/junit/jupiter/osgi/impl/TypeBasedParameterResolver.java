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

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.apache.sling.junit.jupiter.osgi.impl.ReflectionHelper.parameterizedTypeForBaseClass;

/**
 * Abstract implementation of a {@link org.junit.jupiter.api.extension.ParameterResolver} that resolves
 * parameters of one given type. Implementations need only implement the abstract method
 * {@link #resolveParameter(ParameterContext, ExtensionContext, Type)}, the supported parameter type is
 * inferred from the classes type-argument {@code T}.
 *
 * @param <T>
 */
public abstract class TypeBasedParameterResolver<T> extends AbstractTypeBasedParameterResolver {

    private final Type supportedType;

    protected TypeBasedParameterResolver() {
        ParameterizedType parameterizedType = parameterizedTypeForBaseClass(TypeBasedParameterResolver.class, getClass());
        this.supportedType = parameterizedType.getActualTypeArguments()[0];
    }

    @Override
    protected boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext, Type resolvedParameterType) {
        return supportedType == resolvedParameterType;
    }

    @Override
    protected abstract T resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext, Type resolvedParameterType);
}
