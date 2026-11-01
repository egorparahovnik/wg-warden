package com.example.wgmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Аутентификация полностью на JWT; встроенный in-memory UserDetailsService не нужен.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class WgManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WgManagerApplication.class, args);
    }
}
