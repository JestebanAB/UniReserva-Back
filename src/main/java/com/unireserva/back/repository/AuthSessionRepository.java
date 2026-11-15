package com.unireserva.back.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unireserva.back.model.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    boolean existsByIdAndUserIdAndExpiresAtAfter(
            UUID id,
            Long userId,
            Instant now);

    long deleteByExpiresAtBefore(Instant now);
}
