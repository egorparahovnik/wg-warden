package com.example.wgmanager.auth;

import java.util.UUID;

public record UserResponse(UUID id, String email) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}
