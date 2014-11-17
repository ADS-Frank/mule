/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.oauth2.internal.authorizationcode;

import org.mule.DefaultMuleEvent;
import org.mule.api.DefaultMuleException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleRuntimeException;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.transport.PropertyScope;
import org.mule.config.i18n.CoreMessages;
import org.mule.module.http.api.HttpConstants;
import org.mule.module.oauth2.internal.OAuthConstants;
import org.mule.module.oauth2.internal.StateDecoder;
import org.mule.module.oauth2.internal.TokenResponseProcessor;
import org.mule.module.oauth2.internal.authorizationcode.state.ResourceOwnerOAuthContext;

import java.util.HashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

/**
 * Represents the Token request and response handling behaviour of the OAuth 2.0 dance. It provides support for
 * standard OAuth server implementations of the token acquisition part plus a couple of configuration attributes to
 * customize behaviour.
 */
public class AutoAuthorizationCodeTokenRequestHandler extends AbstractAuthorizationCodeTokenRequestHandler
{

    public static final String STATUS_CODE_FLOW_VAR_NAME = "statusCode";
    public static final String OK_STATUS_CODE = "200";
    public static final String REDIRECT_STATUS_CODE = "302";
    private TokenResponseConfiguration tokenResponseConfiguration = new TokenResponseConfiguration();

    public void setTokenResponseConfiguration(final TokenResponseConfiguration tokenResponseConfiguration)
    {
        this.tokenResponseConfiguration = tokenResponseConfiguration;
    }

    /**
     * Starts the http listener for the redirect url callback. This will create a flow with an endpoint on the
     * provided OAuth redirect uri parameter. The OAuth Server will call this url to provide the authentication code
     * required to get the access token.
     *
     * @throws MuleException if the listener couldn't be created.
     */
    public void init() throws MuleException
    {
        createListenerForRedirectUrl();
    }

    protected MessageProcessor createRedirectUrlProcessor()
    {
        return new MessageProcessor()
        {
            @Override
            public MuleEvent process(MuleEvent event) throws MuleException
            {
                Map<String, String> queryParams = event.getMessage().getInboundProperty(HttpConstants.RequestProperties.HTTP_QUERY_PARAMS);
                String authorizationCode = queryParams.get(OAuthConstants.CODE_PARAMETER);
                String state = queryParams.get(OAuthConstants.STATE_PARAMETER);
                event.setFlowVariable(STATUS_CODE_FLOW_VAR_NAME, OK_STATUS_CODE);
                if (authorizationCode == null)
                {
                    event.getMessage().setPayload("Failure retrieving access token.\n OAuth Server uri from callback: " + event.getMessage().getInboundProperty("http.request.uri"));
                }
                else
                {
                    setMapPayloadWithTokenRequestParameters(event, authorizationCode);
                    final MuleEvent tokenUrlResposne = invokeTokenUrl(event);
                    decodeStateAndUpdateOAuthUserState(tokenUrlResposne, state);
                    event.getMessage().setPayload("Successfully retrieved access token!");
                }
                final StateDecoder stateDecoder = new StateDecoder(state);
                final String onCompleteRedirectToValue = stateDecoder.decodeOnCompleteRedirectTo();
                if (!StringUtils.isEmpty(onCompleteRedirectToValue))
                {
                    event.setFlowVariable(STATUS_CODE_FLOW_VAR_NAME, REDIRECT_STATUS_CODE);
                    event.getMessage().setOutboundProperty("Location", onCompleteRedirectToValue);
                }
                return event;
            }
        };
    }

    private void setMapPayloadWithTokenRequestParameters(final MuleEvent event, final String authorizationCode)
    {
        final HashMap<String, String> formData = new HashMap<String, String>();
        formData.put(OAuthConstants.CODE_PARAMETER, authorizationCode);
        formData.put(OAuthConstants.CLIENT_ID_PARAMETER, getOauthConfig().getClientId());
        formData.put(OAuthConstants.CLIENT_SECRET_PARAMETER, getOauthConfig().getClientSecret());
        formData.put(OAuthConstants.GRANT_TYPE_PARAMETER, OAuthConstants.GRANT_TYPE_AUTHENTICATION_CODE);
        formData.put(OAuthConstants.REDIRECT_URI_PARAMETER, getOauthConfig().getRedirectionUrl());
        event.getMessage().setPayload(formData);
    }

    private void setMapPayloadWithRefreshTokenRequestParameters(final MuleEvent event, final String refreshToken)
    {
        final HashMap<String, String> formData = new HashMap<String, String>();
        formData.put(OAuthConstants.REFRESH_TOKEN_PARAMETER, refreshToken);
        formData.put(OAuthConstants.CLIENT_ID_PARAMETER, getOauthConfig().getClientId());
        formData.put(OAuthConstants.CLIENT_SECRET_PARAMETER, getOauthConfig().getClientSecret());
        formData.put(OAuthConstants.GRANT_TYPE_PARAMETER, OAuthConstants.GRANT_TYPE_REFRESH_TOKEN);
        formData.put(OAuthConstants.REDIRECT_URI_PARAMETER, getOauthConfig().getRedirectionUrl());
        event.getMessage().setPayload(formData);
    }

    private void decodeStateAndUpdateOAuthUserState(final MuleEvent tokenUrlResponse, final String originalState) throws org.mule.api.registry.RegistrationException
    {
        final StateDecoder stateDecoder = new StateDecoder(originalState);
        String decodedState = stateDecoder.decodeOriginalState();
        String encodedResourceOwnerId = stateDecoder.decodeResourceOwnerId();
        String resourceOwnerId = encodedResourceOwnerId == null ? ResourceOwnerOAuthContext.DEFAULT_RESOURCE_OWNER_ID : encodedResourceOwnerId;

        final ResourceOwnerOAuthContext stateForUser = getOauthConfig().getUserOAuthContext().getContextForResourceOwner(resourceOwnerId);

        processTokenUrlResponse(tokenUrlResponse, decodedState, stateForUser);

        getOauthConfig().getUserOAuthContext().updateResourceOwnerOAuthContext(stateForUser);
    }

    private void processTokenUrlResponse(MuleEvent tokenUrlResponse, String decodedState, ResourceOwnerOAuthContext stateForUser)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Update OAuth Context for resourceOwnerId %s", stateForUser.getResourceOwnerId());
        }
        final TokenResponseProcessor tokenResponseProcessor = TokenResponseProcessor.createAuthorizationCodeProcessor(tokenResponseConfiguration, getMuleContext().getExpressionManager());
        tokenResponseProcessor.process(tokenUrlResponse);

        stateForUser.setAccessToken(tokenResponseProcessor.getAccessToken());
        stateForUser.setRefreshToken(tokenResponseProcessor.getRefreshToken());
        stateForUser.setExpiresIn(tokenResponseProcessor.getExpiresIn());

        //State may be null because there's no state or because this was called after refresh token.
        if (decodedState != null)
        {
            stateForUser.setState(decodedState);
        }

        final Map<String, Object> customResponseParameters = tokenResponseProcessor.getCustomResponseParameters();
        for (String paramName : customResponseParameters.keySet())
        {
            final Object paramValue = customResponseParameters.get(paramName);
            if (paramValue != null)
            {
                stateForUser.getTokenResponseParameters().put(paramName, paramValue);
            }
        }

        if (logger.isDebugEnabled())
        {
            logger.debug("New OAuth State for resourceOwnerId %s is: accessToken(%s), refreshToken(%s), expiresIn(%s), state(%s)", stateForUser.getResourceOwnerId(), stateForUser.getAccessToken(), stateForUser.getRefreshToken(), stateForUser.getExpiresIn(), stateForUser.getState());
        }
    }

    /**
     * Executes a refresh token for a particular user. It will call the OAuth Server token url
     * and provide the refresh token to get a new access token.
     *
     * @param currentEvent              the event being processed when the refresh token was required.
     * @param resourceOwnerOAuthContext oauth context for who we need to update the access token.
     */
    public void doRefreshToken(final MuleEvent currentEvent, final ResourceOwnerOAuthContext resourceOwnerOAuthContext)
    {
        try
        {
            final MuleEvent muleEvent = DefaultMuleEvent.copy(currentEvent);
            muleEvent.getMessage().clearProperties(PropertyScope.OUTBOUND);
            final String userRefreshToken = resourceOwnerOAuthContext.getRefreshToken();
            if (userRefreshToken == null)
            {
                throw new DefaultMuleException(CoreMessages.createStaticMessage("The user with user id %s has no refresh token in his OAuth state so we can't execute the refresh token call", resourceOwnerOAuthContext.getResourceOwnerId()));
            }
            setMapPayloadWithRefreshTokenRequestParameters(muleEvent, userRefreshToken);
            final MuleEvent refreshTokenResponse = invokeTokenUrl(muleEvent);

            processTokenUrlResponse(refreshTokenResponse, null, resourceOwnerOAuthContext);
        }
        catch (Exception e)
        {
            throw new MuleRuntimeException(e);
        }
    }
}
