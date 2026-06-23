package com.example.auth.user.service;

import com.example.auth.oidc.OidcTokenClient;
import com.example.auth.user.persistence.port.UserAuthenticationPort;
import com.example.auth.util.SessionUtil;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class LogoutService {

    private static final Logger LOG = LoggerFactory.getLogger(LogoutService.class);

    private final UserAuthenticationPort userAuthenticationPort;
    private final OidcTokenClient oidcTokenClient;
    private final String clientId;
    private final String clientSecret;

    public LogoutService(UserAuthenticationPort userAuthenticationPort,
                         OidcTokenClient oidcTokenClient,
                         @Value("${micronaut.security.oauth2.clients.oidc.client-id}") String clientId,
                         @Value("${micronaut.security.oauth2.clients.oidc.client-secret}") String clientSecret) {
        this.userAuthenticationPort = userAuthenticationPort;
        this.oidcTokenClient = oidcTokenClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public void logout(String sessionIdValue) {
        SessionUtil.parseSessionId(sessionIdValue).ifPresent(sessionId ->
            userAuthenticationPort.deleteBySessionIdReturning(sessionId).ifPresent(tokens -> {
                revokeQuietly(tokens.accessToken());
                revokeQuietly(tokens.refreshToken());
            }));
    }

    private void revokeQuietly(String token) {
        if (token == null) {
            return;
        }
        try {
            Map<String, String> form = new HashMap<>();
            form.put("token", token);
            form.put("client_id", clientId);
            form.put("client_secret", clientSecret);
            oidcTokenClient.revoke(form);
        } catch (Exception revokeFailure) {
            LOG.warn("Token revocation at provider failed: {}", revokeFailure.getMessage());
        }
    }
}
