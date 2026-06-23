package com.example.auth.user.persistence.adapter;

import com.example.auth.user.persistence.port.UserRolePort;
import com.example.jooq.system.tables.UserRole;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;

import java.util.List;

@Singleton
public class UserRoleAdapter implements UserRolePort {

    private static final UserRole USER_ROLE = UserRole.USER_ROLE;

    private final DSLContext dslContext;

    public UserRoleAdapter(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public void replaceRoles(Long userId, List<String> roles) {
        dslContext.deleteFrom(USER_ROLE)
            .where(USER_ROLE.USER_ID.eq(userId))
            .execute();

        if (roles == null || roles.isEmpty()) {
            return;
        }

        var insertStep = dslContext.insertInto(USER_ROLE, USER_ROLE.USER_ID, USER_ROLE.ROLE);
        for (String role : roles) {
            insertStep = insertStep.values(userId, role);
        }
        insertStep.execute();
    }
}
