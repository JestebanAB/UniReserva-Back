package com.unireserva.back.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.unireserva.back.dto.ReservationRequest;
import com.unireserva.back.dto.ReservationResponse;
import com.unireserva.back.model.ReservationChannel;
import com.unireserva.back.service.ReservationService;

import jakarta.validation.Valid;

/** Reservas del usuario autenticado. El correo viene en el "subject" del JWT. */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody ReservationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ReservationResponse created = reservationService.create(jwt.getSubject(), request, ReservationChannel.WEB);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/mine")
    public List<ReservationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return reservationService.getMine(jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    public ReservationResponse cancel(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return reservationService.cancel(jwt.getSubject(), id, isAdmin);
    }
}
