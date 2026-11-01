package com.example.wgmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String masterKey, String jwtSecret) {
}
