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

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

/**
 * Validate waitForSystemStartup method, along with private some implementations.
 */
public class TestsManagerImplTest {

  private static final int SYSTEM_STARTUP_SECONDS = 2;

  static {
    // Set a short timeout so our tests can run faster
    System.setProperty("sling.junit.core.SystemStartupTimeoutSeconds", String.valueOf(SYSTEM_STARTUP_SECONDS));
  }

  /**
   * case if needToWait should return true, mainly it still have some bundles in the list to wait, and global timeout didn't pass.
   */
  @Test
  public void needToWaitPositiveNotEmptyListNotGloballyTimeout() {
    long startupTimeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5 * SYSTEM_STARTUP_SECONDS);
    final Set<Bundle> bundlesToWaitFor = new HashSet<>(singletonList(mock(Bundle.class)));
    assertTrue(TestsManagerImpl.needToWait(startupTimeout, bundlesToWaitFor));
  }

  /**
   * case if needToWait should return false, when for example it reached the global timeout limit.
   */
  @Test
  public void needToWaitNegativeForstartupTimeout() {
    long lastChange = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS / 2);
    long startupTimeout = lastChange - TimeUnit.SECONDS.toMillis(1);
    assertFalse(TestsManagerImpl.needToWait(startupTimeout, emptySet()));
  }

  /**
   * case if needToWait should return false, when for example it reached the global timeout limit.
   */
  @Test
  public void needToWaitNegativeForEmptyList() {
    long lastChange = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS / 2);
    long startupTimeout = lastChange + TimeUnit.SECONDS.toMillis(10);
    assertFalse(TestsManagerImpl.needToWait(startupTimeout, emptySet()));
  }

  @Test
  public void waitForSystemStartupTimeout() {
    BundleContext bundleContext = setupBundleContext(Bundle.INSTALLED);
    TestsManagerImpl testsManager = new TestsManagerImpl();
    testsManager.activate(bundleContext);
    
    final long elapsed = testsManager.waitForSystemStartup();
    assertTrue(elapsed > TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS));
    assertTrue(elapsed < TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS + 1));
    assertTrue(testsManager.isReady());
  }

  @Test
  public void waitForSystemStartupAllActiveBundles() {
    BundleContext bundleContext = setupBundleContext(Bundle.ACTIVE);
    TestsManagerImpl testsManager = new TestsManagerImpl();
    testsManager.activate(bundleContext);

    final long elapsed = testsManager.waitForSystemStartup();
    assertTrue(elapsed < TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS));
    assertTrue(testsManager.isReady());
  }

  private BundleContext setupBundleContext(int state) {
    final Bundle bundle = mock(Bundle.class);
    when(bundle.getSymbolicName()).thenReturn("mocked-bundle");
    when(bundle.getState()).thenReturn(state);
    when(bundle.adapt(BundleWiring.class)).thenReturn(mock(BundleWiring.class));
    when(bundle.getHeaders()).thenReturn(new Hashtable<>());

    final BundleContext bundleContext = mock(BundleContext.class);
    when(bundleContext.getBundle()).thenReturn(bundle);
    when(bundleContext.getBundles()).thenAnswer(m -> new Bundle[] { bundle });

    when(bundle.getBundleContext()).thenReturn(bundleContext);
    return bundleContext;
  }
}