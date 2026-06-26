package com.example.controller;

import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.persistence.adapter.UserAdapter;
import com.example.auth.user.service.RoleGroupResolver;
import com.example.openapi.api.UserApi;
import com.example.openapi.model.UserInfoOutputModal;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Controller
public class UserController implements UserApi {

    private final UserAdapter userAdapter;
    private final SecurityService securityService;
    private final RoleGroupResolver roleGroupResolver;

    public UserController(UserAdapter userAdapter, SecurityService securityService, RoleGroupResolver roleGroupResolver) {
        this.userAdapter = userAdapter;
        this.securityService = securityService;
        this.roleGroupResolver = roleGroupResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public HttpResponse<UserInfoOutputModal> fetchUserInfo() {
        return securityService.getAuthentication()
            .flatMap(authentication -> parseUuid(authentication.getName()))
            .flatMap(userAdapter::findUserByUuid)
            .<HttpResponse<UserInfoOutputModal>>map(user -> HttpResponse.ok(toUserInfo(user)))
            .orElseGet(HttpResponse::notFound);
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException invalidUuid) {
            return Optional.empty();
        }
    }

    private UserInfoOutputModal toUserInfo(AuthenticatedUser user) {
        UserInfoOutputModal userInfo = new UserInfoOutputModal(
            user.getUserId().intValue(),
            user.getSsn(),
            user.getFirstName(),
            user.getLastName(),
            user.getRoles());
        userInfo.setEmail(user.getEmail());
        userInfo.setRoleGroups(roleGroupResolver.resolve(user.getRoles()));
        return userInfo;
    }
}
