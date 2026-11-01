package com.example.wgmanager.peer;
import jakarta.validation.constraints.NotNull;
public record PeerEnabledRequest(@NotNull Boolean enabled) {}
