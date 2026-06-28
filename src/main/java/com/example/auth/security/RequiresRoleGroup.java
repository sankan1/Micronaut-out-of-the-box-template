package com.example.auth.security;

import com.example.openapi.model.UserInfoOutputModalRoleGroupsInner;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@Type(RequiresRoleGroupInterceptor.class)
public @interface RequiresRoleGroup {

    UserInfoOutputModalRoleGroupsInner[] value();
}
