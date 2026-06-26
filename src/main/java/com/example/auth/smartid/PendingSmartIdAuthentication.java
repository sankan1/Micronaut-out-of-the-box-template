package com.example.auth.smartid;

import ee.sk.smartid.rest.dao.NotificationAuthenticationSessionRequest;

public record PendingSmartIdAuthentication(
    String sessionId,
    NotificationAuthenticationSessionRequest authenticationSessionRequest
) {
}
