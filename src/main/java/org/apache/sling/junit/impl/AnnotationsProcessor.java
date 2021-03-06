/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.junit.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.sling.junit.TestObjectProcessor;
import org.apache.sling.junit.annotations.TestReference;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Processor for annotations in test classes */
@Component
public class AnnotationsProcessor implements TestObjectProcessor {
    private Logger log = LoggerFactory.getLogger(getClass());
    private BundleContext bundleContext;
    private Map<Object, List<ServiceGetter<?>>> map;

    protected void activate(ComponentContext ctx) {
        bundleContext = ctx.getBundleContext();
        this.map = new IdentityHashMap<>();
        if(bundleContext == null) {
            throw new IllegalArgumentException("Null BundleContext in activate()");
        }
        log.debug("{} activated, BundleContext={}", this, bundleContext);
    }
    
    protected void deactivate(ComponentContext ctx) {
        bundleContext = null;
        log.debug("{} deactivated", this);
        for (Map.Entry<Object, List<ServiceGetter<?>>> entry : map.entrySet()) {
            cleanupTest(entry.getKey());
        }
        map.clear();
    }
    
    /** Process annotations on the test object */
    @Override
    public Object process(Object testObject) throws Exception {
        log.debug("processing {}", testObject);
        map.put(testObject, new ArrayList<>());
        for(Field f : testObject.getClass().getDeclaredFields()) {
            if(f.isAnnotationPresent(TestReference.class)) {
                processTestReference(testObject, f);
            }
        }
        return testObject;
    }
    
    public void cleanupTest(Object test) {
        List<ServiceGetter<?>> serviceGetters = this.map.remove(test);
        for (int i=0; i<serviceGetters.size(); i++) {
            serviceGetters.get(i).close();
        }
    }

    /** Process the TestReference annotation to inject services into fields */
    private void processTestReference(Object testObject, Field f) throws Exception {
        if(bundleContext == null) {
            final String msg = "Null BundleContext in processTestReference(), not activated?";
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }
        final Class<?> serviceType = f.getType();
        Annotation[] testReferences = f.getDeclaredAnnotations();
        if(Objects.nonNull(testReferences) && testReferences.length != 0){
            TestReference testReference = (TestReference) testReferences[0];

            final Object service = getService(serviceType, testReference.target(), testObject);
            if(service != null) {
                f.setAccessible(true);
                f.set(testObject, service);
                log.debug("Injected service {} into field {}",
                        serviceType.getName(), f.getName());
            } else {
                log.warn("Service {} not found for field {}",
                        serviceType.getName(), f.getName());
            }
        }
    }

    private Object getService(Class<?> c, String target, Object testObj) {
        // target may be used to get a specific service implementation of the interface, c
        Object result = null;
        final ServiceGetter<? extends Object> serviceGetter = ServiceGetter.create(bundleContext, c, target);
        result = serviceGetter.getService();
        this.map.get(testObj).add(serviceGetter);
        return result;
    }
}
