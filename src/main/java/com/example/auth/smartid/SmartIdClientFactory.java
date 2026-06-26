package com.example.auth.smartid;

import ee.sk.smartid.CertificateValidator;
import ee.sk.smartid.CertificateValidatorImpl;
import ee.sk.smartid.FileTrustedCAStoreBuilder;
import ee.sk.smartid.NotificationAuthenticationResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.TrustedCACertStore;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.io.ResourceResolver;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.Optional;

@Factory
public class SmartIdClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SmartIdClientFactory.class);

    private final ResourceResolver resourceResolver = new ResourceResolver();

    @Bean
    @Singleton
    public SmartIdClient smartIdClient(SmartIdProperties properties) {
        SmartIdClient client = new SmartIdClient();
        client.setRelyingPartyUUID(properties.getRelyingPartyUuid());
        client.setRelyingPartyName(properties.getRelyingPartyName());
        client.setHostUrl(properties.getHostUrl());
        loadTrustStore(properties).ifPresent(client::setTrustStore);
        return client;
    }

    @Bean
    @Singleton
    public CertificateValidator certificateValidator() {
        TrustedCACertStore trustedCACertStore = new FileTrustedCAStoreBuilder().build();
        return new CertificateValidatorImpl(trustedCACertStore);
    }

    @Bean
    @Singleton
    public NotificationAuthenticationResponseValidator notificationAuthenticationResponseValidator(
            CertificateValidator certificateValidator) {
        return NotificationAuthenticationResponseValidator.defaultSetupWithCertificateValidator(certificateValidator);
    }

    private Optional<KeyStore> loadTrustStore(SmartIdProperties properties) {
        String path = properties.getTruststorePath();
        Optional<InputStream> resource = resourceResolver.getResourceAsStream(path);
        if (resource.isEmpty()) {
            LOG.warn("Smart-ID truststore '{}' not found, falling back to the JVM default trust manager", path);
            return Optional.empty();
        }
        try (InputStream is = resource.get()) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(is, properties.getTruststorePassword().toCharArray());
            return Optional.of(trustStore);
        } catch (Exception e) {
            LOG.warn("Failed to load Smart-ID truststore '{}', falling back to the JVM default trust manager", path, e);
            return Optional.empty();
        }
    }
}
