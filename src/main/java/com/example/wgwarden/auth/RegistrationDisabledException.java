package com.example.wgwarden.auth;

public class RegistrationDisabledException extends RuntimeException {

    public RegistrationDisabledException() {
        super("registration is disabled");
    }
}
