package com.unireserva.back.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.unireserva.back.model.AuthSession;
import com.unireserva.back.repository.AuthSessionRepository;

@Service
@Transactional
public class AuthSessionService {
    private final AuthSessionRepository authSessionRepository;

    @Value("${jwt.expiration}")
    private long sessionExpirationTime;

    @Value("${jwt.remember-me-expiration}")
    private long rememberMeExpirationTime;

    public AuthSessionService(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    public AuthSession createSession(Long userId, boolean rememberMe) {
        authSessionRepository.deleteByExpiresAtBefore(Instant.now());
        long expirationTime = rememberMe ? rememberMeExpirationTime : sessionExpirationTime;
        AuthSession session = new AuthSession(
                UUID.randomUUID(),
                userId,
                Instant.now().plusMillis(expirationTime));
        return authSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, Long userId) {
        return authSessionRepository.existsByIdAndUserIdAndExpiresAtAfter(
                sessionId,
                userId,
                Instant.now());
    }

    public void revokeSession(UUID sessionId) {
        authSessionRepository.deleteById(sessionId);
    }
}
