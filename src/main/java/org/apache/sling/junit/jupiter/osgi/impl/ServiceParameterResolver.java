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

import org.apache.sling.junit.jupiter.osgi.Service;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

public class ServiceParameterResolver extends AbstractTypeBasedParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ServiceParameterResolver.class);

    @Override
    protected boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext, Type resolvedParameterType) {
        return computeServiceType(resolvedParameterType)
                .flatMap(serviceType -> findServiceAnnotation(parameterContext, extensionContext, serviceType))
                .isPresent();
    }

    @Override
    protected Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext, Type resolvedParameterType) {
        return computeServiceType(resolvedParameterType)
                .map(serviceType -> {
                    final Optional<Service> serviceAnnotation = findServiceAnnotation(parameterContext, extensionContext, serviceType);
                    return serviceAnnotation
                            .map(ann -> toKey(serviceType, ann))
                            .map(key -> extensionContext.getStore(NAMESPACE)
                                    .getOrComputeIfAbsent(key, serviceHolderFactory(extensionContext, serviceType), ServiceHolder.class))
                            .map(serviceHolder -> isMultiple(resolvedParameterType) ? serviceHolder.getServices() : serviceHolder.getService())
                            .orElseThrow(() -> createServiceNotFoundException(serviceAnnotation.map(Service::filter).orElse(null), serviceType));
                })
                .orElseThrow(() -> new ParameterResolutionException("Cannot handle type " + resolvedParameterType));
    }

    private static ServiceHolder.Key toKey(Class<?> serviceType, Service serviceAnnotation) {
        return new ServiceHolder.Key(serviceType, serviceAnnotation);
    }

    @NotNull
    private static Optional<Class<?>> computeServiceType(Type resolvedParameterType) {
        if (resolvedParameterType instanceof ParameterizedType) {
            final ParameterizedType parameterizedType = (ParameterizedType) resolvedParameterType;
            final Class<?> clazz = getRawClass(parameterizedType);
            if (Collection.class == clazz || List.class.isAssignableFrom(clazz)) {
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

    private ParameterResolutionException createServiceNotFoundException(String ldapFilter, Type resolvedParameterType) {
        return Optional.ofNullable(ldapFilter)
                .map(String::trim)
                .filter(filter -> !filter.isEmpty())
                .map(filter -> new ParameterResolutionException("No service of type " + resolvedParameterType + " with filter \"" + filter + "\" available"))
                .orElseGet(() -> new ParameterResolutionException("No service of type " + resolvedParameterType + " available"));
    }

    @NotNull
    private static Function<ServiceHolder.Key, ServiceHolder> serviceHolderFactory(ExtensionContext extensionContext, Class<?> requiredServiceType) {
        return key -> new ServiceHolder(getBundleContext(extensionContext), key);
    }

    @Nullable
    private static BundleContext getBundleContext(ExtensionContext extensionContext) {
        return Optional.ofNullable(FrameworkUtil.getBundle(extensionContext.getRequiredTestClass()))
                .map(Bundle::getBundleContext)
                .orElse(null);
    }

    @NotNull
    private static Optional<Service> findServiceAnnotation(ParameterContext parameterContext, ExtensionContext extensionContext, Class<?> requiredServiceType) {
        return Stream.<Supplier<Optional<Service>>>of(
                () -> findServiceAnnotationOnParameter(parameterContext, requiredServiceType),
                () -> findServiceAnnotationOnMethodOrClass(extensionContext, requiredServiceType))
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private static Optional<Service> findServiceAnnotationOnMethodOrClass(ExtensionContext extensionContext, Class<?> requiredServiceType) {
        return extensionContext.getElement()
                .map(ae -> findMatchingServiceAnnotation(ae, requiredServiceType))
                .filter(Optional::isPresent)
                .orElseGet(() -> extensionContext.getParent().flatMap(p -> findServiceAnnotationOnMethodOrClass(p, requiredServiceType)));
    }

    private static Optional<Service> findServiceAnnotationOnParameter(ParameterContext parameterContext, Class<?> requiredServiceType) {
        final Optional<Service> serviceAnnotation = findAnnotation(parameterContext.getParameter(), Service.class);
        serviceAnnotation.ifPresent(ann -> {
            if (!ann.value().isAssignableFrom(requiredServiceType)) {
                throw new ParameterResolutionException("Mismatched types in annotation and parameter. " +
                        "Annotation type is \"" + ann.value().getSimpleName() + "\", parameter type is \"" + requiredServiceType.getSimpleName() + "\"");
            }
        });
        return serviceAnnotation;
    }

    private static Optional<Service> findMatchingServiceAnnotation(AnnotatedElement annotatedElement, Class<?> requiredServiceType) {
        return AnnotationSupport.findRepeatableAnnotations(annotatedElement, Service.class)
                .stream()
                .filter(serviceAnnotation -> Objects.equals(serviceAnnotation.value(), requiredServiceType))
                .findFirst();
    }

    private boolean isMultiple(Type resolvedParameterType) {
        if (resolvedParameterType instanceof ParameterizedType) {
            final Class<?> type = getRawClass((ParameterizedType) resolvedParameterType);
            return Collection.class == type || List.class.isAssignableFrom(type);
        }
        return false;
    }

    private static class ServiceHolder implements ExtensionContext.Store.CloseableResource {

        private final ServiceTracker<?, ?> serviceTracker;

        private ServiceHolder(BundleContext bundleContext, Key key) {
            final Filter filter = createFilter(bundleContext, key.type(), key.filter());
            serviceTracker = new SortingServiceTracker<>(bundleContext, filter);
            serviceTracker.open();
        }

        @Override
        public void close() throws Throwable {
            serviceTracker.close();
        }

        public Object getService() {
            return serviceTracker.getService();
        }

        public List<Object> getServices() {
            final Object[] services = serviceTracker.getServices();
            return services == null ? Collections.emptyList() : Arrays.asList(services);
        }

        private static Filter createFilter(BundleContext bundleContext, Class<?> clazz, String ldapFilter) {
            final String classFilter = String.format("(%s=%s)", Constants.OBJECTCLASS, clazz.getName());
            final String combinedFilter;
            if (ldapFilter == null || ldapFilter.trim().isEmpty()) {
                combinedFilter = classFilter;
            } else {
                combinedFilter = String.format("(&%s%s)", classFilter, ldapFilter);
            }
            try {
                return bundleContext.createFilter(combinedFilter);
            } catch (InvalidSyntaxException e) {
                throw new IllegalArgumentException("Invalid filter expression: \"" + ldapFilter + "\"", e);
            }
        }

        private static class SortingServiceTracker<T> extends ServiceTracker<T, T> {
            public SortingServiceTracker(BundleContext bundleContext, Filter filter) {
                super(bundleContext, filter, null);
            }

            @Override
            public ServiceReference<T>[] getServiceReferences() {
                return Optional.ofNullable(super.getServiceReferences())
                        .map(serviceReferences -> {
                            Arrays.sort(serviceReferences);
                            return serviceReferences;
                        })
                        .orElse(null);
            }
        }

        private static class Key {

            private final Class<?> serviceType;

            private final Service serviceAnnotation;

            public Key(Class<?> serviceType, Service serviceAnnotation) {
                this.serviceType = serviceType;
                this.serviceAnnotation = serviceAnnotation;
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

            public Class<?> type() {
                return serviceType;
            }

            public String filter() {
                return serviceAnnotation.filter();
            }
        }
    }
}
