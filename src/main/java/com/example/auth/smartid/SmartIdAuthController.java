package com.example.auth.smartid;

import com.example.auth.AuthFeatureFlags;
import com.example.auth.user.dto.AuthenticatedUser;
import com.example.auth.user.service.SmartIdLoginService;
import com.example.openapi.api.AuthApi;
import com.example.openapi.model.SmartIdInitRequest;
import com.example.openapi.model.SmartIdInitResponse;
import ee.sk.smartid.AuthenticationCertificateLevel;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.HashAlgorithm;
import ee.sk.smartid.NotificationAuthenticationResponseValidator;
import ee.sk.smartid.NotificationAuthenticationSessionRequestBuilder;
import ee.sk.smartid.RpChallenge;
import ee.sk.smartid.RpChallengeGenerator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.VerificationCodeCalculator;
import ee.sk.smartid.common.notification.interactions.NotificationInteraction;
import ee.sk.smartid.rest.dao.NotificationAuthenticationSessionResponse;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.sk.smartid.signature.AuthenticationSignatureAlgorithm;
import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.context.BeanProvider;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.cookie.Cookie;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
public class SmartIdAuthController implements AuthApi {

    private static final Logger LOG = LoggerFactory.getLogger(SmartIdAuthController.class);
    private static final String SESSION_CACHE_NAME = "smart-id-sessions";
    private static final String SESSION_STATE_COMPLETE = "COMPLETE";

    private final AuthFeatureFlags authFeatureFlags;
    private final SmartIdProperties smartIdProperties;
    private final BeanProvider<SmartIdClient> smartIdClientProvider;
    private final BeanProvider<NotificationAuthenticationResponseValidator> authenticationResponseValidatorProvider;
    private final SmartIdLoginService smartIdLoginService;
    private final SyncCache<?> sessionCache;

    public SmartIdAuthController(AuthFeatureFlags authFeatureFlags,
                                 SmartIdProperties smartIdProperties,
                                 BeanProvider<SmartIdClient> smartIdClientProvider,
                                 BeanProvider<NotificationAuthenticationResponseValidator> authenticationResponseValidatorProvider,
                                 SmartIdLoginService smartIdLoginService,
                                 CacheManager<?> cacheManager) {
        this.authFeatureFlags = authFeatureFlags;
        this.smartIdProperties = smartIdProperties;
        this.smartIdClientProvider = smartIdClientProvider;
        this.authenticationResponseValidatorProvider = authenticationResponseValidatorProvider;
        this.smartIdLoginService = smartIdLoginService;
        this.sessionCache = cacheManager.getCache(SESSION_CACHE_NAME);
    }

    @Override
    public HttpResponse<SmartIdInitResponse> smartIdInit(@Valid SmartIdInitRequest smartIdInitRequest) {
        if (authFeatureFlags.shouldRejectSmartIdLogin()) {
            LOG.warn("Smart-ID login rejected - 'smart-id-auth' feature flag is disabled while oidc-auth is enforced");
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }
        if (smartIdInitRequest == null || smartIdInitRequest.getIdentityCode() == null) {
            return HttpResponse.badRequest();
        }

        SemanticsIdentifier semanticsIdentifier = new SemanticsIdentifier(
            SemanticsIdentifier.IdentityType.PNO,
            SemanticsIdentifier.CountryCode.EE,
            smartIdInitRequest.getIdentityCode());

        RpChallenge rpChallenge = RpChallengeGenerator.generate();
        String verificationCode = VerificationCodeCalculator.calculate(rpChallenge.value());

        NotificationAuthenticationSessionRequestBuilder builder = smartIdClientProvider.get().createNotificationAuthentication()
            .withSemanticsIdentifier(semanticsIdentifier)
            .withRpChallenge(rpChallenge.toBase64EncodedValue())
            .withCertificateLevel(AuthenticationCertificateLevel.QUALIFIED)
            .withSignatureAlgorithm(AuthenticationSignatureAlgorithm.RSASSA_PSS)
            .withHashAlgorithm(HashAlgorithm.SHA3_512)
            .withInteractions(List.of(NotificationInteraction.displayTextAndPin("Logging in")));

        NotificationAuthenticationSessionResponse sessionResponse = builder.initAuthenticationSession();

        String reference = UUID.randomUUID().toString();
        sessionCache.put(reference, new PendingSmartIdAuthentication(
            sessionResponse.sessionID(), builder.getAuthenticationSessionRequest()));

        return HttpResponse.ok(new SmartIdInitResponse()
            .reference(reference)
            .verificationCode(verificationCode));
    }

    @Override
    public HttpResponse<Void> smartIdComplete(String reference) {
        if (authFeatureFlags.shouldRejectSmartIdLogin()) {
            LOG.warn("Smart-ID login rejected - 'smart-id-auth' feature flag is disabled while oidc-auth is enforced");
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }

        Optional<PendingSmartIdAuthentication> pending = sessionCache.get(reference, PendingSmartIdAuthentication.class);
        if (pending.isEmpty()) {
            return HttpResponse.notFound();
        }
        sessionCache.invalidate(reference);

        SessionStatus sessionStatus;
        try {
            sessionStatus = smartIdClientProvider.get().getSessionStatusPoller()
                .fetchFinalSessionStatus(pending.get().sessionId());
        } catch (RuntimeException e) {
            LOG.warn("Smart-ID session polling failed", e);
            return HttpResponse.status(HttpStatus.UNAUTHORIZED);
        }

        if (!SESSION_STATE_COMPLETE.equals(sessionStatus.getState())) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED);
        }

        AuthenticationIdentity identity;
        try {
            identity = authenticationResponseValidatorProvider.get().validate(
                sessionStatus, pending.get().authenticationSessionRequest(), smartIdProperties.getSchemeName());
        } catch (RuntimeException e) {
            LOG.warn("Smart-ID authentication validation failed", e);
            return HttpResponse.status(HttpStatus.UNAUTHORIZED);
        }

        AuthenticatedUser user = smartIdLoginService.loginWithSmartId(identity);
        boolean secure = ServerRequestContext.<Object>currentRequest()
            .map(HttpRequest::isSecure)
            .orElse(false);
        Cookie cookie = smartIdLoginService.buildAuthCookie(user.getSessionId(), secure);

        MutableHttpResponse<Void> response = HttpResponse.ok();
        response.cookie(cookie);
        return response;
    }
}
