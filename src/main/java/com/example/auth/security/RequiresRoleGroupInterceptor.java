package com.example.auth.security;

import com.example.auth.user.service.RoleGroupResolver;
import com.example.openapi.model.UserInfoOutputModalRoleGroupsInner;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
@InterceptorBean(RequiresRoleGroup.class)
public class RequiresRoleGroupInterceptor implements MethodInterceptor<Object, Object> {

    private final SecurityService securityService;
    private final RoleGroupResolver roleGroupResolver;

    public RequiresRoleGroupInterceptor(SecurityService securityService, RoleGroupResolver roleGroupResolver) {
        this.securityService = securityService;
        this.roleGroupResolver = roleGroupResolver;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Set<UserInfoOutputModalRoleGroupsInner> allowedGroups = Set.of(
            context.synthesize(RequiresRoleGroup.class).value());

        List<String> rawRoles = securityService.getAuthentication()
            .map(authentication -> new ArrayList<>(authentication.getRoles()))
            .orElseGet(ArrayList::new);

        List<UserInfoOutputModalRoleGroupsInner> callerGroups = roleGroupResolver.resolve(rawRoles);

        if (callerGroups.stream().noneMatch(allowedGroups::contains)) {
            throw new AuthorizationException(securityService.getAuthentication().orElse(null));
        }

        return context.proceed();
    }
}
