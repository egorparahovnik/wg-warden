package com.example.wgwarden.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String masterKey, String jwtSecret, Duration jwtExpiration,
                            @DefaultValue("false") boolean registrationEnabled,
                            @DefaultValue("") String bootstrapToken,
                            @DefaultValue("2") int passwordHashConcurrency,
                            @DefaultValue("172.16.0.0/12,10.0.0.0/8,192.168.0.0/16")
                            String trustedProxies) {
}
