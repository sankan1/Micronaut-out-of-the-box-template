package com.example.auth.smartid;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("smart-id")
public class SmartIdProperties {

    private boolean enabled;
    private String relyingPartyUuid;
    private String relyingPartyName;
    private String hostUrl;
    private String schemeName;
    private String truststorePath;
    private String truststorePassword;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRelyingPartyUuid() {
        return relyingPartyUuid;
    }

    public void setRelyingPartyUuid(String relyingPartyUuid) {
        this.relyingPartyUuid = relyingPartyUuid;
    }

    public String getRelyingPartyName() {
        return relyingPartyName;
    }

    public void setRelyingPartyName(String relyingPartyName) {
        this.relyingPartyName = relyingPartyName;
    }

    public String getHostUrl() {
        return hostUrl;
    }

    public void setHostUrl(String hostUrl) {
        this.hostUrl = hostUrl;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }
}
