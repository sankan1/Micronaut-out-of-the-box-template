package com.example.auth.user.service;

import com.example.auth.user.persistence.port.UserAuthenticationPort;
import com.example.auth.user.persistence.port.UserRolePort;
import jakarta.inject.Singleton;

import java.util.Arrays;
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

    public List<String> syncRoles(Long userId, String scope) {
        List<String> roles = scope == null
            ? List.of()
            : Arrays.stream(scope.split(" ")).filter(role -> !role.isBlank()).toList();
        userRolePort.replaceRoles(userId, roles);
        return roles;
    }
}
