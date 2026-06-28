package com.example.auth.user.service;

import com.example.auth.user.persistence.port.UserAuthenticationPort;
import com.example.auth.user.persistence.port.UserRolePort;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class UserRoleService {

    private final UserAuthenticationPort userAuthenticationPort;
    private final UserRolePort userRolePort;

    public UserRoleService(UserAuthenticationPort userAuthenticationPort, UserRolePort userRolePort) {
        this.userAuthenticationPort = userAuthenticationPort;
        this.userRolePort = userRolePort;
    }

    public List<String> getRoles(Long userId) {
        return userAuthenticationPort.findRoles(userId);
    }

    /**
     * Replaces the user's stored roles with the roles asserted by the identity provider
     * (the ID token's "roles" claim for OIDC, or a fixed default for Smart-ID).
     */
    public List<String> syncRoles(Long userId, List<String> roles) {
        List<String> safeRoles = roles == null ? List.of() : roles;
        userRolePort.replaceRoles(userId, safeRoles);
        return safeRoles;
    }
}
