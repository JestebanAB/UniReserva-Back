package com.unireserva.back.dto;

import java.time.LocalDateTime;

import com.unireserva.back.model.Reservation;
import com.unireserva.back.model.ReservationChannel;
import com.unireserva.back.model.ReservationStatus;

public record ReservationResponse(
        Long id,
        Long resourceId,
        String resourceName,
        String categoryName,
        String location,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer quantity,
        Integer attendees,
        String notes,
        ReservationStatus status,
        ReservationChannel channel,
        String userEmail,
        String userName,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getResource().getId(),
                r.getResource().getName(),
                r.getResource().getCategory().getName(),
                r.getResource().getLocation(),
                r.getStartTime(),
                r.getEndTime(),
                r.getQuantity(),
                r.getAttendees(),
                r.getNotes(),
                r.getStatus(),
                r.getChannel(),
                r.getUser().getEmail(),
                r.getUser().getNames() + " " + r.getUser().getSurNames(),
                r.getCreatedAt());
    }
}
