/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.api.registry;

import org.mule.api.MuleContext;

import java.util.Properties;

public interface TransportServiceDescriptorFactory
{

    public static final String TRANSPORT_SERVICE_TYPE = "transport";

    ServiceDescriptor create(MuleContext muleContext, Properties overrides) throws ServiceException;
}
