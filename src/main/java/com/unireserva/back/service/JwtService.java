package com.unireserva.back.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails; 

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import java.util.Date;
import java.util.Map;

import com.unireserva.back.model.AuthSession;


@Service
@Transactional
public class JwtService {
    @Value("${jwt.secret}")
    private String secretKey;

    public String generateToken(UserDetails userDetails, AuthSession session) {
        return Jwts.builder()
                .claims(Map.of(
                        "roles", userDetails.getAuthorities().stream()
                                .map(auth -> auth.getAuthority())
                                .toList(),
                        "sid", session.getId().toString(),
                        "uid", session.getUserId()))
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(Date.from(session.getExpiresAt()))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
