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

import java.util.Dictionary;
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

  static {
    // Set the system properties for this test as the default would wait longer.
    System.setProperty(TestsManagerImpl.PROPERTY_SYSTEM_STARTUP_GLOBAL_TIMEOUT_SECONDS, "2");
  }

  /**
   * case if isWaitNeeded should return true, mainly it still have some bundles in the list to wait, and global timeout didn't pass.
   */
  @Test
  public void isWaitNeededPositiveNotEmptyListNotGloballyTimeout() throws Exception {
    final TestsManagerImpl testsManager = new TestsManagerImpl();
    long globalTimeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
    final Set<Bundle> bundlesToWaitFor = new HashSet<Bundle>();
    bundlesToWaitFor.add(Mockito.mock(Bundle.class));
    boolean isWaitNeeded = Whitebox.invokeMethod(TestsManagerImpl.class, "isWaitNeeded", globalTimeout, bundlesToWaitFor);
    assertTrue(isWaitNeeded);
  }

  /**
   * case if isWaitNeeded should return false, when for example it reached the global timeout limit.
   */
  @Test
  public void isWaitNeededNegativeForGlobalTimeout() throws Exception {
    final TestsManagerImpl testsManager = new TestsManagerImpl();
    long lastChange = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(1);
    long globalTimeout = lastChange - TimeUnit.SECONDS.toMillis(1);
    final Set<Bundle> bundlesToWaitFor = new HashSet<Bundle>();
    boolean isWaitNeeded = Whitebox.invokeMethod(TestsManagerImpl.class, "isWaitNeeded", globalTimeout, bundlesToWaitFor);
    assertFalse(isWaitNeeded);
  }

  /**
   * case if isWaitNeeded should return false, when for example it reached the global timeout limit.
   */
  @Test
  public void isWaitNeededNegativeForEmptyList() throws Exception {
    final TestsManagerImpl testsManager = new TestsManagerImpl();
    long lastChange = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(1);
    long globalTimeout = lastChange + TimeUnit.SECONDS.toMillis(10);
    final Set<Bundle> bundlesToWaitFor = new HashSet<Bundle>();
    boolean isWaitNeeded = Whitebox.invokeMethod(TestsManagerImpl.class, "isWaitNeeded", globalTimeout, bundlesToWaitFor);
    assertFalse(isWaitNeeded);
  }

  @Test
  public void waitForSystemStartupTimeout() {
    setupBundleContextMock(Bundle.INSTALLED);
    TestsManagerImpl.waitForSystemStartup();
    long timeWaitForSystemStartup = Whitebox.getInternalState(TestsManagerImpl.class, "timeWaitForSystemStartup");
    assertTrue(timeWaitForSystemStartup > TimeUnit.SECONDS.toMillis(2));
    assertTrue(timeWaitForSystemStartup < TimeUnit.SECONDS.toMillis(3));
    assertFalse((Boolean) Whitebox.getInternalState(TestsManagerImpl.class, "waitForSystemStartup"));
  }

  @Test
  public void waitForSystemStartupAllActiveBundles() {
    setupBundleContextMock(Bundle.ACTIVE);
    TestsManagerImpl.waitForSystemStartup();
    long timeWaitForSystemStartup = Whitebox.getInternalState(TestsManagerImpl.class, "timeWaitForSystemStartup");
    assertTrue(timeWaitForSystemStartup < TimeUnit.SECONDS.toMillis(2));
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