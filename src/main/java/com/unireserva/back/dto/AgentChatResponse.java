package com.unireserva.back.dto;

import java.util.List;

/**
 * reply: texto para mostrar al usuario.
 * toolsUsed: herramientas que el agente ejecutó (transparencia / depuración).
 * reservationsChanged: true si el agente creó o canceló algo, para que el front refresque "Mis reservas".
 */
public record AgentChatResponse(
        String reply,
        List<String> toolsUsed,
        boolean reservationsChanged
) {
}
