package com.example.auth.oidc;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.CookieConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.CookieBasedAuthenticationModeCondition;
import io.micronaut.security.config.RedirectConfiguration;
import io.micronaut.security.config.RedirectService;
import io.micronaut.security.handlers.LogoutHandler;
import io.micronaut.security.oauth2.ProviderResolver;
import io.micronaut.security.oauth2.client.OpenIdClient;
import io.micronaut.security.token.cookie.AccessTokenCookieConfiguration;
import io.micronaut.security.token.cookie.RefreshTokenCookieConfiguration;
import io.micronaut.security.token.cookie.TokenCookieClearerLogoutHandler;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Clears the local session cookie and, for users authenticated via an OpenID Connect
 * provider that supports it, also redirects to the provider's end-session endpoint so the
 * provider's own SSO session ends. Without this, the provider would silently re-authenticate
 * the browser on the very next login-required check, making logout appear to do nothing.
 */
@Requires(classes = {MutableHttpResponse.class, HttpRequest.class})
@Requires(condition = CookieBasedAuthenticationModeCondition.class)
@Replaces(TokenCookieClearerLogoutHandler.class)
@Singleton
public class OidcEndSessionLogoutHandler implements LogoutHandler<HttpRequest<?>, MutableHttpResponse<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(OidcEndSessionLogoutHandler.class);

    private final String logout;
    private final AccessTokenCookieConfiguration accessTokenCookieConfiguration;
    private final RefreshTokenCookieConfiguration refreshTokenCookieConfiguration;
    private final SecurityService securityService;
    private final ProviderResolver providerResolver;
    private final BeanContext beanContext;

    public OidcEndSessionLogoutHandler(AccessTokenCookieConfiguration accessTokenCookieConfiguration,
                                       RefreshTokenCookieConfiguration refreshTokenCookieConfiguration,
                                       RedirectConfiguration redirectConfiguration,
                                       RedirectService redirectService,
                                       SecurityService securityService,
                                       ProviderResolver providerResolver,
                                       BeanContext beanContext) {
        this.accessTokenCookieConfiguration = accessTokenCookieConfiguration;
        this.refreshTokenCookieConfiguration = refreshTokenCookieConfiguration;
        this.logout = redirectConfiguration.isEnabled() ? redirectService.logoutUrl() : null;
        this.securityService = securityService;
        this.providerResolver = providerResolver;
        this.beanContext = beanContext;
    }

    @Override
    public MutableHttpResponse<?> logout(HttpRequest<?> request) {
        MutableHttpResponse<?> response = endSessionRedirect(request).orElseGet(this::localRedirect);
        clearCookie(accessTokenCookieConfiguration, response, request.isSecure());
        if (refreshTokenCookieConfiguration != null) {
            clearCookie(refreshTokenCookieConfiguration, response, request.isSecure());
        }
        return response;
    }

    private Optional<MutableHttpResponse<?>> endSessionRedirect(HttpRequest<?> request) {
        Authentication authentication = securityService.getAuthentication().orElse(null);
        if (authentication == null) {
            return Optional.empty();
        }
        return providerResolver.resolveProvider(authentication)
            .flatMap(provider -> beanContext.findBean(OpenIdClient.class, Qualifiers.byName(provider)))
            .filter(OpenIdClient::supportsEndSession)
            .flatMap(client -> client.endSessionRedirect(request, authentication));
    }

    private MutableHttpResponse<?> localRedirect() {
        if (logout == null) {
            return HttpResponse.ok();
        }
        try {
            return HttpResponse.seeOther(new URI(logout));
        } catch (URISyntaxException e) {
            LOG.warn("Invalid logout redirect URI [{}]", logout, e);
            return HttpResponse.serverError();
        }
    }

    private void clearCookie(CookieConfiguration cookieConfiguration, MutableHttpResponse<?> response, boolean isSecure) {
        Cookie cookie = Cookie.of(cookieConfiguration.getCookieName(), "");
        cookie.configure(cookieConfiguration, isSecure);
        cookie.maxAge(0);
        response.cookie(cookie);
    }
}
