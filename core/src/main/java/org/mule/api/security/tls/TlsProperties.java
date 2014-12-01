/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.api.security.tls;


import org.mule.util.IOUtils;
import org.mule.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TlsProperties
{

    private static final Logger logger = LoggerFactory.getLogger(TlsProperties.class);

    private String[] enabledCipherSuites;
    private String[] enabledProtocols;

    public String[] getEnabledCipherSuites()
    {
        return enabledCipherSuites;
    }

    public String[] getEnabledProtocols()
    {
        return enabledProtocols;
    }

    public void load(String fileName)
    {
        Properties properties = new Properties();
        try
        {
            InputStream config = IOUtils.getResourceAsStream(fileName, TlsProperties.class);

            if (config == null)
            {
                logger.warn(String.format("File %s not found, using default configuration.", fileName));
            }
            else
            {
                properties.load(config);

                String enabledCipherSuitesProperty = properties.getProperty("enabledCipherSuites");
                String enabledProtocolsProperty = properties.getProperty("enabledProtocols");

                if (enabledCipherSuitesProperty != null)
                {
                    enabledCipherSuites = StringUtils.splitAndTrim(enabledCipherSuitesProperty, ",");

                }
                if (enabledProtocolsProperty != null)
                {
                    enabledProtocols = StringUtils.splitAndTrim(enabledProtocolsProperty, ",");
                }
            }
        }
        catch (IOException e)
        {
            logger.warn(String.format("Cannot read file %s, using default configuration", fileName), e);
        }
    }
}
