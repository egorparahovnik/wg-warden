package com.example.wgwarden.peer;
import jakarta.validation.constraints.Size;
public record PeerUpdateRequest(@Size(max=100) String name, Boolean enabled, String privateKey) {}
