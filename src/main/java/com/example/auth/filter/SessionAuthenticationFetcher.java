package com.example.auth.filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.auth.AuthFeatureFlags;
import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.service.SessionValidationService;
import com.example.auth.util.SessionUtil;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.security.oauth2.endpoint.token.response.OauthAuthenticationMapper;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Singleton
public class SessionAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionAuthenticationFetcher.class);
    
    private static final String AUTH_METHOD_OIDC = "OIDC";
    private static final String OIDC_PROVIDER_NAME = "oidc";

    private final SessionValidationService sessionValidationService;
    private final AuthFeatureFlags authFeatureFlags;

    public SessionAuthenticationFetcher(SessionValidationService sessionValidationService,
                                        AuthFeatureFlags authFeatureFlags) {
        this.sessionValidationService = sessionValidationService;
        this.authFeatureFlags = authFeatureFlags;
    }

    @Override
    public Publisher<Authentication> fetchAuthentication(@NonNull HttpRequest<?> request) {
        if (!authFeatureFlags.isAuthenticationRequired()) {
            LOG.warn("oidc-auth and smart-id-auth are both disabled - bypassing authentication for {}", request.getPath());
            return Mono.just(new ServerAuthentication("anonymous-bypass", List.of(), Map.of()));
        }
        String sessionId = SessionUtil.findSessionCookie(request);
        if (sessionId == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> sessionValidationService.validate(sessionId))
            .subscribeOn(Schedulers.boundedElastic())
            .mapNotNull(user -> new ServerAuthentication(
                user.getUuid().toString(),
                user.getRoles(),
                buildAttributes(user)));
    }

    private Map<String, Object> buildAttributes(AuthenticatedUser user) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("user_ssn", user.getSsn());
        if (AUTH_METHOD_OIDC.equals(user.getAuthMethod())) {
            attributes.put(OauthAuthenticationMapper.PROVIDER_KEY, OIDC_PROVIDER_NAME);
        }
        return attributes;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
