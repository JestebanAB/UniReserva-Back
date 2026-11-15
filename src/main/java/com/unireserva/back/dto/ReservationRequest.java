package com.unireserva.back.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReservationRequest(
        @NotNull(message = "Debes indicar el recurso")
        Long resourceId,
        @NotNull(message = "Debes indicar la fecha y hora de inicio")
        LocalDateTime startTime,
        @NotNull(message = "Debes indicar la fecha y hora de fin")
        LocalDateTime endTime,
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        Integer quantity,
        @Min(value = 1, message = "El número de asistentes debe ser al menos 1")
        Integer attendees,
        @Size(max = 500, message = "Las notas no pueden tener más de 500 caracteres")
        String notes
) {
}
