package com.example.wgwarden.server;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ServerRepository extends JpaRepository<Server, UUID> {
    List<Server> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Server> findByIdAndUserId(UUID id, UUID userId);
}
