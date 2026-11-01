package com.example.wgmanager.peer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record PeerRequest(@NotBlank @Size(max=100) String name) {}
