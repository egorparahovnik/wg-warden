package com.example.wgmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String masterKey, String jwtSecret, Duration jwtExpiration) {
}
