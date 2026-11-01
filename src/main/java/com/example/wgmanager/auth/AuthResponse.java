package com.example.wgmanager.auth;

public record AuthResponse(String token, UserResponse user) {
}
