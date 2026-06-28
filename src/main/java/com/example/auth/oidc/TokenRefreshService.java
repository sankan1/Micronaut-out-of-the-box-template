package com.example.auth.oidc;

import com.example.auth.user.persistence.port.UserAuthenticationPort;
import com.example.auth.user.persistence.port.UserAuthenticationPort.SessionTokens;
import com.example.auth.user.service.UserRoleService;
import com.example.auth.util.SessionUtil;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class TokenRefreshService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenRefreshService.class);

    private final OidcTokenClient oidcTokenClient;
    private final UserAuthenticationPort userAuthenticationPort;
    private final UserRoleService userRoleService;
    private final String clientId;
    private final String clientSecret;

    public TokenRefreshService(OidcTokenClient oidcTokenClient,
                               UserAuthenticationPort userAuthenticationPort,
                               UserRoleService userRoleService,
                               @Value("${micronaut.security.oauth2.clients.oidc.client-id}") String clientId,
                               @Value("${micronaut.security.oauth2.clients.oidc.client-secret}") String clientSecret) {
        this.oidcTokenClient = oidcTokenClient;
        this.userAuthenticationPort = userAuthenticationPort;
        this.userRoleService = userRoleService;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW, timeout = 60)
    public Optional<List<String>> refresh(Long userAuthenticationId) {
        SessionTokens auth = userAuthenticationPort.findForUpdate(userAuthenticationId).orElse(null);
        if (auth == null) {
            return Optional.empty();
        }

        if (SessionUtil.isStillValid(auth.tokenExpiration())) {
            return Optional.of(userRoleService.getRoles(auth.userId()));
        }

        if (auth.refreshToken() == null) {
            terminate(auth, "refresh token missing");
            return Optional.empty();
        }

        return refreshUpstream(auth);
    }

    private Optional<List<String>> refreshUpstream(SessionTokens auth) {
        OidcTokenResponse response;
        try {
            response = oidcTokenClient.refresh(refreshForm(auth.refreshToken()));
        } catch (HttpClientResponseException httpError) {
            if (isInvalidGrant(httpError)) {
                terminate(auth, "provider rejected refresh token (invalid_grant)");
                return Optional.empty();
            }
            return keepIfStillValid(auth, httpError);
        } catch (HttpClientException transportError) {
            return keepIfStillValid(auth, transportError);
        }

        if (response == null || response.accessToken() == null || response.expiresIn() == null) {
            return keepIfStillValid(auth,
                new IllegalStateException("token response missing access_token/expires_in"));
        }
        return Optional.of(persistAndSyncRoles(auth, response));
    }

    private List<String> persistAndSyncRoles(SessionTokens auth, OidcTokenResponse response) {
        String newRefreshToken = response.refreshToken() != null ? response.refreshToken() : auth.refreshToken();
        OffsetDateTime newExpiry = OffsetDateTime.now().plusSeconds(response.expiresIn());
        userAuthenticationPort.updateTokens(
            auth.userAuthenticationId(), response.accessToken(), newRefreshToken, newExpiry);

        return userRoleService.getRoles(auth.userId());
    }

    private void terminate(SessionTokens auth, String reason) {
        userAuthenticationPort.deleteByUserAuthenticationId(auth.userAuthenticationId());
        LOG.info("Session terminated ({}); session row deleted.", reason);
    }

    private Optional<List<String>> keepIfStillValid(SessionTokens auth, RuntimeException cause) {
        if (auth.tokenExpiration() != null && auth.tokenExpiration().isAfter(OffsetDateTime.now())) {
            LOG.warn("Provider unavailable during refresh ({}); keeping still-valid token.",
                cause.getMessage());
            return Optional.of(userRoleService.getRoles(auth.userId()));
        }
        LOG.warn("Provider unavailable during refresh ({}) and token expired; denying request, session kept.",
            cause.getMessage());
        return Optional.empty();
    }

    private Map<String, String> refreshForm(String refreshToken) {
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        return form;
    }

    private static boolean isInvalidGrant(HttpClientResponseException httpError) {
        int code = httpError.getStatus().getCode();
        if (code != 400 && code != 401) {
            return false;
        }
        String error = httpError.getResponse()
            .getBody(Argument.mapOf(String.class, String.class))
            .map(body -> body.get("error"))
            .orElse(null);
        return "invalid_grant".equals(error) || "invalid_token".equals(error);
    }
}
