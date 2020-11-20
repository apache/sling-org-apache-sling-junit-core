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
import java.util.Objects;

import org.apache.sling.junit.TestObjectProcessor;
import org.apache.sling.junit.annotations.TestReference;
import org.apache.sling.junit.ServiceGetter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Processor for annotations in test classes */
@Component
public class AnnotationsProcessor implements TestObjectProcessor {
    private Logger log = LoggerFactory.getLogger(getClass());
    private BundleContext bundleContext;
    
    protected void activate(ComponentContext ctx) {
        bundleContext = ctx.getBundleContext();
        if(bundleContext == null) {
            throw new IllegalArgumentException("Null BundleContext in activate()");
        }
        log.debug("{} activated, BundleContext={}", this, bundleContext);
    }
    
    protected void deactivate(ComponentContext ctx) {
        bundleContext = null;
        log.debug("{} deactivated", this);
    }
    
    /** Process annotations on the test object */
    public Object process(Object testObject) throws Exception {
        log.debug("processing {}", testObject);
        for(Field f : testObject.getClass().getDeclaredFields()) {
            if(f.isAnnotationPresent(TestReference.class)) {
                processTestReference(testObject, f);
            }
        }
        return testObject;
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
            String filter = testReference.filter();
            final Object service = getService(serviceType, filter);
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

    private Object getService(Class<?> c, String filter) {
        Object result = null;
        if(c.equals(BundleContext.class)) {
            // BundleContext is not a service, but can be injected
            result = bundleContext;
        } else if (Objects.nonNull(filter) && !filter.trim().isEmpty()){
            // filter was used to target a specific service implementation
            result = ServiceGetter.create(bundleContext, c, filter).getService();
        } else {
            ServiceReference ref = bundleContext.getServiceReference(c.getName());
            if(ref != null) {
                result = bundleContext.getService(ref);
            }
        }
        return result;
    }
}
