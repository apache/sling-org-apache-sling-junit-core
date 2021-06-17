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

import org.apache.sling.junit.impl.servlet.junit5.JUnitPlatformHelper;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.opentest4j.MultipleFailuresError;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.apache.sling.junit.jupiter.osgi.impl.ReflectionHelper.parameterizedTypeForBaseClass;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotatedMethods;

/**
 * This test executes pseudo test classes using the {@link JupiterTestEngine} in order to
 * verify the correct injection of parameters via the {@code @OSGi} and  {@code @Service}
 * annotations.
 * <br>
 * In order to achieve this, test methods from the pseudo test classes ({@code PseudoTest*}
 * are executed and the test summary evaluated to verify expectations. The additional indirection
 * can be a little tricky, but is necessary to test the annotations work correctly. Particularly
 * when testing failure scenarios, where the failure of a pseudo test is required to pass the actual
 * test.
 */
@ExtendWith(OsgiContextExtension.class)
public class OSGiAnnotationTest {

    private static final JupiterTestEngine JUPITER_TEST_ENGINE = new JupiterTestEngine();

    OsgiContext osgiContext = new OsgiContext();

    @SuppressWarnings("unused") // provides parameters
    static Stream<Arguments> frameworkObjectsInjectionTests() {
        return Stream.of(PseudoTestBundleInjection.class, PseudoTestBundleContextInjection.class)
                .flatMap(OSGiAnnotationTest::allTestMethods);
    }

    @ParameterizedTest(name = "{0}#{2}")
    @MethodSource("frameworkObjectsInjectionTests")
    void injectFrameworkObjects(String name, Class<?> testClass, String testMethodName) {
        withMockedFrameworkUtil(() -> {
            assertNoFailures(testClass, testMethodName);
        });
    }

    @SuppressWarnings("unused") // provides parameters
    static Stream<Arguments> serviceInjectionTests() {
        return Stream
                .of(
                        PseudoTestServiceInjectionGloballyAnnotated.class,
                        PseudoTestServiceInjectionGloballyAnnotatedWithFilter.class,
                        PseudoTestInheritedServiceInjectionGloballyAnnotated.class)
                .flatMap(OSGiAnnotationTest::allTestMethods);
    }

    @ParameterizedTest(name = "{0}#{2}")
    @MethodSource("serviceInjectionTests")
    void injectServices(String name, Class<?> testClass, String testMethodName) {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "foo", "quz");
        withMockedFrameworkUtil(() -> {
            assertNoFailures(testClass, testMethodName);
        });
    }

    @SuppressWarnings("unused") // provides parameters
    static Stream<Arguments> failConstructionDueToMissingServiceInjectionTests() {
        return Stream.of(PseudoTestServiceInjectionNotAnnotated.class, PseudoTestServiceInjectionGloballyAnnotatedWithFilter.class)
                .flatMap(namedMethods("injectedConstructorParameter"));
    }

    @ParameterizedTest(name = "{0}#{2}")
    @MethodSource("failConstructionDueToMissingServiceInjectionTests")
    void failConstructionDueToMissingServiceInjection(String name, Class<?> testClass, String testMethodName) {
        // setup service with non-matching filter
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "foo", "no match");
        withMockedFrameworkUtil(() -> {
            assertTestConstructionFailsDueToMissingService(testClass, testMethodName);
        });
    }

    @Test
    void injectServiceAsAnnotatedMethodParameterWithExplicitClass() {
        osgiContext.registerService(ServiceInterface.class, new ServiceA());
        withMockedFrameworkUtil(() -> {
            assertNoFailures(PseudoTestServiceMethodInjection.class, "annotatedParameterWithExplicitClass");
        });
    }

    @Test
    void injectServiceAsAnnotatedMethodParameterWithImplicitClass() {
        osgiContext.registerService(ServiceInterface.class, new ServiceA());
        withMockedFrameworkUtil(() -> {
            assertNoFailures(PseudoTestServiceMethodInjection.class, "annotatedParameterWithImpliedClass");
        });
    }

    @Test
    void injectServiceAsAnnotatedMethodParameterWithExplicitClassMultiple() {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "service.ranking", 1);
        osgiContext.registerService(ServiceInterface.class, new ServiceC(), "service.ranking", 3);
        osgiContext.registerService(ServiceInterface.class, new ServiceB(), "service.ranking", 2);
        withMockedFrameworkUtil(() -> {
            assertNoFailures(PseudoTestServiceMethodInjection.class, "annotatedParameterWithExplicitClassMultiple");
        });
    }

    @Test
    void injectServiceAsAnnotatedMethodParameterWithImplicitClassMultiple() {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "service.ranking", 1);
        osgiContext.registerService(ServiceInterface.class, new ServiceC(), "service.ranking", 3);
        osgiContext.registerService(ServiceInterface.class, new ServiceB(), "service.ranking", 2);
        withMockedFrameworkUtil(() -> {
            assertNoFailures(PseudoTestServiceMethodInjection.class, "annotatedParameterWithImpliedClassMultiple");
        });
    }


    @Test
    void injectServiceAsAnnotatedMethodParameterWithImplicitClassEmptyMultiple() {
        withMockedFrameworkUtil(() -> {
            assertNoFailures(PseudoTestServiceMethodInjection.class, "annotatedParameterWithImpliedClassEmptyMultiple");
        });
    }

    @Test
    void injectServiceAsAnnotatedMethodParameterWithIncorrectExplicitClassMultiple() {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "service.ranking", 1);
        osgiContext.registerService(ServiceInterface.class, new ServiceC(), "service.ranking", 3);
        osgiContext.registerService(ServiceInterface.class, new ServiceB(), "service.ranking", 2);
        withMockedFrameworkUtil(() -> {
            final TestExecutionSummary summary = executeAndSummarize(PseudoTestServiceMethodInjection.class, "annotatedParameterWithIncorrectExplicitClassMultiple");
            assertEquals(1, summary.getTestsFailedCount(), "expected test failure count");
            final Throwable exception = summary.getFailures().get(0).getException();
            assertThat(exception, instanceOf(ParameterResolutionException.class));
            assertThat(exception.getMessage(), equalTo("Mismatched types in annotation and parameter. " +
                    "Annotation type is \"ServiceB\", parameter type is \"ServiceInterface\""));
        });
    }

    @Test
    void injectServiceAsParameterOfAnnotatedMethod() {
        osgiContext.registerService(ServiceInterface.class, new ServiceA());
        withMockedFrameworkUtil(() -> {
            assertNoFailures(PseudoTestServiceMethodInjection.class, "annotatedMethod");
        });
    }

    @OSGi
    static class PseudoTestServiceMethodInjection {
        @Test
        void annotatedParameterWithExplicitClass(@Service(ServiceInterface.class) ServiceInterface serviceA) {
            assertThat(serviceA, instanceOf(ServiceA.class));
        }

        @Test
        void annotatedParameterWithImpliedClass(@Service ServiceInterface serviceA) {
            assertThat(serviceA, instanceOf(ServiceA.class));
        }

        @Test
        void annotatedParameterWithExplicitClassMultiple(@Service(ServiceInterface.class) List<ServiceInterface> services) {
            assertThat(services, instanceOf(List.class));
            assertThat(services, contains(asList(instanceOf(ServiceA.class), instanceOf(ServiceB.class), instanceOf(ServiceC.class))));
        }

        @Test
        void annotatedParameterWithImpliedClassMultiple(@Service Collection<ServiceInterface> services) {
            assertThat(services, instanceOf(Collection.class));
            assertThat(services, contains(asList(instanceOf(ServiceA.class), instanceOf(ServiceB.class), instanceOf(ServiceC.class))));
        }

        @Test
        void annotatedParameterWithImpliedClassEmptyMultiple(@Service List<ServiceInterface> services) {
            assertThat(services, instanceOf(List.class));
            assertThat(services, empty());
        }

        @Test
        void annotatedParameterWithIncorrectExplicitClassMultiple(@Service(ServiceB.class) List<ServiceInterface> services) {
            assertThat(services, instanceOf(List.class));
            assertThat(services, contains(instanceOf(ServiceA.class)));
        }

        @Test
        @Service(ServiceInterface.class)
        void annotatedMethod(ServiceInterface serviceA) {
            assertThat(serviceA, instanceOf(ServiceA.class));
        }
    }

    private void withMockedFrameworkUtil(Runnable callback) {
        try (final MockedStatic<FrameworkUtil> frameworkUtilMock = Mockito.mockStatic(FrameworkUtil.class)) {
            frameworkUtilMock
                    .when(() -> FrameworkUtil.getBundle(Mockito.any()))
                    .then(invocation -> osgiContext.bundleContext().getBundle());
            callback.run();
        }
    }

    @NotNull
    private static Stream<Arguments> allTestMethods(Class<?> cls) {
        return findAnnotatedMethods(cls, Testable.class, HierarchyTraversalMode.BOTTOM_UP).stream()
                .map(toArguments(cls));
    }

    @NotNull
    private static Function<Class<?>, Stream<Arguments>> namedMethods(String... testMethodNames) {
        return cls -> findAnnotatedMethods(cls, Testable.class, HierarchyTraversalMode.BOTTOM_UP).stream()
                .filter(method -> asList(testMethodNames).contains(method.getName()))
                .map(toArguments(cls));
    }

    @NotNull
    private static Function<Method, Arguments> toArguments(Class<?> cls) {
        return method -> Arguments.of(cls.getSimpleName(), cls, method.getName());
    }

    private static TestExecutionSummary executeAndSummarize(@NotNull Class<?> testClass, @Nullable String testMethodName) {
        final SummaryGeneratingListener listener = new SummaryGeneratingListener();
        JUnitPlatformHelper.executeTest(JUPITER_TEST_ENGINE, testClass, testMethodName, listener);
        return listener.getSummary();
    }

    private static void assertNoFailures(@NotNull Class<?> testClass, @Nullable String testMethodName) {
        final TestExecutionSummary summary = executeAndSummarize(testClass, testMethodName);
        assertThat("number of tests found", (int) summary.getTestsFoundCount(), greaterThan(0));
        final List<TestExecutionSummary.Failure> failures = summary.getFailures();
        switch (failures.size()) {
            case 0:
                break;
            case 1:
                fail("Got one failure instead of none", failures.get(0).getException());
            default:
                throw new MultipleFailuresError("Got " + failures.size() + " failures instead of none",
                        failures.stream().map(TestExecutionSummary.Failure::getException).collect(Collectors.toList()));
        }
    }

    private void assertTestConstructionFailsDueToMissingService(Class<?> testClass, String testMethodName) {
        final TestExecutionSummary summary = executeAndSummarize(testClass, testMethodName);
        final List<TestExecutionSummary.Failure> failures = summary.getFailures();
        assertEquals(1, failures.size(), "number of test failures");
        final TestExecutionSummary.Failure failure = failures.get(0);
        final Throwable exception = failure.getException();
        assertThat(exception, Matchers.instanceOf(ParameterResolutionException.class));
        assertThat(exception.getMessage(), anyOf(
                allOf(containsString("No ParameterResolver registered for parameter "), containsString(" in constructor ")),
                // allOf(containsString("Failed to resolve parameter "), containsString(" in constructor ")),
                allOf(containsString("No service of type "), containsString(" available"))
        ));
    }

    @OSGi
    static class PseudoTestBundleContextInjection extends Injection<BundleContext> {
        public PseudoTestBundleContextInjection(BundleContext object) {
            super(object);
        }
    }

    @OSGi
    static class PseudoTestBundleInjection extends Injection<Bundle> {
        public PseudoTestBundleInjection(Bundle object) {
            super(object);
        }
    }

    @OSGi
    static class PseudoTestServiceInjectionNotAnnotated extends Injection<ServiceInterface> {
        public PseudoTestServiceInjectionNotAnnotated(ServiceInterface object) {
            super(object);
        }

        @Override
        void injectedMethodParameter(ServiceInterface objectFromMethodInjection) {
            super.injectedMethodParameter(objectFromMethodInjection);
        }
    }

    @OSGi
    @Service(ServiceInterface.class)
    static class PseudoTestServiceInjectionGloballyAnnotated extends Injection<ServiceInterface> {
        public PseudoTestServiceInjectionGloballyAnnotated(ServiceInterface object) {
            super(object);
        }
    }

    static class PseudoTestInheritedServiceInjectionGloballyAnnotated extends PseudoTestServiceInjectionGloballyAnnotated {
        public PseudoTestInheritedServiceInjectionGloballyAnnotated(ServiceInterface object) {
            super(object);
        }
    }

    @OSGi
    @Service(value = ServiceInterface.class, filter = "(foo=quz)")
    static class PseudoTestServiceInjectionGloballyAnnotatedWithFilter extends Injection<ServiceInterface> {
        public PseudoTestServiceInjectionGloballyAnnotatedWithFilter(ServiceInterface object) {
            super(object);
        }
    }

    static abstract class Injection<T> {

        T objectFromConstructor;

        private final String typeName;

        public Injection(T object) {
            this.objectFromConstructor = object;
            final ParameterizedType parameterizedType = parameterizedTypeForBaseClass(Injection.class, getClass());
            this.typeName = ((Class<?>) parameterizedType.getActualTypeArguments()[0]).getSimpleName();
        }

        @Test
        final void injectedConstructorParameter() {
            assertNotNull(objectFromConstructor, typeName + " constructor parameter");
        }

        @Test
        void injectedMethodParameter(T objectFromMethodInjection) {
            assertNotNull(objectFromMethodInjection, typeName + " method parameter");
            assertSame(objectFromConstructor, objectFromMethodInjection,
                    typeName + " same parameter should be injected into method and constructor");
        }
    }

    interface ServiceInterface {
    }

    static class ServiceA implements ServiceInterface {
    }

    static class ServiceB implements ServiceInterface {
    }

    static class ServiceC implements ServiceInterface {
    }
}
