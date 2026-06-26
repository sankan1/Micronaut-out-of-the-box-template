package com.example.auth.oidc;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.security.oauth2.client.DefaultOpenIdProviderMetadata;
import io.micronaut.security.oauth2.client.OpenIdProviderMetadata;
import io.micronaut.security.oauth2.configuration.OauthClientConfiguration;
import io.micronaut.security.oauth2.endpoint.endsession.request.EndSessionEndpoint;
import io.micronaut.security.oauth2.endpoint.endsession.response.EndSessionCallbackUrlBuilder;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

/**
 * Micronaut Security's built-in Keycloak detection ({@code AuthorizationServer#infer}) only
 * recognizes the legacy issuer path "/auth/realms/{realm}", removed in Keycloak 17. Our issuer
 * ("/realms/micronaut") doesn't match, so {@code DefaultOpenIdClient#supportsEndSession()}
 * silently returns false and the "oidc" provider's end-session redirect never gets built.
 * Registering this bean named "oidc" short-circuits {@code EndSessionEndpointResolver}'s
 * lookup before it falls back to that broken heuristic.
 */
@Factory
class KeycloakEndSessionEndpointFactory {

    @Singleton
    @Named("oidc")
    EndSessionEndpoint oidcEndSessionEndpoint(@Named("oidc") OauthClientConfiguration clientConfiguration,
                                              @Named("oidc") BeanProvider<DefaultOpenIdProviderMetadata> openIdProviderMetadata,
                                              EndSessionCallbackUrlBuilder endSessionCallbackUrlBuilder) {
        Supplier<OpenIdProviderMetadata> metadataSupplier = SupplierUtil.memoized(openIdProviderMetadata::get);
        return new ModernKeycloakEndSessionEndpoint(endSessionCallbackUrlBuilder, clientConfiguration, metadataSupplier);
    }
}
