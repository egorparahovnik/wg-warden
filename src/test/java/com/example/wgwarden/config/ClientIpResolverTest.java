package com.example.wgwarden.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private ClientIpResolver resolver() {
        return new ClientIpResolver(new AppProperties(
                "unused", "unused", Duration.ofHours(1), false, "", 2,
                "172.16.0.0/12,10.0.0.0/8"));
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");
        request.addHeader("X-Real-IP", "1.2.3.4");

        assertThat(resolver().resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void acceptsForwardedHeaderFromTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.2");
        request.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(resolver().resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void rejectsNonIpForwardedValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.2");
        request.addHeader("X-Real-IP", "attacker.example");

        assertThat(resolver().resolve(request)).isEqualTo("172.18.0.2");
    }
}
