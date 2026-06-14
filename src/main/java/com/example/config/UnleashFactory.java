package com.example.config;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.util.UnleashConfig;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Factory
public class UnleashFactory {

    @Value("${unleash.api-url}")
    private String apiUrl;

    @Value("${unleash.api-token}")
    private String apiToken;

    @Value("${unleash.app-name}")
    private String appName;

    @Bean
    @Singleton
    public Unleash unleash() {
        UnleashConfig config = UnleashConfig.builder()
                .appName(appName)
                .unleashAPI(apiUrl)
                .customHttpHeader("Authorization", apiToken)
                .build();
        return new DefaultUnleash(config);
    }
}
