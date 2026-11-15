package com.unireserva.back.dto;

public record RegistrationResponse(
    String message,
    String email,
    String names,
    String nickname
) {

}
