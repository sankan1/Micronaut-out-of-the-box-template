package com.example.auth.user.persistence.adapter;

import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.persistence.port.UserAuthenticationPort;
import com.example.jooq.system.tables.User;
import com.example.jooq.system.tables.UserAuthentication;
import com.example.jooq.system.tables.UserRole;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class UserAuthenticationAdapter implements UserAuthenticationPort {

    private static final UserAuthentication USER_AUTHENTICATION = UserAuthentication.USER_AUTHENTICATION;
    private static final UserRole USER_ROLE = UserRole.USER_ROLE;

    private final DSLContext dslContext;

    public UserAuthenticationAdapter(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public void upsertSession(NewSession session) {
        dslContext.insertInto(USER_AUTHENTICATION)
            .set(USER_AUTHENTICATION.USER_ID, session.userId())
            .set(USER_AUTHENTICATION.AUTH_METHOD, session.authMethod())
            .set(USER_AUTHENTICATION.SESSION_ID, session.sessionId())
            .set(USER_AUTHENTICATION.ACCESS_TOKEN, session.accessToken())
            .set(USER_AUTHENTICATION.REFRESH_TOKEN, session.refreshToken())
            .set(USER_AUTHENTICATION.TOKEN_EXPIRATION, session.tokenExpiration())
            .set(USER_AUTHENTICATION.SESSION_EXPIRATION, session.sessionExpiration())
            .onConflict(USER_AUTHENTICATION.USER_ID)
            .doUpdate()
            .set(USER_AUTHENTICATION.AUTH_METHOD, session.authMethod())
            .set(USER_AUTHENTICATION.SESSION_ID, session.sessionId())
            .set(USER_AUTHENTICATION.ACCESS_TOKEN, session.accessToken())
            .set(USER_AUTHENTICATION.REFRESH_TOKEN, session.refreshToken())
            .set(USER_AUTHENTICATION.TOKEN_EXPIRATION, session.tokenExpiration())
            .set(USER_AUTHENTICATION.SESSION_EXPIRATION, session.sessionExpiration())
            .set(USER_AUTHENTICATION.LAST_ACTIVITY, (OffsetDateTime) null)
            .execute();
    }

    @Override
    public Optional<SessionTokens> findForUpdate(Long userAuthenticationId) {
        return dslContext.select(
                USER_AUTHENTICATION.USER_AUTHENTICATION_ID,
                USER_AUTHENTICATION.USER_ID,
                USER_AUTHENTICATION.REFRESH_TOKEN,
                USER_AUTHENTICATION.TOKEN_EXPIRATION)
            .from(USER_AUTHENTICATION)
            .where(USER_AUTHENTICATION.USER_AUTHENTICATION_ID.eq(userAuthenticationId))
            .forUpdate()
            .fetchOptional(record -> new SessionTokens(
                record.get(USER_AUTHENTICATION.USER_AUTHENTICATION_ID),
                record.get(USER_AUTHENTICATION.USER_ID),
                record.get(USER_AUTHENTICATION.REFRESH_TOKEN),
                record.get(USER_AUTHENTICATION.TOKEN_EXPIRATION)));
    }

    @Override
    public void updateTokens(Long userAuthenticationId, String accessToken, String refreshToken,
                             OffsetDateTime tokenExpiration) {
        dslContext.update(USER_AUTHENTICATION)
            .set(USER_AUTHENTICATION.ACCESS_TOKEN, accessToken)
            .set(USER_AUTHENTICATION.REFRESH_TOKEN, refreshToken)
            .set(USER_AUTHENTICATION.TOKEN_EXPIRATION, tokenExpiration)
            .where(USER_AUTHENTICATION.USER_AUTHENTICATION_ID.eq(userAuthenticationId))
            .execute();
    }

    @Override
    public void touchLastActivity(Long userAuthenticationId, OffsetDateTime sessionExpiration) {
        dslContext.update(USER_AUTHENTICATION)
            .set(USER_AUTHENTICATION.LAST_ACTIVITY, OffsetDateTime.now())
            .set(USER_AUTHENTICATION.SESSION_EXPIRATION, sessionExpiration)
            .where(USER_AUTHENTICATION.USER_AUTHENTICATION_ID.eq(userAuthenticationId))
            .execute();
    }

    @Override
    public void deleteByUserAuthenticationId(Long userAuthenticationId) {
        dslContext.deleteFrom(USER_AUTHENTICATION)
            .where(USER_AUTHENTICATION.USER_AUTHENTICATION_ID.eq(userAuthenticationId))
            .execute();
    }

    @Override
    public Optional<RevocableTokens> deleteBySessionIdReturning(UUID sessionId) {
        return dslContext.deleteFrom(USER_AUTHENTICATION)
            .where(USER_AUTHENTICATION.SESSION_ID.eq(sessionId))
            .returning(USER_AUTHENTICATION.ACCESS_TOKEN, USER_AUTHENTICATION.REFRESH_TOKEN)
            .fetchOptional()
            .map(record -> new RevocableTokens(
                record.get(USER_AUTHENTICATION.ACCESS_TOKEN),
                record.get(USER_AUTHENTICATION.REFRESH_TOKEN)));
    }

    @Override
    public List<String> findRoles(Long userId) {
        return dslContext.select(USER_ROLE.ROLE)
            .from(USER_ROLE)
            .where(USER_ROLE.USER_ID.eq(userId))
            .fetch(USER_ROLE.ROLE);
    }
}
