package com.example.wgwarden.auth;

public record AuthResponse(String token, UserResponse user) {
}
