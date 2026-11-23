package com.unireserva.back.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.unireserva.back.dto.ReservationRequest;
import com.unireserva.back.dto.ReservationResponse;
import com.unireserva.back.model.Reservation;
import com.unireserva.back.model.ReservationChannel;
import com.unireserva.back.model.ReservationStatus;
import com.unireserva.back.model.Resource;
import com.unireserva.back.model.ResourceCategory;
import com.unireserva.back.model.ResourceType;
import com.unireserva.back.model.Users;
import com.unireserva.back.repository.ReservationRepository;
import com.unireserva.back.repository.ResourceRepository;
import com.unireserva.back.repository.UsersRepository;

class ReservationServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    // "Ahora" fijo: viernes 20 de noviembre de 2026, 10:00 hora Bogotá
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 11, 20, 10, 0).atZone(ZONE).toInstant(), ZONE);

    private ReservationRepository reservationRepository;
    private ResourceRepository resourceRepository;
    private UsersRepository usersRepository;
    private ReservationService service;
    private Resource tennisCourt;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        resourceRepository = mock(ResourceRepository.class);
        usersRepository = mock(UsersRepository.class);
        service = new ReservationService(reservationRepository, resourceRepository, usersRepository, CLOCK);

        Users user = Users.builder().id(1L).email("ana@udea.edu.co").names("Ana").surNames("Pérez").build();
        when(usersRepository.findByEmail("ana@udea.edu.co")).thenReturn(Optional.of(user));

        tennisCourt = Resource.builder()
                .id(10L)
                .category(ResourceCategory.builder().id(1L).name("Canchas deportivas").build())
                .name("Cancha de tenis 1")
                .resourceType(ResourceType.SPACE)
                .capacity(4)
                .quantity(1)
                .openTime(LocalTime.of(6, 0))
                .closeTime(LocalTime.of(20, 0))
                .maxDurationMinutes(90)
                .requiresApproval(false)
                .build();
        when(resourceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(tennisCourt));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReservationRequest request(int startHour, int startMinute, int endHour, int endMinute) {
        return new ReservationRequest(10L,
                LocalDateTime.of(2026, 11, 21, startHour, startMinute),
                LocalDateTime.of(2026, 11, 21, endHour, endMinute),
                null, 2, null);
    }

    private void noOverlap() {
        when(reservationRepository.sumReservedQuantity(eq(10L), anyCollection(), any(), any())).thenReturn(0L);
    }

    @Test
    void confirmsReservationWhenSlotIsFree() {
        noOverlap();

        ReservationResponse response = service.create("ana@udea.edu.co", request(14, 0, 15, 0), ReservationChannel.WEB);

        assertEquals(ReservationStatus.CONFIRMED, response.status());
        assertEquals(ReservationChannel.WEB, response.channel());
    }

    @Test
    void leavesReservationPendingWhenResourceRequiresApproval() {
        noOverlap();
        tennisCourt.setRequiresApproval(true);

        ReservationResponse response = service.create("ana@udea.edu.co", request(14, 0, 15, 0), ReservationChannel.AGENT);

        assertEquals(ReservationStatus.PENDING, response.status());
        assertEquals(ReservationChannel.AGENT, response.channel());
    }

    @Test
    void rejectsOverlappingReservationWithConflict() {
        when(reservationRepository.sumReservedQuantity(eq(10L), anyCollection(), any(), any())).thenReturn(1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("ana@udea.edu.co", request(14, 0, 15, 0), ReservationChannel.WEB));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void rejectsReservationInThePast() {
        noOverlap();
        ReservationRequest past = new ReservationRequest(10L,
                LocalDateTime.of(2026, 11, 20, 8, 0),
                LocalDateTime.of(2026, 11, 20, 9, 0),
                null, 2, null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("ana@udea.edu.co", past, ReservationChannel.WEB));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsReservationOutsideOpeningHours() {
        noOverlap();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("ana@udea.edu.co", request(19, 0, 20, 30), ReservationChannel.WEB));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsReservationLongerThanMaxDuration() {
        noOverlap();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("ana@udea.edu.co", request(14, 0, 16, 0), ReservationChannel.WEB));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsTimesThatAreNotQuarterHours() {
        noOverlap();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("ana@udea.edu.co", request(14, 10, 15, 0), ReservationChannel.WEB));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsMoreAttendeesThanCapacity() {
        noOverlap();
        ReservationRequest tooMany = new ReservationRequest(10L,
                LocalDateTime.of(2026, 11, 21, 14, 0),
                LocalDateTime.of(2026, 11, 21, 15, 0),
                null, 9, null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("ana@udea.edu.co", tooMany, ReservationChannel.WEB));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsInvalidQuantityForEquipment() {
        noOverlap();
        tennisCourt.setResourceType(ResourceType.EQUIPMENT);
        tennisCourt.setQuantity(3);
        ReservationRequest invalidQuantity = new ReservationRequest(10L,
                LocalDateTime.of(2026, 11, 21, 14, 0),
                LocalDateTime.of(2026, 11, 21, 15, 0),
                0, null, null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("ana@udea.edu.co", invalidQuantity, ReservationChannel.WEB));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void quarterHourRoundingIsCorrect() {
        assertEquals(LocalDateTime.of(2026, 11, 20, 10, 15),
                ResourceService.ceilToQuarterHour(LocalDateTime.of(2026, 11, 20, 10, 1)));
        assertEquals(LocalDateTime.of(2026, 11, 20, 10, 0),
                ResourceService.ceilToQuarterHour(LocalDateTime.of(2026, 11, 20, 10, 0)));
        assertEquals(LocalDateTime.of(2026, 11, 20, 11, 0),
                ResourceService.ceilToQuarterHour(LocalDateTime.of(2026, 11, 20, 10, 46)));
    }
}
