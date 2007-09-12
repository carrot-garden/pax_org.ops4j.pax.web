/*
 * Copyright 2007 Alin Dreghiciu.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.web.extender.internal;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Tracks http services.
 *
 * @author Alin Dreghiciu
 * @since August 21, 2007
 */
public class HttpServiceTracker
    extends ServiceTracker
{

    /**
     * Logger.
     */
    private static final Log LOGGER = LogFactory.getLog( HttpServiceTracker.class );
    /**
     * An array of listeners to be notified when service come and go.
     */
    private HttpServiceListener[] m_listeners;
    /**
     * The current used http service;
     */
    private HttpService m_httpService;
    /**
     * Lock for thread safe http service usage.
     */
    Lock lock;

    /**
     * Tracks Http Services.
     *
     * @param bundleContext a bundle context; mandatory
     * @param listeners     an array of listeners; mandatory
     */
    public HttpServiceTracker( final BundleContext bundleContext, final HttpServiceListener[] listeners )
    {
        super( validateBundleContext( bundleContext ), HttpService.class.getName(), null );
        if( listeners == null )
        {
            throw new IllegalArgumentException( "Http Service listeners cannot be null" );
        }
        m_listeners = listeners;
        lock = new ReentrantLock();
    }

    /**
     * Validates that the bundle context is not null.
     * If null will throw IllegalArgumentException
     *
     * @param bundleContext a bundle context
     *
     * @return the bundle context if not null
     */
    private static BundleContext validateBundleContext( BundleContext bundleContext )
    {
        if( bundleContext == null )
        {
            throw new IllegalArgumentException( "Bundle context cannot be null" );
        }
        return bundleContext;
    }

    /**
     * Gets the service if one is not already available and notify listeners.
     *
     * @see org.osgi.util.tracker.ServiceTracker#addingService(org.osgi.framework.ServiceReference)
     */
    @Override
    public Object addingService( final ServiceReference serviceReference )
    {
        LOGGER.debug( "HttpService available [" + serviceReference + "]" );
        lock.lock();
        HttpService httpService = null;
        try
        {
            if( m_httpService != null )
            {
                return super.addingService( serviceReference );
            }
            m_httpService = (HttpService) super.addingService( serviceReference );
            httpService = m_httpService;
        }
        finally
        {
            lock.unlock();
        }
        for( HttpServiceListener listener : m_listeners )
        {
            listener.available( httpService );
        }
        return httpService;
    }

    /**
     * Notify listeners that the http service becomed unavailable. Then looks for another one and if available notifies
     * listeners.
     *
     * @see org.osgi.util.tracker.ServiceTracker#removedService(org.osgi.framework.ServiceReference,Object)
     */
    @Override
    public void removedService( final ServiceReference serviceReference, final Object service )
    {
        LOGGER.debug( "HttpService removed [" + serviceReference + "]" );
        lock.lock();
        HttpService httpService = null;
        try
        {
            super.removedService( serviceReference, service );
            if( m_httpService != service )
            {
                return;
            }
            httpService = m_httpService;
            m_httpService = null;
        }
        finally
        {
            lock.unlock();
        }
        for( HttpServiceListener listener : m_listeners )
        {
            listener.unavailable( httpService );
        }

        // look for another service
        lock.lock();
        try
        {
            m_httpService = (HttpService) getService();
            if( m_httpService == null )
            {
                return;
            }
            httpService = m_httpService;
        }
        finally
        {
            lock.unlock();
        }
        for( HttpServiceListener listener : m_listeners )
        {
            listener.available( httpService );
        }
    }

}