package com.example.wgwarden.peer;
import jakarta.validation.constraints.NotNull;
public record PeerEnabledRequest(@NotNull Boolean enabled) {}
