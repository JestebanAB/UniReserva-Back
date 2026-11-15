package com.unireserva.back.model;

import java.util.List;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    REJECTED,
    COMPLETED;

    /** Estados que ocupan el recurso en el calendario. */
    public static final List<ReservationStatus> OCCUPYING = List.of(PENDING, CONFIRMED);
}
