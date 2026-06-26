package com.example.auth.oidc;

import com.example.auth.util.SessionUtil;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.config.RedirectConfiguration;
import io.micronaut.security.config.RedirectService;
import io.micronaut.security.errors.PriorToLoginPersistence;
import io.micronaut.security.token.cookie.AccessTokenCookieConfiguration;
import io.micronaut.security.token.cookie.RefreshTokenCookieConfiguration;
import io.micronaut.security.token.cookie.TokenCookieLoginHandler;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.generator.AccessTokenConfiguration;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;

@Singleton
@Replaces(TokenCookieLoginHandler.class)
public class SessionCookieLoginHandler extends TokenCookieLoginHandler {

    private final RedirectConfiguration redirectConfiguration;

    public SessionCookieLoginHandler(RedirectService redirectService,
                                     RedirectConfiguration redirectConfiguration,
                                     AccessTokenCookieConfiguration accessTokenCookieConfiguration,
                                     RefreshTokenCookieConfiguration refreshTokenCookieConfiguration,
                                     AccessTokenConfiguration accessTokenConfiguration,
                                     AccessRefreshTokenGenerator accessRefreshTokenGenerator,
                                     @Nullable PriorToLoginPersistence<HttpRequest<?>, MutableHttpResponse<?>> priorToLoginPersistence) {
        super(redirectService, redirectConfiguration, accessTokenCookieConfiguration,
            refreshTokenCookieConfiguration, accessTokenConfiguration,
            accessRefreshTokenGenerator, priorToLoginPersistence);
        this.redirectConfiguration = redirectConfiguration;
    }

    @Override
    public List<Cookie> getCookies(Authentication authentication, HttpRequest<?> request) {
        String sessionId = (String) authentication.getAttributes().get(SessionUtil.SESSION_ID_ATTRIBUTE);
        Cookie authCookie = Cookie.of(SessionUtil.AUTH_COOKIE_NAME, sessionId);
        authCookie.configure(accessTokenCookieConfiguration, request.isSecure());
        accessTokenCookieConfiguration.getCookieMaxAge().ifPresent(authCookie::maxAge);
        return List.of(authCookie);
    }

    /**
     * Micronaut Security 4.10.1's RedirectService mangles absolute redirect.login-success
     * URLs by string-concatenating the server context path onto them (no scheme/host check),
     * e.g. "http://localhost:9000/login" becomes "/apihttp://localhost:9000/login". Building
     * the redirect from the raw, unmangled RedirectConfiguration value instead.
     */
    @Override
    public MutableHttpResponse<?> loginSuccess(Authentication authentication, HttpRequest<?> request) {
        String loginSuccess = redirectConfiguration.getLoginSuccess();
        MutableHttpResponse<?> response = loginSuccess == null
            ? HttpResponse.ok()
            : HttpResponse.status(HttpStatus.SEE_OTHER).headers(headers -> headers.location(URI.create(loginSuccess)));
        return applyCookies(response, getCookies(authentication, request));
    }
}
