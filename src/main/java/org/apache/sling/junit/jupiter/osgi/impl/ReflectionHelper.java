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

import org.apache.commons.lang3.reflect.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.platform.commons.util.Preconditions;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class used for resolving type-arguments to concrete types.
 */
public class ReflectionHelper {

    public static Map<TypeVariable<?>, Type> determineTypeArguments(@NotNull Class<?> clazz) {
        final Map<TypeVariable<?>, Type> typeVariableTypeMap = new HashMap<>();
        determineTypeArguments(clazz, typeVariableTypeMap);
        return Collections.unmodifiableMap(typeVariableTypeMap);
    }

    private static void determineTypeArguments(Class<?> clazz, Map<TypeVariable<?>, Type> typeVariableTypeMap) {
        final Type genericSuperclass = clazz.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            final ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
            typeVariableTypeMap.putAll(TypeUtils.determineTypeArguments(clazz, parameterizedType));

            final Type rawType = parameterizedType.getRawType();
            if (!(rawType instanceof Class<?>)) {
                throw new UnsupportedOperationException("Expected Class#getGenericSuperclass() to return an object of type Class<?>");
            }
            determineTypeArguments((Class<?>) rawType, typeVariableTypeMap);
        } else if (genericSuperclass instanceof Class<?>) {
            if (genericSuperclass == Object.class && ((Class<?>) genericSuperclass).isArray()) {
                // TODO: handle array types, see docs for Class#getGenericSuperclass()
                throw new UnsupportedOperationException("Unsupported case where genericSuperclass == Object.class");
            } else {
                determineTypeArguments((Class<?>) genericSuperclass, typeVariableTypeMap);
            }
        } else if (genericSuperclass == null) {
            final Type[] genericInterfaces = clazz.getGenericInterfaces();
            for (Type genericInterface : genericInterfaces) {
                if (genericInterface instanceof ParameterizedType) {
                    final ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                    typeVariableTypeMap.putAll(TypeUtils.determineTypeArguments(clazz, parameterizedType));
                }
            }
        } else {
            throw new UnsupportedOperationException("Expected Class#getGenericSuperclass() to return null or an object of type Class<?> or ParameterizedType");
        }
    }


    @NotNull
    @SuppressWarnings("java:S2637")
    public static ParameterizedType parameterizedTypeForBaseClass(@NotNull Class<?> baseClass, @NotNull Class<?> clazz) {
        ParameterizedType parameterizedType = findParameterizedTypeForBaseClass(baseClass, clazz);
        Preconditions.notNull(parameterizedType,
                () -> String.format(
                        "Failed to discover type supported by %s; "
                                + "potentially caused by lacking parameterized type in class declaration.",
                        clazz.getName()));
        return parameterizedType;
    }

    private static ParameterizedType findParameterizedTypeForBaseClass(Class<?> baseClass, Class<?> clazz) {
        Class<?> superclass = clazz.getSuperclass();

        // Abort?
        if (superclass == null || superclass == Object.class) {
            return null;
        }

        Type genericSuperclass = clazz.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) genericSuperclass).getRawType();
            if (rawType == baseClass) {
                return ((ParameterizedType) genericSuperclass);
            }
        }
        return findParameterizedTypeForBaseClass(baseClass, superclass);
    }
}
