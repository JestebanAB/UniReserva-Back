package com.unireserva.back.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El front manda el historial completo de la conversación (el backend no guarda estado de chat).
 * El último mensaje debe ser del usuario.
 */
public record AgentChatRequest(
        @NotEmpty(message = "La conversación no puede estar vacía")
        @Size(max = 40, message = "La conversación es demasiado larga")
        List<@Valid ChatMessage> messages
) {
    public record ChatMessage(
            @Pattern(regexp = "user|assistant", message = "El rol del mensaje debe ser user o assistant")
            String role,
            @NotBlank(message = "El mensaje no puede estar vacío")
            @Size(max = 2000, message = "Cada mensaje puede tener máximo 2000 caracteres")
            String content
    ) {
    }
}
