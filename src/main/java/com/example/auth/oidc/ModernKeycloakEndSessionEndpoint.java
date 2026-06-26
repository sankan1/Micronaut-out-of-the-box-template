package com.example.auth.oidc;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.oauth2.client.OpenIdProviderMetadata;
import io.micronaut.security.oauth2.configuration.OauthClientConfiguration;
import io.micronaut.security.oauth2.endpoint.endsession.request.AbstractEndSessionRequest;
import io.micronaut.security.oauth2.endpoint.endsession.response.EndSessionCallbackUrlBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Keycloak 19+ removed the legacy "redirect_uri" logout parameter (used by Micronaut Security's
 * built-in {@code KeycloakEndSessionEndpoint}) in favor of the standard RP-Initiated Logout
 * parameters: "post_logout_redirect_uri" plus either "id_token_hint" or "client_id". We don't
 * persist the id_token, so this uses "client_id", which Keycloak accepts as an alternative.
 */
class ModernKeycloakEndSessionEndpoint extends AbstractEndSessionRequest {

    private static final String PARAM_POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String LOGOUT_URI = "/protocol/openid-connect/logout";

    private final OauthClientConfiguration clientConfiguration;

    ModernKeycloakEndSessionEndpoint(EndSessionCallbackUrlBuilder endSessionCallbackUrlBuilder,
                                     OauthClientConfiguration clientConfiguration,
                                     Supplier<OpenIdProviderMetadata> providerMetadata) {
        super(endSessionCallbackUrlBuilder, clientConfiguration, providerMetadata);
        this.clientConfiguration = clientConfiguration;
    }

    @Override
    protected String getUrl() {
        OpenIdProviderMetadata openIdProviderMetadata = providerMetadataSupplier.get();
        return openIdProviderMetadata.getEndSessionEndpoint() != null
            ? openIdProviderMetadata.getEndSessionEndpoint()
            : StringUtils.prependUri(openIdProviderMetadata.getIssuer(), LOGOUT_URI);
    }

    @Override
    protected Map<String, Object> getArguments(HttpRequest<?> originating, Authentication authentication) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(PARAM_POST_LOGOUT_REDIRECT_URI, getRedirectUri(originating));
        arguments.put(PARAM_CLIENT_ID, clientConfiguration.getClientId());
        return arguments;
    }
}
