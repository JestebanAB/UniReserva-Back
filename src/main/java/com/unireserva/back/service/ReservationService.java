package com.unireserva.back.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.unireserva.back.dto.ReservationRequest;
import com.unireserva.back.dto.ReservationResponse;
import com.unireserva.back.model.Reservation;
import com.unireserva.back.model.ReservationChannel;
import com.unireserva.back.model.ReservationStatus;
import com.unireserva.back.model.Resource;
import com.unireserva.back.model.ResourceState;
import com.unireserva.back.model.ResourceType;
import com.unireserva.back.model.Users;
import com.unireserva.back.repository.ReservationRepository;
import com.unireserva.back.repository.ResourceRepository;
import com.unireserva.back.repository.UsersRepository;

/**
 * Reglas de negocio de las reservas. Es la ÚNICA puerta para crear o cancelar:
 * tanto el formulario web como el agente de IA pasan por aquí, así las reglas se aplican igual.
 */
@Service
@Transactional
public class ReservationService {
    private static final int SLOT_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final UsersRepository usersRepository;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservationRepository,
            ResourceRepository resourceRepository,
            UsersRepository usersRepository,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.usersRepository = usersRepository;
        this.clock = clock;
    }

    public ReservationResponse create(String email, ReservationRequest request, ReservationChannel channel) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));

        // Bloquea el recurso: evita que dos reservas simultáneas pasen la validación de cupo a la vez.
        Resource resource = resourceRepository.findByIdForUpdate(request.resourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El recurso no existe"));

        validateRules(resource, request);

        int quantity = resource.getResourceType() == ResourceType.SPACE
                ? 1
                : (request.quantity() == null ? 1 : request.quantity());

        long reserved = reservationRepository.sumReservedQuantity(
                resource.getId(), ReservationStatus.OCCUPYING, request.startTime(), request.endTime());
        long free = resource.getQuantity() - reserved;
        if (free < quantity) {
            String message = resource.getResourceType() == ResourceType.SPACE
                    ? "Ese horario ya está ocupado. Consulta la disponibilidad y elige otra franja."
                    : "En ese horario solo quedan " + Math.max(free, 0) + " unidad(es) disponibles.";
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }

        Reservation reservation = Reservation.builder()
                .resource(resource)
                .user(user)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .quantity(quantity)
                .attendees(request.attendees())
                .notes(blankToNull(request.notes()))
                .status(resource.isRequiresApproval() ? ReservationStatus.PENDING : ReservationStatus.CONFIRMED)
                .channel(channel)
                .build();

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMine(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));
        return reservationRepository.findByUserWithResource(user.getId()).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    public ReservationResponse cancel(String email, Long reservationId, boolean isAdmin) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe"));

        if (!isAdmin && !reservation.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes cancelar tus propias reservas");
        }
        if (!ReservationStatus.OCCUPYING.contains(reservation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta reserva ya no se puede cancelar (estado: " + reservation.getStatus() + ")");
        }
        if (!reservation.getStartTime().isAfter(LocalDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede cancelar una reserva que ya comenzó");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        return ReservationResponse.from(reservation);
    }

    /** Valida horario, duración, capacidad y estado del recurso (sin consultar cupo). */
    void validateRules(Resource resource, ReservationRequest request) {
        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();

        if (resource.getState() != ResourceState.AVAILABLE) {
            throw bad(HttpStatus.CONFLICT, "Este recurso no está disponible para reservas en este momento");
        }
        if (!end.isAfter(start)) {
            throw bad(HttpStatus.BAD_REQUEST, "La hora de fin debe ser posterior a la de inicio");
        }
        if (start.isBefore(LocalDateTime.now(clock))) {
            throw bad(HttpStatus.BAD_REQUEST, "No puedes reservar en el pasado");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw bad(HttpStatus.BAD_REQUEST, "La reserva debe empezar y terminar el mismo día");
        }
        if (start.getMinute() % SLOT_MINUTES != 0 || end.getMinute() % SLOT_MINUTES != 0
                || start.getSecond() != 0 || end.getSecond() != 0) {
            throw bad(HttpStatus.BAD_REQUEST,
                    "Las reservas deben empezar y terminar en múltiplos de " + SLOT_MINUTES + " minutos (por ejemplo 14:00 o 14:15)");
        }
        if (start.toLocalTime().isBefore(resource.getOpenTime()) || end.toLocalTime().isAfter(resource.getCloseTime())) {
            throw bad(HttpStatus.BAD_REQUEST,
                    "Este recurso solo se puede reservar entre " + resource.getOpenTime() + " y " + resource.getCloseTime());
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes > resource.getMaxDurationMinutes()) {
            throw bad(HttpStatus.BAD_REQUEST,
                    "La duración máxima por reserva es de " + resource.getMaxDurationMinutes() + " minutos");
        }
        if (resource.getResourceType() == ResourceType.EQUIPMENT) {
            int quantity = request.quantity() == null ? 1 : request.quantity();
            if (quantity > resource.getQuantity()) {
                throw bad(HttpStatus.BAD_REQUEST, "Solo existen " + resource.getQuantity() + " unidades de este recurso");
            }
        }
        if (resource.getResourceType() == ResourceType.SPACE
                && resource.getCapacity() != null && request.attendees() != null
                && request.attendees() > resource.getCapacity()) {
            throw bad(HttpStatus.BAD_REQUEST, "La capacidad máxima de este espacio es de " + resource.getCapacity() + " personas");
        }
    }

    private static ResponseStatusException bad(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
