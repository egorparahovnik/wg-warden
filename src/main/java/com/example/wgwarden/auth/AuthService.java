package com.example.wgwarden.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.dummyPasswordHash = passwords.encode("dummy-password-for-timing");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyTakenException();
        }
        User user = new User(email, passwords.encode(request.password()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyTakenException();
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = users.findByEmail(email).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwords.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(java.util.UUID userId) {
        User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        return UserResponse.from(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwt.createToken(user), UserResponse.from(user));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
