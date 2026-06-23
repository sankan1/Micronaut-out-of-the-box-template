package com.example.auth.user.service;

import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.persistence.adapter.UserAdapter;
import com.example.auth.user.persistence.port.UserAuthenticationPort;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.OffsetDateTime;
import java.util.UUID;

@Singleton
public class UserService {

    private final UserAdapter userAdapter;
    private final UserAuthenticationPort userAuthenticationPort;
    private final UserRoleService userRoleService;
    private final SessionPolicy sessionPolicy;

    public UserService(UserAdapter userAdapter,
                       UserAuthenticationPort userAuthenticationPort,
                       UserRoleService userRoleService,
                       SessionPolicy sessionPolicy) {
        this.userAdapter = userAdapter;
        this.userAuthenticationPort = userAuthenticationPort;
        this.userRoleService = userRoleService;
        this.sessionPolicy = sessionPolicy;
    }

    @Transactional(timeout = 60)
    public AuthenticatedUser loginWithOidc(String ssn, String givenName, String familyName,
                                           String email, String scope, OpenIdTokenResponse tokenResponse) {
        AuthenticatedUser user = userAdapter.findUserBySsn(ssn)
            .orElseGet(() -> userAdapter.createUser(ssn, givenName, familyName, email));

        user.setRoles(userRoleService.syncRoles(user.getUserId(), scope));

        UUID sessionId = UUID.randomUUID();
        OffsetDateTime tokenExpiration = OffsetDateTime.now().plusSeconds(tokenResponse.getExpiresIn());

        userAuthenticationPort.upsertSession(new UserAuthenticationPort.NewSession(
            user.getUserId(),
            "OIDC",
            sessionId,
            tokenResponse.getAccessToken(),
            tokenResponse.getRefreshToken(),
            tokenExpiration,
            sessionPolicy.newAbsoluteExpiry()));

        user.setSessionId(sessionId);
        return user;
    }
}
