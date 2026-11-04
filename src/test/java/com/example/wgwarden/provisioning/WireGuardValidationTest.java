package com.example.wgwarden.provisioning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WireGuardValidationTest {

    @Test
    void acceptsValidKeysAndAddresses() {
        assertThat(WireGuardValidation.publicKey("yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=")).isTrue();
        assertThat(WireGuardValidation.ipv4("10.67.0.2")).isTrue();
        assertThat(WireGuardValidation.ipv4Cidr("10.67.0.2/32")).isTrue();
    }

    @Test
    void rejectsShellAndConfigInjection() {
        assertThat(WireGuardValidation.publicKey("key'; rm -rf / #")).isFalse();
        assertThat(WireGuardValidation.ipv4("10.67.0.2; reboot")).isFalse();
        assertThat(WireGuardValidation.ipv4Cidr("10.67.0.2/32\nPostUp=evil")).isFalse();
    }
}
