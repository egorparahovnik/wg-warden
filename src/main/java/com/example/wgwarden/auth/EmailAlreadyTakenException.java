package com.example.wgwarden.auth;

public class EmailAlreadyTakenException extends RuntimeException {

    public EmailAlreadyTakenException() {
        super("email already registered");
    }
}
