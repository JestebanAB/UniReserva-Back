package com.unireserva.back.dto;

import java.util.List;

public record LoginResponse(
    String email,
    String names,
    String nickname,
    List<String> roles
) {
    
}
