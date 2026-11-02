package com.example.wgwarden.peer;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record PeerRequest(@NotBlank @Size(max=100) String name,@PositiveOrZero Long trafficLimitBytes,@Future Instant expiresAt) {}
