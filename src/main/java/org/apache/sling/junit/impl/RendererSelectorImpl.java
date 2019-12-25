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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.sling.junit.Renderer;
import org.apache.sling.junit.RendererFactory;
import org.apache.sling.junit.RendererSelector;
import org.apache.sling.junit.TestSelector;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default RendererSelector */
@Component(
    service = RendererSelector.class,
    immediate = false
)
public class RendererSelectorImpl implements RendererSelector {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final List<Renderer> renderers = new ArrayList<Renderer>();
    private ServiceTracker<Renderer,Renderer> renderersTracker;
    private int renderersTrackerTrackingCount = -1;
    private BundleContext bundleContext;
    
    public Collection<Renderer> getRenderers() {
        return Collections.unmodifiableCollection(renderers);
    }
    
    public Renderer getRenderer(TestSelector selector) {
        log.debug("Detected renderers {}", renderers);
        if(renderersTracker.getTrackingCount() != renderersTrackerTrackingCount) {
            log.debug("Rebuilding list of {}", Renderer.class.getSimpleName());
            renderersTrackerTrackingCount = renderersTracker.getTrackingCount();
            final ServiceReference<Renderer> [] refs = renderersTracker.getServiceReferences();
            renderers.clear();
            if(refs != null) {
                for(ServiceReference<Renderer> ref : refs) {
                    renderers.add( (Renderer)bundleContext.getService(ref) );
                }
            }
            log.info("List of {} rebuilt: {}", 
                    Renderer.class.getSimpleName(),
                    renderers);
        }
        
        for(Renderer r : renderers) {
            if(r.appliesTo(selector)) {
                if(r instanceof RendererFactory) {
                    return ((RendererFactory)r).createRenderer();
                }
                throw new UnsupportedOperationException("Renderers must implement RendererFactory, this one does not:" + r);
            }
        }
        
        return null;
    }
    
    @Activate
    @Modified
    protected void activate(ComponentContext ctx) throws ServletException, NamespaceException {
        bundleContext = ctx.getBundleContext();
        renderersTracker = new ServiceTracker<Renderer,Renderer>(ctx.getBundleContext(), Renderer.class.getName(), null);
        renderersTracker.open();
    }
    
    @Deactivate
    protected void deactivate(ComponentContext ctx) throws ServletException, NamespaceException {
        if(renderersTracker != null) {
            renderersTracker.close();
            renderersTracker = null;
        }
        bundleContext = null;
    }
}
