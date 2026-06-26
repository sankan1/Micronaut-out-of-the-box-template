package com.example.auth;

import io.getunleash.Unleash;
import jakarta.inject.Singleton;

@Singleton
public class AuthFeatureFlags {

    public static final String OIDC_AUTH_FLAG = "oidc-auth";
    public static final String SMART_ID_AUTH_FLAG = "smart-id-auth";

    private final Unleash unleash;

    public AuthFeatureFlags(Unleash unleash) {
        this.unleash = unleash;
    }

    public boolean isOidcAuthEnabled() {
        return unleash.isEnabled(OIDC_AUTH_FLAG);
    }

    public boolean isSmartIdAuthEnabled() {
        return unleash.isEnabled(SMART_ID_AUTH_FLAG);
    }

    public boolean isAuthenticationRequired() {
        return isOidcAuthEnabled() || isSmartIdAuthEnabled();
    }

    /**
     * True only when smart-id-auth is carrying the authentication requirement and oidc-auth
     * is specifically turned off - i.e. OIDC is the disabled method, not authentication as a whole.
     * When neither flag is on, login is bypassed like everything else, so OIDC login must not fail here.
     */
    public boolean shouldRejectOidcLogin() {
        return isAuthenticationRequired() && !isOidcAuthEnabled();
    }

    public boolean shouldRejectSmartIdLogin() {
        return isAuthenticationRequired() && !isSmartIdAuthEnabled();
    }
}
