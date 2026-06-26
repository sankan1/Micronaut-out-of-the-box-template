package com.example.auth.oidc;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.auth.AuthFeatureFlags;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Singleton
@Named("oidc")
public class OidcAuthenticationMapper implements OpenIdAuthenticationMapper {

    private static final Logger LOG = LoggerFactory.getLogger(OidcAuthenticationMapper.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int[] CHECKSUM_WEIGHTS_1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 1};
    private static final int[] CHECKSUM_WEIGHTS_2 = {3, 4, 5, 6, 7, 8, 9, 1, 2, 3};

    private final UserService userService;
    private final AuthFeatureFlags authFeatureFlags;

    public OidcAuthenticationMapper(UserService userService, AuthFeatureFlags authFeatureFlags) {
        this.userService = userService;
        this.authFeatureFlags = authFeatureFlags;
    }

    @Override
    public Publisher<AuthenticationResponse> createAuthenticationResponse(String providerName,
                                                                          OpenIdTokenResponse tokenResponse,
                                                                          OpenIdClaims openIdClaims,
                                                                          @Nullable State state) {
        if (authFeatureFlags.shouldRejectOidcLogin()) {
            LOG.warn("OIDC login rejected - 'oidc-auth' feature flag is disabled while smart-id-auth is enforced");
            return Mono.just(AuthenticationResponse.failure("oidc-auth-disabled"));
        }
        String givenName = openIdClaims.getGivenName();
        String familyName = openIdClaims.getFamilyName();
        String email = openIdClaims.getEmail();
        String scope = tokenResponse.getScope();
        String ssn = generateEstonianSsn();

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

    // checksum-valid Estonian personal code, starts with 5
    private static String generateEstonianSsn() {
        int year = RANDOM.nextInt(26);
        int month = 1 + RANDOM.nextInt(12);
        int day = 1 + RANDOM.nextInt(28);
        int serial = RANDOM.nextInt(1000);

        String first10Digits = String.format("5%02d%02d%02d%03d", year, month, day, serial);
        return first10Digits + calculateChecksum(first10Digits);
    }

    private static int calculateChecksum(String first10Digits) {
        int checksum = weightedRemainder(first10Digits, CHECKSUM_WEIGHTS_1);
        if (checksum == 10) {
            checksum = weightedRemainder(first10Digits, CHECKSUM_WEIGHTS_2);
        }
        return checksum == 10 ? 0 : checksum;
    }

    private static int weightedRemainder(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        return sum % 11;
    }
}
