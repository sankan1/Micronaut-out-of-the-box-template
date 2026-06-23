package com.example.auth.util;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public final class SessionUtil {

    public static final String AUTH_COOKIE_NAME = "authToken";
    public static final String SESSION_ID_ATTRIBUTE = "sessionId";

    private static final long EXPIRY_SKEW_SECONDS = 30;

    private SessionUtil() {
    }

    public static String findSessionCookie(HttpRequest<?> request) {
        return request.getCookies().findCookie(AUTH_COOKIE_NAME)
            .map(Cookie::getValue)
            .orElse(null);
    }

    public static Optional<UUID> parseSessionId(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(sessionId));
        } catch (IllegalArgumentException invalidUuid) {
            return Optional.empty();
        }
    }

    public static boolean isStillValid(OffsetDateTime expiry) {
        return expiry != null && expiry.isAfter(OffsetDateTime.now().plusSeconds(EXPIRY_SKEW_SECONDS));
    }
}
