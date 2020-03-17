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

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.sling.junit.Activator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

/**
 * Validate waitForSystemStartup method, along with private some implementations.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Activator.class, TestsManagerImpl.class })
public class TestsManagerImplTest {

  private static final String WAIT_METHOD_NAME = "needToWait";
  private static final int SYSTEM_STARTUP_SECONDS = 2;

  static {
    // Set a short timeout so our tests can run faster
    System.setProperty("sling.junit.core.SystemStartupTimeoutSeconds", String.valueOf(SYSTEM_STARTUP_SECONDS));
  }

  /**
   * case if needToWait should return true, mainly it still have some bundles in the list to wait, and global timeout didn't pass.
   */
  @Test
  public void needToWaitPositiveNotEmptyListNotGloballyTimeout() throws Exception {
    long startupTimeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5 * SYSTEM_STARTUP_SECONDS);
    final Set<Bundle> bundlesToWaitFor = new HashSet<Bundle>();
    bundlesToWaitFor.add(Mockito.mock(Bundle.class));
    assertTrue((Boolean)Whitebox.invokeMethod(TestsManagerImpl.class, WAIT_METHOD_NAME, startupTimeout, bundlesToWaitFor));
  }

  /**
   * case if needToWait should return false, when for example it reached the global timeout limit.
   */
  @Test
  public void needToWaitNegativeForstartupTimeout() throws Exception {
    long lastChange = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS / 2);
    long startupTimeout = lastChange - TimeUnit.SECONDS.toMillis(1);
    assertFalse((Boolean)Whitebox.invokeMethod(TestsManagerImpl.class, WAIT_METHOD_NAME, startupTimeout, new HashSet<Bundle>()));
  }

  /**
   * case if needToWait should return false, when for example it reached the global timeout limit.
   */
  @Test
  public void needToWaitNegativeForEmptyList() throws Exception {
    long lastChange = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS / 2);
    long startupTimeout = lastChange + TimeUnit.SECONDS.toMillis(10);
    assertFalse((Boolean)Whitebox.invokeMethod(TestsManagerImpl.class, WAIT_METHOD_NAME, startupTimeout, new HashSet<Bundle>()));
  }

  @Test
  public void waitForSystemStartupTimeout() {
    setupBundleContextMock(Bundle.INSTALLED);
    final long elapsed = TestsManagerImpl.waitForSystemStartup();
    assertTrue(elapsed > TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS));
    assertTrue(elapsed < TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS + 1));
    assertFalse((Boolean) Whitebox.getInternalState(TestsManagerImpl.class, "waitForSystemStartup"));
  }

  @Test
  public void waitForSystemStartupAllActiveBundles() {
    setupBundleContextMock(Bundle.ACTIVE);
    final long elapsed = TestsManagerImpl.waitForSystemStartup();
    assertTrue(elapsed < TimeUnit.SECONDS.toMillis(SYSTEM_STARTUP_SECONDS));
    assertFalse((Boolean) Whitebox.getInternalState(TestsManagerImpl.class, "waitForSystemStartup"));
  }

  private void setupBundleContextMock(final int bundleState) {
    PowerMockito.mockStatic(Activator.class);
    BundleContext mockedBundleContext = mock(BundleContext.class);
    Bundle mockedBundle = mock(Bundle.class);
    Hashtable<String, String> bundleHeaders = new Hashtable<String, String>();
    when(mockedBundle.getState()).thenReturn(bundleState);
    when(mockedBundle.getHeaders()).thenReturn(bundleHeaders);
    when(mockedBundleContext.getBundles()).thenReturn(new Bundle[] { mockedBundle });
    when(Activator.getBundleContext()).thenReturn(mockedBundleContext);
    Whitebox.setInternalState(TestsManagerImpl.class, "waitForSystemStartup", true);
  }
}