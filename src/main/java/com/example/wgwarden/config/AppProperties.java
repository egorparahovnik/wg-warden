package com.example.wgwarden.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String masterKey, String jwtSecret, Duration jwtExpiration,
                            @DefaultValue("true") boolean registrationEnabled) {
}
