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
package org.apache.sling.junit.jupiter.osgi.utils;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.support.HierarchyTraversalMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotatedMethods;

@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(MetaTestSourceProvider.class)
@ParameterizedTest(name = "{0}#{2}")
public @interface MetaTest {
    /**
     * Meta test classes, i.e. classes with methods directly or indirectly
     * annotated as {@code @Testable}.
     */
    Class<?>[] value();

    /**
     * List of test method names that should be called on the test class(es).
     * By default all {@code @Testable} methods are called.
     */
    String[] methods() default {};
}

class MetaTestSourceProvider implements ArgumentsProvider, AnnotationConsumer<MetaTest> {

    private Class<?>[] testClasses;

    private List<String> allowedMethodNames;

    @Override
    public void accept(MetaTest metaTest) {
        testClasses = metaTest.value();
        allowedMethodNames = asList(metaTest.methods());
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
        return Stream.of(testClasses).flatMap(this::permutate);
    }

    @NotNull
    private Stream<Arguments> permutate(Class<?> cls) {
        return findAnnotatedMethods(cls, Testable.class, HierarchyTraversalMode.BOTTOM_UP).stream()
                .filter(method -> allowedMethodNames.isEmpty() || allowedMethodNames.contains(method.getName()))
                .map(toArguments(cls));
    }

    @NotNull
    private static Function<Method, Arguments> toArguments(Class<?> cls) {
        return method -> Arguments.of(cls.getSimpleName(), cls, method.getName());
    }
}
