package com.example.wgwarden.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Блокировка на время транзакции: сериализует создание первого пользователя
     * между несколькими запросами и экземплярами приложения.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(72110961)", nativeQuery = true)
    Integer lockRegistration();
}
