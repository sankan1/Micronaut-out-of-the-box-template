package com.example.auth.oidc;

import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.service.UserService;
import com.example.auth.util.SessionUtil;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.oauth2.endpoint.authorization.state.State;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdAuthenticationMapper;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdClaims;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

@Singleton
@Named("oidc")
public class OidcAuthenticationMapper implements OpenIdAuthenticationMapper {

    private static final String CLAIM_SSN = "personal_code";

    private final UserService userService;

    public OidcAuthenticationMapper(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Publisher<AuthenticationResponse> createAuthenticationResponse(String providerName,
                                                                          OpenIdTokenResponse tokenResponse,
                                                                          OpenIdClaims openIdClaims,
                                                                          @Nullable State state) {
        String ssn = (String) openIdClaims.getClaims().get(CLAIM_SSN);
        String givenName = openIdClaims.getGivenName();
        String familyName = openIdClaims.getFamilyName();
        String email = openIdClaims.getEmail();
        String scope = tokenResponse.getScope();

        return Mono.fromCallable(() -> {
                AuthenticatedUser user = userService.loginWithOidc(
                    ssn, givenName, familyName, email, scope, tokenResponse);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put(SessionUtil.SESSION_ID_ATTRIBUTE, user.getSessionId().toString());
                attributes.put("user_ssn", user.getSsn());

                return (AuthenticationResponse) AuthenticationResponse.success(
                    user.getUuid().toString(), user.getRoles(), attributes);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }
}
