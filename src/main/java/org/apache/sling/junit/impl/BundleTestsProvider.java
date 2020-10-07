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

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.BundleTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A TestProvider that gets test classes from bundles
 *  that have a Sling-Test-Regexp header and corresponding
 *  exported classes.
 */
@Component
public class BundleTestsProvider extends AbstractTestsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(BundleTestsProvider.class);

    public static final String SLING_TEST_REGEXP = "Sling-Test-Regexp";
    
    private TestClassesTracker tracker;

    @Activate
    protected void activate(BundleContext ctx) {
        tracker = new TestClassesTracker(ctx);
        tracker.open();
    }

    @Deactivate
    protected void deactivate() {
        if (tracker != null) {
            tracker.close();
            tracker = null;
        }
    }

    public Class<?> createTestClass(String testName) throws ClassNotFoundException {
        final Bundle bundle = tracker.getTracked().entrySet().stream()
                .filter(entry -> entry.getValue().contains(testName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No Bundle found that supplies test class " + testName));
        return bundle.loadClass(testName);
    }

    public List<String> getTestNames() {
        return tracker.getTracked().values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private static class TestClassesTracker extends BundleTracker<Set<String>> {
        public TestClassesTracker(BundleContext ctx) {
            super(ctx, Bundle.ACTIVE, null);
        }

        @Override
        public Set<String> addingBundle(Bundle bundle, BundleEvent event) {
            super.addingBundle(bundle, event);
            final Set<String> testClasses = getTestClasses(bundle);
            return testClasses.isEmpty() ? null : testClasses;
        }

        /** Get test classes that bundle b provides (as done in Felix/Sigil) */
        @NotNull
        private static Set<String> getTestClasses(Bundle bundle) {
            final String headerValue = getSlingTestRegexp(bundle);
            if (headerValue == null) {
                LOG.debug("Bundle '{}' does not have {} header, not looking for test classes",
                        bundle.getSymbolicName(), SLING_TEST_REGEXP);
                return Collections.emptySet();
            }

            Predicate<String> isTestClass;
            try {
                isTestClass = Pattern.compile(headerValue).asPredicate();
            } catch (PatternSyntaxException pse) {
                LOG.warn("Bundle '{}' has an invalid pattern for {} header, ignored: '{}'",
                        bundle.getSymbolicName(), SLING_TEST_REGEXP, headerValue);
                return Collections.emptySet();
            }

            Enumeration<URL> classUrls = bundle.findEntries("", "*.class", true);
            final Set<String> result = new LinkedHashSet<>();
            while (classUrls.hasMoreElements()) {
                URL url = classUrls.nextElement();
                final String name = toClassName(url);
                if(isTestClass.test(name)) {
                    result.add(name);
                } else {
                    LOG.debug("Class '{}' does not match {} pattern '{}' of bundle '{}', ignored",
                            name, SLING_TEST_REGEXP, headerValue, bundle.getSymbolicName());
                }
            }

            LOG.info("{} test classes found in bundle '{}'", result.size(), bundle.getSymbolicName());
            return result;
        }

        private static String getSlingTestRegexp(Bundle bundle) {
            return bundle.getHeaders().get(SLING_TEST_REGEXP);
        }

        /** Convert class URL to class name */
        private static String toClassName(URL url) {
            final String f = url.getFile();
            final String cn = f.substring(1, f.length() - ".class".length());
            return cn.replace('/', '.');
        }
    }
}
