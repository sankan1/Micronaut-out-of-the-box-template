package com.example.auth.oidc;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.security.oauth2.endpoint.authorization.request.DefaultAuthorizationRedirectHandler;
import io.micronaut.security.oauth2.endpoint.authorization.request.OpenIdAuthorizationRequest;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@Replaces(DefaultAuthorizationRedirectHandler.class)
public class CustomAuthorizationRedirectHandler extends DefaultAuthorizationRedirectHandler {

    @Override
    protected void populateUiLocales(OpenIdAuthorizationRequest authorizationRequest,
                                     Map<String, Object> parameters) {
        parameters.put("ui_locales", "et");
    }
}
