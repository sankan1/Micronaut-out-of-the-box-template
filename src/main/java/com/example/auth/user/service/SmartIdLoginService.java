package com.example.auth.user.service;

import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.persistence.adapter.UserAdapter;
import com.example.auth.user.persistence.port.UserAuthenticationPort;
import com.example.auth.user.persistence.port.UserRolePort;
import com.example.auth.util.SessionUtil;
import ee.sk.smartid.AuthenticationIdentity;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.token.cookie.AccessTokenCookieConfiguration;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

@Singleton
public class SmartIdLoginService {

    private static final List<String> DEFAULT_SMART_ID_ROLES = List.of("end-user");

    private final UserAdapter userAdapter;
    private final UserAuthenticationPort userAuthenticationPort;
    private final UserRolePort userRolePort;
    private final SessionPolicy sessionPolicy;
    private final AccessTokenCookieConfiguration cookieConfiguration;

    public SmartIdLoginService(UserAdapter userAdapter,
                               UserAuthenticationPort userAuthenticationPort,
                               UserRolePort userRolePort,
                               SessionPolicy sessionPolicy,
                               AccessTokenCookieConfiguration cookieConfiguration) {
        this.userAdapter = userAdapter;
        this.userAuthenticationPort = userAuthenticationPort;
        this.userRolePort = userRolePort;
        this.sessionPolicy = sessionPolicy;
        this.cookieConfiguration = cookieConfiguration;
    }

    @Transactional(timeout = 60)
    public AuthenticatedUser loginWithSmartId(AuthenticationIdentity identity) {
        String ssn = identity.getIdentityNumber();

        AuthenticatedUser user = userAdapter.findUserBySsn(ssn)
            .orElseGet(() -> userAdapter.createUser(
                ssn, identity.getGivenName(), identity.getSurname(), null));

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            userRolePort.replaceRoles(user.getUserId(), DEFAULT_SMART_ID_ROLES);
            user.setRoles(DEFAULT_SMART_ID_ROLES);
        }

        UUID sessionId = UUID.randomUUID();
        userAuthenticationPort.upsertSession(new UserAuthenticationPort.NewSession(
            user.getUserId(),
            "SMART_ID",
            sessionId,
            null,
            null,
            null,
            sessionPolicy.newAbsoluteExpiry()));

        user.setSessionId(sessionId);
        return user;
    }

    public Cookie buildAuthCookie(UUID sessionId, boolean secureRequest) {
        Cookie authCookie = Cookie.of(SessionUtil.AUTH_COOKIE_NAME, sessionId.toString());
        authCookie.configure(cookieConfiguration, secureRequest);
        cookieConfiguration.getCookieMaxAge().ifPresent(authCookie::maxAge);
        return authCookie;
    }
}
