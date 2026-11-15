package com.unireserva.back.dto;

import java.time.LocalTime;

import com.unireserva.back.model.ResourceState;
import com.unireserva.back.model.ResourceType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Crear o editar un recurso (panel de administración). */
public record ResourceUpsertRequest(
        @NotNull(message = "Debes indicar la categoría")
        Long categoryId,
        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(max = 120, message = "El nombre no puede tener más de 120 caracteres")
        String name,
        String description,
        @NotNull(message = "Debes indicar el tipo de recurso")
        ResourceType resourceType,
        @Size(max = 160, message = "La ubicación no puede tener más de 160 caracteres")
        String location,
        @Min(value = 1, message = "La capacidad debe ser al menos 1")
        Integer capacity,
        @NotNull(message = "Debes indicar la cantidad de unidades")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        Integer quantity,
        @NotNull(message = "Debes indicar la hora de apertura")
        LocalTime openTime,
        @NotNull(message = "Debes indicar la hora de cierre")
        LocalTime closeTime,
        @NotNull(message = "Debes indicar la duración máxima")
        @Min(value = 15, message = "La duración máxima debe ser de al menos 15 minutos")
        Integer maxDurationMinutes,
        boolean requiresApproval,
        ResourceState state,
        @Size(max = 255, message = "La URL de la imagen no puede tener más de 255 caracteres")
        String imageUrl
) {
}
