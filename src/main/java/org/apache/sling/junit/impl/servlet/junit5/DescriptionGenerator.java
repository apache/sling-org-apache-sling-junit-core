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
package org.apache.sling.junit.impl.servlet.junit5;

import org.jetbrains.annotations.NotNull;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.runner.Description;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public enum DescriptionGenerator {

    CLASS_SOURCE(ClassSource.class, src -> Description.createSuiteDescription(src.getJavaClass())),
    
    METHOD_SOURCE(MethodSource.class, src -> Description.createTestDescription(src.getClassName(), src.getMethodName()))

    ;

    private final Class<? extends TestSource> clazz;

    private final Function<? super TestSource, Description> generator;

    <T extends TestSource> DescriptionGenerator(Class<T> clazz, Function<? super T, Description> generator) {
        this.clazz = clazz;
        this.generator = (Function<? super TestSource, Description>) generator;
    }

    @NotNull
    public static Optional<Description> toDescription(TestIdentifier testIdentifier) {
        return testIdentifier.getSource().map(DescriptionGenerator::createDescription);
    }

    static Description createDescription(TestSource testSource) {
        if (testSource != null) {
            return Arrays.stream(values())
                    .filter(v -> v.clazz.isInstance(testSource))
                    .map(v -> v.generator.apply(testSource))
                    .findFirst()
                    .orElse(null);
        }
        ;
        return null;
    }
}
