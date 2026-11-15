package com.unireserva.back.dto;

public record LoginResult(
        LoginResponse response,
        String token
) {
}
