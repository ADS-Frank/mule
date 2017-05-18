/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.connectivity.oauth;

/**
 * Groups the sum of all the parameters that a user configured in order to provision
 * an OAuth access token callback
 *
 * @since 4.0
 */
public final class OAuthCallbackConfig {

  private final String listenerConfig;
  private final String callbackPath;
  private final String localAuthorizePath;

  public OAuthCallbackConfig(String listenerConfig, String callbackPath, String localAuthorizePath) {
    this.listenerConfig = listenerConfig;
    this.callbackPath = callbackPath;
    this.localAuthorizePath = localAuthorizePath;
  }

  public String getListenerConfig() {
    return listenerConfig;
  }

  public String getCallbackPath() {
    return callbackPath;
  }

  public String getLocalAuthorizePath() {
    return localAuthorizePath;
  }
}
