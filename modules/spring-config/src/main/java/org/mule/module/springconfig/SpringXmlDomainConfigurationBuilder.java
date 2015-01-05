/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.springconfig;

import org.mule.api.MuleContext;
import org.mule.api.config.ConfigurationException;
import org.mule.config.ConfigResource;

import org.springframework.context.ApplicationContext;

import org.osgi.framework.BundleContext;

/**
 * Spring configuration builder used to create domains.
 */
public class SpringXmlDomainConfigurationBuilder extends SpringXmlConfigurationBuilder
{

    public SpringXmlDomainConfigurationBuilder(String configResources, BundleContext bundleContext) throws ConfigurationException
    {
        super(configResources, bundleContext);
        setUseMinimalConfigResource(true);
    }

    @Override
    protected ApplicationContext createApplicationContext(MuleContext muleContext, ConfigResource[] configResources, BundleContext bundleContext) throws Exception
    {
        return new MuleDomainContext(muleContext, configResources);
    }
}
