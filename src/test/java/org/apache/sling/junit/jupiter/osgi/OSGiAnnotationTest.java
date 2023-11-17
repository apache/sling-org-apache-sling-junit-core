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
import org.apache.sling.junit.jupiter.osgi.utils.MetaTest;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.jupiter.params.provider.Arguments;
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
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.apache.sling.junit.jupiter.osgi.ServiceCardinality.MANDATORY;
import static org.apache.sling.junit.jupiter.osgi.ServiceCardinality.OPTIONAL;
import static org.apache.sling.junit.jupiter.osgi.impl.ReflectionHelper.parameterizedTypeForBaseClass;
import static org.apache.sling.junit.jupiter.osgi.utils.MemberMatcher.hasMember;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
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
class OSGiAnnotationTest {

    private static final JupiterTestEngine JUPITER_TEST_ENGINE = new JupiterTestEngine();

    OsgiContext osgiContext = new OsgiContext();

    @MetaTest({BundleInjection.class, BundleContextInjection.class})
    void injectFrameworkObjects(String name, Class<?> testClass, String testMethodName) {
        withMockedFrameworkUtil(() -> {
            assertNoFailures(testClass, testMethodName);
        });
    }

    @MetaTest({ServiceInjectionGloballyAnnotated.class,
            InheritedServiceInjectionGloballyAnnotated.class,
            ServiceInjectionGloballyAnnotatedWithFilter.class,
            MultipleServiceInjectionParameterAnnotated.class,
            MultipleServiceInjectionMethodAnnotated.class,
            MultipleServiceInjectionClassAnnotated.class})
    void injectServices(String name, Class<?> testClass, String testMethodName) {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "foo", "quz");
        withMockedFrameworkUtil(() -> {
            assertNoFailures(testClass, testMethodName);
        });
    }

    @MetaTest(value = ServiceInjectionNoServiceAnnotation.class, methods = "injectedConstructorParameter")
    void failConstructionDueToMissingServiceAnnotation(String name, Class<?> testClass, String testMethodName) {
        osgiContext.registerService(ServiceInterface.class, new ServiceA());
        withMockedFrameworkUtil(() -> {
            assertFailure(testClass, testMethodName, ParameterResolutionException.class,
                    allOf(containsString("No ParameterResolver registered for parameter "), containsString(" in constructor ")));
        });
    }

    @MetaTest(value = ServiceInjectionGloballyAnnotatedWithFilter.class, methods = "injectedConstructorParameter")
    void failConstructionDueToMissingService(String name, Class<?> testClass, String testMethodName) {
        // setup service with non-matching filter
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "foo", "no match");
        withMockedFrameworkUtil(() -> {
            assertFailure(testClass, testMethodName, ParameterResolutionException.class,
                    allOf(containsString("No service of type "), containsString(" available")));
        });
    }

    @MetaTest(MultipleAnnotationsOnParameterFailure.class)
    void failMultipleAnnotationsOnParameter(String name, Class<?> testClass, String testMethodName) {
        withMockedFrameworkUtil(() -> {
            assertFailure(testClass, testMethodName, ParameterResolutionException.class,
                    allOf(
                            startsWith("Parameters must not be annotated with multiple @Service annotations: "),
                            containsString("MultipleAnnotationsOnParameterFailure"), // class name
                            containsString("multipleAnnotationsFailure"), // method name
                            containsString("MissingServiceInterface"))); // parameter type
        });
    }

    @MetaTest(InvalidFilterExpressionFailure.class)
    void failInvalidFilterExpression(String name, Class<?> testClass, String testMethodName) {
        withMockedFrameworkUtil(() -> {
            assertFailure(testClass, testMethodName, ParameterResolutionException.class,
                    equalTo("Invalid filter expression used in @Service annotation :\"(abc = def\""));
        });
    }

    @MetaTest(ServiceMethodInjection.class)
    void allServiceMethodInjectionTests(String name, Class<?> testClass, String testMethodName) {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "service.ranking", 3);
        osgiContext.registerService(ServiceInterface.class, new ServiceC(), "service.ranking", 1);
        osgiContext.registerService(ServiceInterface.class, new ServiceB(), "service.ranking", 2);
        withMockedFrameworkUtil(() -> {
            assertNoFailures(testClass, testMethodName);
        });
    }

    @MetaTest(value = ServiceMethodInjection.class, methods = {
            "annotatedParameterWithImpliedClassOptionalMultiple",
            "annotatedParameterWithImpliedClassExplicitOptionalMultiple"
    })
    void injectServiceAsAnnotatedMethodParameterWithImplicitClassEmptyMultiple(String name, Class<?> testClass, String testMethodName) {
        withMockedFrameworkUtil(() -> {
            assertNoFailures(testClass, testMethodName);
        });
    }

    @MetaTest(MismatchedServiceTypeOnAnnotatedParameter.class)
    void failOnMismatchedServiceType(String name, Class<?> testClass, String testMethodName) {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "service.ranking", 1);
        osgiContext.registerService(ServiceInterface.class, new ServiceC(), "service.ranking", 3);
        osgiContext.registerService(ServiceInterface.class, new ServiceB(), "service.ranking", 2);
        withMockedFrameworkUtil(() -> {
            assertFailure(testClass, testMethodName, ParameterResolutionException.class,
                    equalTo("Mismatched types in annotation and parameter. " +
                            "Annotation type is \"ServiceB\", parameter type is \"ServiceInterface\""));
        });
    }

    @MetaTest(MissingMandatoryServiceInjectionOnAnnotatedParameter.class)
    void failOnMissingMandatoryService(String name, Class<?> testClass, String testMethodName) {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "service.ranking", 1);
        osgiContext.registerService(ServiceInterface.class, new ServiceC(), "service.ranking", 3);
        osgiContext.registerService(ServiceInterface.class, new ServiceB(), "service.ranking", 2);
        withMockedFrameworkUtil(() -> {
            assertFailure(testClass, testMethodName, ParameterResolutionException.class,
                    equalTo("No service of type \"org.apache.sling.junit." +
                            "jupiter.osgi.OSGiAnnotationTest$MissingServiceInterface\" available"));
        });
    }

    @MetaTest(MissingFilteredMandatoryServiceInjectionOnAnnotatedParameter.class)
    void failOnMissingFilteredService(String name, Class<?> testClass, String testMethodName) {
        osgiContext.registerService(ServiceInterface.class, new ServiceA(), "service.ranking", 1);
        osgiContext.registerService(ServiceInterface.class, new ServiceC(), "service.ranking", 3);
        osgiContext.registerService(ServiceInterface.class, new ServiceB(), "service.ranking", 2);
        withMockedFrameworkUtil(() -> {
            assertFailure(testClass, testMethodName, ParameterResolutionException.class,
                    equalTo("No service of type \"org.apache.sling.junit." +
                            "jupiter.osgi.OSGiAnnotationTest$ServiceInterface\" " +
                            "with filter \"(service.ranking=100)\" available"));
        });
    }


    @MetaTest({BundleInjection.class, BundleContextInjection.class, ServiceInjectionGloballyAnnotated.class})
    void failOutsideOSGiEnvironment(String name, Class<?> testClass, String testMethodName) {
        assertFailure(testClass, testMethodName, ParameterResolutionException.class,
                endsWith("@OSGi and @Service annotations can only be used with tests running in an OSGi environment"));
    }

    private void assertFailure(Class<?> testClass, String testMethodName, Class<? extends Throwable> exceptionClass, Matcher<String> exceptionMessageMatcher) {
        final TestExecutionSummary summary = executeAndSummarize(testClass, testMethodName);
        final List<TestExecutionSummary.Failure> failures = summary.getFailures().stream().filter(failure -> failure.getTestIdentifier().isTest()).collect(Collectors.toList());
        assertThat(failures, contains(
                hasMember("getException()", TestExecutionSummary.Failure::getException,
                    allOf(instanceOf(exceptionClass), hasMember("getMessage()", Throwable::getMessage, exceptionMessageMatcher)))));
    }

    @OSGi
    static class MismatchedServiceTypeOnAnnotatedParameter {

        @Test
        void unary(@Service(ServiceB.class) ServiceInterface service) {
            failMismatchedServiceType();
        }

        @Test
        void multiple(@Service(ServiceB.class) List<ServiceInterface> services) {
            failMismatchedServiceType();
        }

        private static void failMismatchedServiceType() {
            fail("Method not be called due to mismatching service types");
        }
    }

    @OSGi
    @Service(value = ServiceInterface.class, cardinality = MANDATORY, filter = "(service.ranking=100)")
    static class MissingFilteredMandatoryServiceInjectionOnAnnotatedParameter {

        @Test
        void missingUnaryFilteredService(ServiceInterface service) {
            failMissing();
        }

        @Test
        void missingMultipleFilteredService(List<ServiceInterface> services) {
            failMissing();
        }

        private static void failMissing() {
            fail("Method must not be called due to missing service with matching filter");
        }

    }

    @OSGi
    static class MissingMandatoryServiceInjectionOnAnnotatedParameter {

        @Test
        void missingImplictlyMandatoryUnaryService(@Service MissingServiceInterface service) {
            failMandatory();
        }

        @Test
        void missingExplicitlyMandatoryUnaryService(@Service(cardinality = MANDATORY) MissingServiceInterface service) {
            failMandatory();
        }

        @Test
        void missingMandatoryMultipleService(@Service(cardinality = MANDATORY) List<MissingServiceInterface> services) {
            failMandatory();
        }

        private static void failMandatory() {
            fail("Method must not be called due to missing mandatory service");
        }
    }

    @OSGi
    static class ServiceMethodInjection {

        @Test
        void annotatedParameterWithMissingOptionalService(@Service(cardinality = OPTIONAL) MissingServiceInterface service) {
            assertThat(service, nullValue());
        }

        @Test
        void annotatedParameterWithImpliedClassExplicitOptionalMultiple(@Service(cardinality = OPTIONAL) List<MissingServiceInterface> services) {
            assertThat(services, instanceOf(List.class));
            assertThat(services, empty());
        }

        @Test
        void annotatedParameterWithImpliedClassOptionalMultiple(@Service List<MissingServiceInterface> services) {
            assertThat(services, instanceOf(List.class));
            assertThat(services, empty());
        }

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
        void annotatedParameterWithImpliedClassExplicitMandatoryMultiple(@Service(cardinality = MANDATORY) List<ServiceInterface> services) {
            assertThat(services, instanceOf(List.class));
            assertThat(services, contains(asList(instanceOf(ServiceA.class), instanceOf(ServiceB.class), instanceOf(ServiceC.class))));
        }

        @Test
        void annotatedParameterWithExistingOptionalService(@Service(cardinality = OPTIONAL) ServiceInterface service) {
            assertThat(service, instanceOf(ServiceA.class));
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

    @OSGi
    static class BundleContextInjection extends Injection<BundleContext> {
        public BundleContextInjection(BundleContext object) {
            super(object);
        }
    }

    @OSGi
    static class BundleInjection extends Injection<Bundle> {
        public BundleInjection(Bundle object) {
            super(object);
        }
    }

    @OSGi
    static class ServiceInjectionNoServiceAnnotation extends Injection<ServiceInterface> {
        public ServiceInjectionNoServiceAnnotation(ServiceInterface object) {
            super(object);
        }
    }

    @OSGi
    @Service(ServiceInterface.class)
    static class ServiceInjectionGloballyAnnotated extends Injection<ServiceInterface> {
        public ServiceInjectionGloballyAnnotated(ServiceInterface object) {
            super(object);
        }
    }

    @OSGi
    @Service(value = ServiceInterface.class, filter = "(foo=quz)")
    static class ServiceInjectionGloballyAnnotatedWithFilter extends Injection<ServiceInterface> {
        public ServiceInjectionGloballyAnnotatedWithFilter(ServiceInterface object) {
            super(object);
        }
    }

    static class InheritedServiceInjectionGloballyAnnotated extends ServiceInjectionGloballyAnnotated {
        public InheritedServiceInjectionGloballyAnnotated(ServiceInterface object) {
            super(object);
        }
    }

    @OSGi
    static class MultipleServiceInjectionParameterAnnotated extends AbstractMultipleServiceInjection {
        public MultipleServiceInjectionParameterAnnotated(@Service List<ServiceInterface> object) {
            super(object);
        }

        @Test @Override
        void injectedMethodParameter(@Service List<ServiceInterface> objectFromMethodInjection) {
            super.injectedMethodParameter(objectFromMethodInjection);
        }
    }

    @OSGi
    static class MultipleServiceInjectionMethodAnnotated extends AbstractMultipleServiceInjection {

        @Service(ServiceInterface.class)
        public MultipleServiceInjectionMethodAnnotated(List<ServiceInterface> object) {
            super(object);
        }

        @Test @Override
        @Service(ServiceInterface.class)
        void injectedMethodParameter(List<ServiceInterface> objectFromMethodInjection) {
            super.injectedMethodParameter(objectFromMethodInjection);
        }
    }

    @OSGi
    @Service(ServiceInterface.class)
    static class MultipleServiceInjectionClassAnnotated extends AbstractMultipleServiceInjection {
        public MultipleServiceInjectionClassAnnotated(List<ServiceInterface> object) {
            super(object);
        }
    }

    @OSGi
    static class MultipleAnnotationsOnParameterFailure {
        @Test
        void multipleAnnotationsFailure(@Service @Service(MissingServiceInterface.class) MissingServiceInterface service) {
            fail("Method must not be called due to duplicate @Service annotation on parameter");
        }
    }

    @OSGi
    static class InvalidFilterExpressionFailure {
        @Test
        void invalidFilterExpressionFailure(@Service(filter = "(abc = def") MissingServiceInterface service) {
            fail("Method must not be called due to duplicate @Service annotation on parameter");
        }
    }

    static abstract class AbstractMultipleServiceInjection extends Injection<List<ServiceInterface>> {

        public AbstractMultipleServiceInjection(List<ServiceInterface> object) {
            super(object);
        }

        @Test @Override
        void injectedMethodParameter(List<ServiceInterface> objectFromMethodInjection) {
            assertNotNull(objectFromMethodInjection, typeName + " method parameter");
            assertEquals(objectFromConstructor, objectFromMethodInjection);
            assertThat("number of services", objectFromMethodInjection.size(), is(1));
            assertThat( "same service instance should be contained in the Lists injected into method and constructor",
                    objectFromConstructor, contains(sameInstance(objectFromMethodInjection.get(0))));
        }
    }

    static abstract class Injection<T> {

        protected T objectFromConstructor;

        protected final String typeName;

        public Injection(T object) {
            this.objectFromConstructor = object;
            final ParameterizedType parameterizedType = parameterizedTypeForBaseClass(Injection.class, getClass());
            final Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
            this.typeName = actualTypeArgument.getTypeName();
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

    interface MissingServiceInterface {
    }
}
