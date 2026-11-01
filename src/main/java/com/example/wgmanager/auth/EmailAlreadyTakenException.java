package com.example.wgmanager.auth;

public class EmailAlreadyTakenException extends RuntimeException {

    public EmailAlreadyTakenException() {
        super("email already registered");
    }
}
