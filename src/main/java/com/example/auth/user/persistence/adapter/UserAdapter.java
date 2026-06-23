package com.example.auth.user.persistence.adapter;

import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.persistence.port.UserAuthenticationPort;
import com.example.jooq.system.tables.User;
import com.example.jooq.system.tables.UserAuthentication;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class UserAdapter {

    private static final User USER = User.USER;
    private static final UserAuthentication USER_AUTHENTICATION = UserAuthentication.USER_AUTHENTICATION;

    private final DSLContext dslContext;
    private final UserAuthenticationPort userAuthenticationPort;

    public UserAdapter(DSLContext dslContext, UserAuthenticationPort userAuthenticationPort) {
        this.dslContext = dslContext;
        this.userAuthenticationPort = userAuthenticationPort;
    }

    public Optional<AuthenticatedUser> findAuthenticatedUserBySessionId(UUID sessionId) {
        return dslContext
            .select(
                USER.USER_ID,
                USER.UUID,
                USER.SSN,
                USER.FIRST_NAME,
                USER.LAST_NAME,
                USER.EMAIL,
                USER_AUTHENTICATION.USER_AUTHENTICATION_ID,
                USER_AUTHENTICATION.AUTH_METHOD,
                USER_AUTHENTICATION.TOKEN_EXPIRATION,
                USER_AUTHENTICATION.SESSION_EXPIRATION,
                USER_AUTHENTICATION.SESSION_ID)
            .from(USER)
            .join(USER_AUTHENTICATION).on(USER_AUTHENTICATION.USER_ID.eq(USER.USER_ID))
            .where(USER_AUTHENTICATION.SESSION_ID.eq(sessionId))
            .fetchOptional(this::mapAuthenticatedUser);
    }

    public Optional<AuthenticatedUser> findUserBySsn(String ssn) {
        return dslContext.selectFrom(USER)
            .where(USER.SSN.eq(ssn))
            .fetchOptional(this::mapUser);
    }

    public Optional<AuthenticatedUser> findUserByUuid(UUID uuid) {
        return dslContext.selectFrom(USER)
            .where(USER.UUID.eq(uuid))
            .fetchOptional(this::mapUser);
    }

    public AuthenticatedUser createUser(String ssn, String firstName, String lastName, String email) {
        UUID userUuid = UUID.randomUUID();
        Long userId = dslContext.insertInto(USER)
            .set(USER.UUID, userUuid)
            .set(USER.SSN, ssn)
            .set(USER.FIRST_NAME, firstName)
            .set(USER.LAST_NAME, lastName)
            .set(USER.EMAIL, email)
            .returning(USER.USER_ID)
            .fetchOne(USER.USER_ID);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setUserId(userId);
        authenticatedUser.setUuid(userUuid);
        authenticatedUser.setSsn(ssn);
        authenticatedUser.setFirstName(firstName);
        authenticatedUser.setLastName(lastName);
        authenticatedUser.setEmail(email);
        return authenticatedUser;
    }

    private AuthenticatedUser mapAuthenticatedUser(Record record) {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId(record.get(USER.USER_ID));
        user.setUuid(record.get(USER.UUID));
        user.setSsn(record.get(USER.SSN));
        user.setFirstName(record.get(USER.FIRST_NAME));
        user.setLastName(record.get(USER.LAST_NAME));
        user.setEmail(record.get(USER.EMAIL));
        user.setUserAuthenticationId(record.get(USER_AUTHENTICATION.USER_AUTHENTICATION_ID));
        user.setAuthMethod(record.get(USER_AUTHENTICATION.AUTH_METHOD));
        user.setTokenExpiration(record.get(USER_AUTHENTICATION.TOKEN_EXPIRATION));
        user.setSessionExpiration(record.get(USER_AUTHENTICATION.SESSION_EXPIRATION));
        user.setSessionId(record.get(USER_AUTHENTICATION.SESSION_ID));
        user.setRoles(userAuthenticationPort.findRoles(user.getUserId()));
        return user;
    }

    private AuthenticatedUser mapUser(Record record) {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId(record.get(USER.USER_ID));
        user.setUuid(record.get(USER.UUID));
        user.setSsn(record.get(USER.SSN));
        user.setFirstName(record.get(USER.FIRST_NAME));
        user.setLastName(record.get(USER.LAST_NAME));
        user.setEmail(record.get(USER.EMAIL));
        user.setRoles(userAuthenticationPort.findRoles(user.getUserId()));
        return user;
    }
}
