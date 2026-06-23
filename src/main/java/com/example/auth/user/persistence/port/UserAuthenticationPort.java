package com.example.auth.user.persistence.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAuthenticationPort {

    record NewSession(
        Long userId,
        String authMethod,
        UUID sessionId,
        String accessToken,
        String refreshToken,
        OffsetDateTime tokenExpiration,
        OffsetDateTime sessionExpiration) {
    }

    record SessionTokens(
        Long userAuthenticationId,
        Long userId,
        String refreshToken,
        OffsetDateTime tokenExpiration) {
    }

    record RevocableTokens(String accessToken, String refreshToken) {
    }

    void upsertSession(NewSession session);

    Optional<SessionTokens> findForUpdate(Long userAuthenticationId);

    void updateTokens(Long userAuthenticationId, String accessToken, String refreshToken,
                      OffsetDateTime tokenExpiration);

    void touchLastActivity(Long userAuthenticationId, OffsetDateTime sessionExpiration);

    void deleteByUserAuthenticationId(Long userAuthenticationId);

    Optional<RevocableTokens> deleteBySessionIdReturning(UUID sessionId);

    List<String> findRoles(Long userId);
}
