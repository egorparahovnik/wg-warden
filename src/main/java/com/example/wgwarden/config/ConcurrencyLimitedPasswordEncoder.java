package com.example.wgwarden.config;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.Semaphore;

/**
 * Ограничивает число одновременных Argon2-операций. Argon2 намеренно потребляет
 * десятки мегабайт на хеш, поэтому без ограничения параллельные логины могут
 * исчерпать память контейнера.
 */
public final class ConcurrencyLimitedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Semaphore permits;

    public ConcurrencyLimitedPasswordEncoder(PasswordEncoder delegate, int concurrency) {
        this.delegate = delegate;
        this.permits = new Semaphore(Math.max(1, concurrency), true);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        acquire();
        try {
            return delegate.encode(rawPassword);
        } finally {
            permits.release();
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        acquire();
        try {
            return delegate.matches(rawPassword, encodedPassword);
        } finally {
            permits.release();
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    private void acquire() {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for password hashing slot", e);
        }
    }
}
