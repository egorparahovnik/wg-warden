package com.example.wgmanager.config;

import com.example.wgmanager.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, exception) -> {
                boolean bearer = request.getHeader(HttpHeaders.AUTHORIZATION) != null
                        && request.getHeader(HttpHeaders.AUTHORIZATION).startsWith("Bearer ");
                boolean invalid = Boolean.TRUE.equals(request.getAttribute("jwt.invalid"));
                log.warn("Unauthorized {} {} (bearerPresent={}, invalidJwt={})", request.getMethod(), request.getRequestURI(), bearer, invalid);
                response.sendError(HttpStatus.UNAUTHORIZED.value());
            }))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
