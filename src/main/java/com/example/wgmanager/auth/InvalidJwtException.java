package com.example.wgmanager.auth;

public class InvalidJwtException extends RuntimeException {

    public InvalidJwtException(Throwable cause) {
        super("invalid token", cause);
    }
}
