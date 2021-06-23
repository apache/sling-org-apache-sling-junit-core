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

import org.apache.sling.junit.jupiter.osgi.impl.AbstractTypeBasedParameterResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.platform.commons.support.AnnotationSupport;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

class ServiceParameterResolver extends AbstractTypeBasedParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ServiceParameterResolver.class);

    @Override
    protected boolean supportsParameter(@NotNull ParameterContext parameterContext, @NotNull ExtensionContext extensionContext, @NotNull Type resolvedParameterType) {
        final Optional<Service> service = computeServiceType(resolvedParameterType)
                .flatMap(serviceType -> findServiceAnnotation(parameterContext, extensionContext, serviceType));
        return service
                .isPresent();
    }

    @Override
    protected Object resolveParameter(@NotNull ParameterContext parameterContext, @NotNull ExtensionContext extensionContext, @NotNull Type resolvedParameterType) {
        final ServiceHolder.Key key = computeServiceType(resolvedParameterType)
                .flatMap(serviceType -> findServiceAnnotation(parameterContext, extensionContext, serviceType)
                        .map(ann -> toKey(serviceType, ann)))
                .orElseThrow(() -> new ParameterResolutionException("Cannot handle type " + resolvedParameterType));

        final ServiceHolder serviceHolder = extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(key, serviceHolderFactory(extensionContext), ServiceHolder.class);
        return isMultiple(resolvedParameterType) ? serviceHolder.getServices() : serviceHolder.getService();
    }

    private static ServiceHolder.Key toKey(Class<?> serviceType, Service serviceAnnotation) {
        return new ServiceHolder.Key(serviceType, serviceAnnotation);
    }

    @NotNull
    private static Optional<Class<?>> computeServiceType(@NotNull Type resolvedParameterType) {
        if (resolvedParameterType instanceof ParameterizedType) {
            final ParameterizedType parameterizedType = (ParameterizedType) resolvedParameterType;
            if (isMultiple(parameterizedType)) {
                final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length == 1 && actualTypeArguments[0] instanceof Class<?>) {
                    return Optional.of((Class<?>) actualTypeArguments[0]);
                }
            }
        } else if (resolvedParameterType instanceof Class<?>) {
            return Optional.of((Class<?>) resolvedParameterType);
        }
        return Optional.empty();
    }

    @NotNull
    private static Class<?> getRawClass(ParameterizedType parameterizedType) {
        final Type rawType = parameterizedType.getRawType();
        if (!(rawType instanceof Class<?>)) {
            throw new UnsupportedOperationException("Unexpected raw type of parametereized type " + parameterizedType + ": " + rawType);
        }
        return (Class<?>) rawType;
    }

    @NotNull
    private static Function<ServiceHolder.Key, ServiceHolder> serviceHolderFactory(ExtensionContext extensionContext) {
        return key -> new ServiceHolder(getBundleContext(extensionContext), key);
    }

    @NotNull
    private static BundleContext getBundleContext(@NotNull ExtensionContext extensionContext) {
        return Optional.ofNullable(FrameworkUtil.getBundle(extensionContext.getRequiredTestClass()))
                .map(Bundle::getBundleContext)
                .orElseThrow(() -> new ParameterResolutionException("@OSGi and @Service annotations can only be used with tests running in an OSGi environment"));
    }

    @NotNull
    private static Optional<Service> findServiceAnnotation(@NotNull ParameterContext parameterContext, @NotNull ExtensionContext extensionContext, @NotNull Class<?> requiredServiceType) {
        return Stream.concat(
                Stream.of(findMatchingServiceAnnotationOnParameter(parameterContext, requiredServiceType)),
                Stream.of(parameterContext.getDeclaringExecutable(), extensionContext.getRequiredTestClass())
                        .map(annotatedElement -> findMatchingServiceAnnotation(annotatedElement, requiredServiceType)))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static Service findMatchingServiceAnnotationOnParameter(@NotNull ParameterContext parameterContext, @NotNull Class<?> requiredServiceType) {
        final List<Service> serviceAnnotations = parameterContext.findRepeatableAnnotations(Service.class);
        switch (serviceAnnotations.size()) {
            case 0:
                return null;
            case 1:
                final Service serviceAnnotation = serviceAnnotations.get(0);
                if (!serviceAnnotation.value().isAssignableFrom(requiredServiceType)) {
                    throw new ParameterResolutionException("Mismatched types in annotation and parameter. " +
                            "Annotation type is \"" + serviceAnnotation.value().getSimpleName() + "\", parameter type is \"" + requiredServiceType.getSimpleName() + "\"");
                }
                return serviceAnnotation;
            default:
                throw new ParameterResolutionException("Parameters must not be annotated with multiple @Service annotations: " + parameterContext.getDeclaringExecutable());
        }
    }

    @Nullable
    private static Service findMatchingServiceAnnotation(@Nullable AnnotatedElement annotatedElement, @NotNull Class<?> requiredServiceType) {
        return AnnotationSupport.findRepeatableAnnotations(annotatedElement, Service.class)
                .stream()
                .filter(serviceAnnotation -> Objects.equals(serviceAnnotation.value(), requiredServiceType))
                .findFirst()
                .orElse(null);
    }

    private static boolean isMultiple(@NotNull Type resolvedParameterType) {
        if (resolvedParameterType instanceof ParameterizedType) {
            final Class<?> type = getRawClass((ParameterizedType) resolvedParameterType);
            return Collection.class == type || List.class.isAssignableFrom(type);
        }
        return false;
    }

    private static class ServiceHolder implements ExtensionContext.Store.CloseableResource {

        private final Key key;

        private final ServiceTracker<?, ?> serviceTracker;

        private ServiceHolder(@NotNull BundleContext bundleContext, @NotNull Key key) {
            this.key = key;
            final Filter filter = createFilter(bundleContext, key.type(), key.filter());
            serviceTracker = new SortingServiceTracker<>(bundleContext, filter);
            serviceTracker.open();
        }

        @Override
        public void close() throws Throwable {
            serviceTracker.close();
        }

        @Nullable
        public Object getService() throws ParameterResolutionException{
            final Object service = serviceTracker.getService();
            return checkCardinality(service, false);
        }

        @NotNull
        public List<Object> getServices() throws ParameterResolutionException {
            @Nullable final Object[] services = serviceTracker.getServices();;
            return Optional.ofNullable(checkCardinality(services, true))
                    .map(Arrays::asList)
                    .orElseGet(Collections::emptyList);
        }

        @Nullable
        private <T> T checkCardinality(@Nullable T service, boolean isMultiple) throws ParameterResolutionException {
            final ServiceCardinality effectiveCardinality = calculateEffectiveCardinality(isMultiple);
            if (service == null && effectiveCardinality == ServiceCardinality.MANDATORY) {
                throw createServiceNotFoundException(key.filter(), key.type());
            }
            return service;
        }

        @NotNull
        private ServiceCardinality calculateEffectiveCardinality(boolean isMultiple) {
            final ServiceCardinality cardinality = key.cardinality();
            if (cardinality == ServiceCardinality.AUTO) {
                return isMultiple ? ServiceCardinality.OPTIONAL : ServiceCardinality.MANDATORY;
            }
            return cardinality;
        }

        @NotNull
        private static ParameterResolutionException createServiceNotFoundException(@NotNull String ldapFilter, @NotNull Type resolvedParameterType) {
            return Optional.of(ldapFilter)
                    .map(String::trim)
                    .filter(filter -> !filter.isEmpty())
                    .map(filter -> new ParameterResolutionException("No service of type \"" + resolvedParameterType.getTypeName() + "\" with filter \"" + filter + "\" available"))
                    .orElseGet(() -> new ParameterResolutionException("No service of type \"" + resolvedParameterType.getTypeName() + "\" available"));
        }

        @NotNull
        private static Filter createFilter(@NotNull BundleContext bundleContext, @NotNull Class<?> clazz, @NotNull String ldapFilter) {
            final String classFilter = String.format("(%s=%s)", Constants.OBJECTCLASS, clazz.getName());
            final String combinedFilter;
            if (ldapFilter.trim().isEmpty()) {
                combinedFilter = classFilter;
            } else {
                combinedFilter = String.format("(&%s%s)", classFilter, ldapFilter);
            }
            try {
                return bundleContext.createFilter(combinedFilter);
            } catch (InvalidSyntaxException e) {
                throw new ParameterResolutionException("Invalid filter expression used in @Service annotation :\"" + ldapFilter + "\"", e);
            }
        }

        private static class SortingServiceTracker<T> extends ServiceTracker<T, T> {
            public SortingServiceTracker(@NotNull BundleContext bundleContext, @NotNull Filter filter) {
                super(bundleContext, filter, null);
            }

            @Override
            @Nullable
            public ServiceReference<T>[] getServiceReferences() {
                return Optional.ofNullable(super.getServiceReferences())
                        .map(serviceReferences -> {
                            Arrays.sort(serviceReferences, Comparator.reverseOrder());
                            return serviceReferences;
                        })
                        .orElse(null);
            }
        }

        private static class Key {

            private final Class<?> serviceType;

            private final Service serviceAnnotation;

            public Key(@NotNull Class<?> serviceType, @NotNull Service serviceAnnotation) {
                this.serviceType = serviceType;
                this.serviceAnnotation = serviceAnnotation;
            }

            @NotNull
            public Class<?> type() {
                return serviceType;
            }

            @NotNull
            public String filter() {
                return serviceAnnotation.filter();
            }

            @NotNull
            public ServiceCardinality cardinality() {
                return serviceAnnotation.cardinality();
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Key)) {
                    return false;
                }
                Key key = (Key) o;
                return this == o
                        || (Objects.equals(serviceType, key.serviceType)
                        && Objects.equals(serviceAnnotation, key.serviceAnnotation));
            }

            @Override
            public int hashCode() {
                return Objects.hash(serviceType, serviceAnnotation);
            }
        }
    }
}
