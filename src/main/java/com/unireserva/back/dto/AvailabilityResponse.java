package com.unireserva.back.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Disponibilidad de un recurso en un día.
 * freeWindows: franjas donde todavía hay al menos una unidad libre (availableQuantity).
 * busySlots: reservas que ocupan el recurso ese día.
 */
public record AvailabilityResponse(
        Long resourceId,
        String resourceName,
        LocalDate date,
        LocalTime openTime,
        LocalTime closeTime,
        Integer totalQuantity,
        Integer maxDurationMinutes,
        List<FreeWindow> freeWindows,
        List<BusySlot> busySlots
) {
    public record FreeWindow(LocalDateTime start, LocalDateTime end, int availableQuantity) {
    }

    public record BusySlot(LocalDateTime start, LocalDateTime end, int quantity) {
    }
}
