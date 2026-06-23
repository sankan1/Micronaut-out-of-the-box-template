package com.example.auth.user.service;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.OffsetDateTime;

@Singleton
public class SessionPolicy {

    private final Duration absoluteLifetime;

    public SessionPolicy(@Value("${app.session.absolute-lifetime:12h}") Duration absoluteLifetime) {
        this.absoluteLifetime = absoluteLifetime;
    }

    public OffsetDateTime newAbsoluteExpiry() {
        return OffsetDateTime.now().plus(absoluteLifetime);
    }

    public Duration getAbsoluteLifetime() {
        return absoluteLifetime;
    }
}
