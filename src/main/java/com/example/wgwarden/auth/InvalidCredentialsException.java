package com.example.wgwarden.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("invalid email or password");
    }
}
