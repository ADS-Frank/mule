/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.mule.api.lifecycle.CreateException;
import org.mule.api.security.tls.TlsConfiguration;
import org.mule.tck.AbstractMuleTestCase;
import org.mule.util.ClassUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.junit.Test;

public class TlsConfigurationTestCase extends AbstractMuleTestCase
{


    private static final String SUPPORTED_CIPHER_SUITE = "TLS_DHE_DSS_WITH_AES_128_CBC_SHA";
    private static final String SUPPORTED_PROTOCOL = "TLSv1";

    @Test
    public void testEmptyConfiguration() throws Exception
    {
        TlsConfiguration configuration = new TlsConfiguration(TlsConfiguration.DEFAULT_KEYSTORE);
        try
        {
            configuration.initialise(false, TlsConfiguration.JSSE_NAMESPACE);
            fail("no key password");
        }
        catch (IllegalArgumentException e)
        {
            assertNotNull("expected", e);
        }
        configuration.setKeyPassword("mulepassword");
        try
        {
            configuration.initialise(false, TlsConfiguration.JSSE_NAMESPACE);
            fail("no store password");
        }
        catch (IllegalArgumentException e)
        {
            assertNotNull("expected", e);
        }
        configuration.setKeyStorePassword("mulepassword");
        configuration.setKeyStore(""); // guaranteed to not exist
        try
        {
            configuration.initialise(false, TlsConfiguration.JSSE_NAMESPACE);
            fail("no keystore");
        }
        catch (Exception e)
        {
            assertNotNull("expected", e);
        }
    }

    public void testSimpleSocket() throws Exception
    {
        TlsConfiguration configuration = new TlsConfiguration(TlsConfiguration.DEFAULT_KEYSTORE);
        configuration.setKeyPassword("mulepassword");
        configuration.setKeyStorePassword("mulepassword");
        configuration.setKeyStore("clientKeystore");
        configuration.initialise(false, TlsConfiguration.JSSE_NAMESPACE);
        SSLSocketFactory socketFactory = configuration.getSocketFactory();
        assertTrue("socket is useless", socketFactory.getSupportedCipherSuites().length > 0);
    }

    public void testCipherSuitesFromConfigFile() throws Exception
    {
        File configFile = createConfigFile();

        try
        {
            TlsConfiguration tlsConfiguration = new TlsConfiguration(TlsConfiguration.DEFAULT_KEYSTORE);
            tlsConfiguration.initialise(true, TlsConfiguration.JSSE_NAMESPACE);

            SSLSocket socket = (SSLSocket) tlsConfiguration.getSocketFactory().createSocket();
            SSLServerSocket serverSocket = (SSLServerSocket) tlsConfiguration.getServerSocketFactory().createServerSocket();

            assertArrayEquals(new String[] {SUPPORTED_CIPHER_SUITE}, socket.getEnabledCipherSuites());
            assertArrayEquals(new String[] {SUPPORTED_CIPHER_SUITE}, serverSocket.getEnabledCipherSuites());
        }
        finally
        {
            configFile.delete();
        }
    }

    public void testProtocolsFromConfigFile() throws Exception
    {
        File configFile = createConfigFile();

        try
        {
            TlsConfiguration tlsConfiguration = new TlsConfiguration(TlsConfiguration.DEFAULT_KEYSTORE);
            tlsConfiguration.initialise(true, TlsConfiguration.JSSE_NAMESPACE);

            SSLSocket socket = (SSLSocket) tlsConfiguration.getSocketFactory().createSocket();
            SSLServerSocket serverSocket = (SSLServerSocket) tlsConfiguration.getServerSocketFactory().createServerSocket();

            assertArrayEquals(new String[] {SUPPORTED_PROTOCOL}, socket.getEnabledProtocols());
            assertArrayEquals(new String[] {SUPPORTED_PROTOCOL}, serverSocket.getEnabledProtocols());
        }
        finally
        {
            configFile.delete();
        }
    }


    private File createConfigFile() throws IOException
    {
        String path = ClassUtils.getClassPathRoot(getClass()).getPath();
        File file = new File(path, TlsConfiguration.DEFAULT_PROPERTIES_FILE);

        PrintWriter writer = new PrintWriter(file, "UTF-8");
        writer.println("enabledCipherSuites=UNSUPPORTED," + SUPPORTED_CIPHER_SUITE);
        writer.println("enabledProtocols=UNSUPPORTED," + SUPPORTED_PROTOCOL);
        writer.close();

        return file;
    }
    

}


