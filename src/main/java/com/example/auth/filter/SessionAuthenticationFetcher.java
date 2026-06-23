package com.example.auth.filter;

import com.example.auth.user.service.SessionValidationService;
import com.example.auth.util.SessionUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Singleton
public class SessionAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {

    private final SessionValidationService sessionValidationService;

    public SessionAuthenticationFetcher(SessionValidationService sessionValidationService) {
        this.sessionValidationService = sessionValidationService;
    }

    @Override
    public Publisher<Authentication> fetchAuthentication(@NonNull HttpRequest<?> request) {
        String sessionId = SessionUtil.findSessionCookie(request);
        if (sessionId == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> sessionValidationService.validate(sessionId))
            .subscribeOn(Schedulers.boundedElastic())
            .mapNotNull(user -> new ServerAuthentication(
                user.getUuid().toString(),
                user.getRoles(),
                Map.of("user_ssn", user.getSsn())));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
