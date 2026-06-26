package com.example.auth.user.service;

import com.example.openapi.model.UserInfoOutputModalRoleGroupsInner;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class RoleGroupResolver {

    private final List<String> adminRoles;
    private final List<String> headUserRoles;
    private final List<String> endUserRoles;

    public RoleGroupResolver(@Value("${app.roles.admin-roles}") List<String> adminRoles,
                             @Value("${app.roles.head-user-roles}") List<String> headUserRoles,
                             @Value("${app.roles.end-user-roles}") List<String> endUserRoles) {
        this.adminRoles = adminRoles;
        this.headUserRoles = headUserRoles;
        this.endUserRoles = endUserRoles;
    }

    public List<UserInfoOutputModalRoleGroupsInner> resolve(List<String> roles) {
        List<UserInfoOutputModalRoleGroupsInner> groups = new ArrayList<>();
        if (roles.stream().anyMatch(adminRoles::contains)) {
            groups.add(UserInfoOutputModalRoleGroupsInner.ADMIN);
        }
        if (roles.stream().anyMatch(headUserRoles::contains)) {
            groups.add(UserInfoOutputModalRoleGroupsInner.HEAD_USER);
        }
        if (roles.stream().anyMatch(endUserRoles::contains)) {
            groups.add(UserInfoOutputModalRoleGroupsInner.END_USER);
        }
        return groups;
    }
}
