package com.unireserva.back.dto;

import java.util.List;

public record SessionResponse(
        String email,
        String names,
        String nickname,
        List<String> roles
) {
}
