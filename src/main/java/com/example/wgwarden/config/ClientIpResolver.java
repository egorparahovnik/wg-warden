package com.example.wgwarden.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Определяет адрес клиента, доверяя X-Real-IP только от явно перечисленных прокси.
 * Заголовок от внешнего клиента игнорируется, поэтому его нельзя использовать для
 * обхода или блокировки чужого rate-limit.
 */
@Component
public class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(AppProperties properties) {
        String configured = properties.trustedProxies() == null ? "" : properties.trustedProxies();
        this.trustedProxies = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isTrustedProxy(remote)) {
            String forwarded = request.getHeader("X-Real-IP");
            if (isIpLiteral(forwarded)) {
                return forwarded;
            }
        }
        return remote;
    }

    private boolean isTrustedProxy(String address) {
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIpLiteral(String value) {
        if (value == null || value.length() > 64) {
            return false;
        }
        // Только IPv4/IPv6-литералы: не принимаем hostname или произвольные строки.
        return value.matches("^[0-9a-fA-F:.]+$");
    }
}
