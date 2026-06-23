package com.example.auth.user.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AuthenticatedUser {

    private Long userId;
    private UUID uuid;
    private String ssn;
    private String firstName;
    private String lastName;
    private String email;
    private List<String> roles;

    private UUID sessionId;
    private Long userAuthenticationId;
    private String authMethod;
    private OffsetDateTime tokenExpiration;
    private OffsetDateTime sessionExpiration;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserAuthenticationId() {
        return userAuthenticationId;
    }

    public void setUserAuthenticationId(Long userAuthenticationId) {
        this.userAuthenticationId = userAuthenticationId;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public OffsetDateTime getTokenExpiration() {
        return tokenExpiration;
    }

    public void setTokenExpiration(OffsetDateTime tokenExpiration) {
        this.tokenExpiration = tokenExpiration;
    }

    public OffsetDateTime getSessionExpiration() {
        return sessionExpiration;
    }

    public void setSessionExpiration(OffsetDateTime sessionExpiration) {
        this.sessionExpiration = sessionExpiration;
    }
}
