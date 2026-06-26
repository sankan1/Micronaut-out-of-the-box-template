package com.example.auth.user.service;

import com.example.auth.oidc.TokenRefreshService;
import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.persistence.adapter.UserAdapter;
import com.example.auth.user.persistence.port.UserAuthenticationPort;
import com.example.auth.util.SessionUtil;
import jakarta.transaction.Transactional;
import jakarta.inject.Singleton;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class SessionValidationService {

    private static final String AUTH_METHOD_OIDC = "OIDC";

    private final UserAdapter userAdapter;
    private final UserAuthenticationPort userAuthenticationPort;
    private final TokenRefreshService tokenRefreshService;
    private final SessionPolicy sessionPolicy;

    public SessionValidationService(UserAdapter userAdapter,
                                    UserAuthenticationPort userAuthenticationPort,
                                    TokenRefreshService tokenRefreshService,
                                    SessionPolicy sessionPolicy) {
        this.userAdapter = userAdapter;
        this.userAuthenticationPort = userAuthenticationPort;
        this.tokenRefreshService = tokenRefreshService;
        this.sessionPolicy = sessionPolicy;
    }

    @Transactional
    public AuthenticatedUser validate(String sessionId) {
        Optional<UUID> parsed = SessionUtil.parseSessionId(sessionId);
        if (parsed.isEmpty()) {
            return null;
        }

        AuthenticatedUser user = userAdapter.findAuthenticatedUserBySessionId(parsed.get()).orElse(null);
        if (user == null) {
            return null;
        }

        if (!SessionUtil.isStillValid(user.getSessionExpiration())) {
            userAuthenticationPort.deleteByUserAuthenticationId(user.getUserAuthenticationId());
            return null;
        }

        if (!AUTH_METHOD_OIDC.equals(user.getAuthMethod())) {
            return touchAndReturn(user);
        }

        if (SessionUtil.isStillValid(user.getTokenExpiration())) {
            return touchAndReturn(user);
        }
        return tokenRefreshService.refresh(user.getUserAuthenticationId())
            .map(roles -> {
                user.setRoles(roles);
                return touchAndReturn(user);
            })
            .orElse(null);
    }

    private AuthenticatedUser touchAndReturn(AuthenticatedUser user) {
        OffsetDateTime extended = sessionPolicy.newAbsoluteExpiry();
        userAuthenticationPort.touchLastActivity(user.getUserAuthenticationId(), extended);
        return user;
    }
}
