package com.unireserva.back.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.unireserva.back.dto.AvailabilityResponse;
import com.unireserva.back.dto.AvailabilityResponse.BusySlot;
import com.unireserva.back.dto.AvailabilityResponse.FreeWindow;
import com.unireserva.back.dto.CategoryResponse;
import com.unireserva.back.dto.ResourceResponse;
import com.unireserva.back.model.Reservation;
import com.unireserva.back.model.ReservationStatus;
import com.unireserva.back.model.Resource;
import com.unireserva.back.model.ResourceImage;
import com.unireserva.back.model.ResourceState;
import com.unireserva.back.model.ResourceType;
import com.unireserva.back.repository.ReservationRepository;
import com.unireserva.back.repository.ResourceCategoryRepository;
import com.unireserva.back.repository.ResourceImageRepository;
import com.unireserva.back.repository.ResourceRepository;

@Service
@Transactional(readOnly = true)
public class ResourceService {
    private final ResourceRepository resourceRepository;
    private final ResourceCategoryRepository categoryRepository;
    private final ReservationRepository reservationRepository;
    private final ResourceImageRepository resourceImageRepository;
    private final Clock clock;

    public ResourceService(
            ResourceRepository resourceRepository,
            ResourceCategoryRepository categoryRepository,
            ReservationRepository reservationRepository,
            ResourceImageRepository resourceImageRepository,
            Clock clock) {
        this.resourceRepository = resourceRepository;
        this.categoryRepository = categoryRepository;
        this.reservationRepository = reservationRepository;
        this.resourceImageRepository = resourceImageRepository;
        this.clock = clock;
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getDescription()))
                .toList();
    }

    /** Catálogo para usuarios finales: solo recursos AVAILABLE. */
    public List<ResourceResponse> search(Long categoryId, ResourceType type, String q) {
        return resourceRepository.search(ResourceState.AVAILABLE, categoryId, type, normalizeQuery(q)).stream()
                .map(ResourceResponse::from)
                .toList();
    }

    /** Catálogo para administración: todos los estados. */
    public List<ResourceResponse> searchAll(Long categoryId, ResourceType type, String q) {
        return resourceRepository.search(null, categoryId, type, normalizeQuery(q)).stream()
                .map(ResourceResponse::from)
                .toList();
    }

    public ResourceResponse getById(Long id) {
        return ResourceResponse.from(findOrThrow(id));
    }

    public ResourceImage getImage(Long resourceId) {
        return resourceImageRepository.findById(resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El recurso no tiene imagen"));
    }

    /**
     * Calcula en qué franjas del día el recurso tiene al menos una unidad libre.
     * Divide el horario de atención en tramos según donde empieza/termina cada reserva y,
     * en cada tramo, resta las unidades ocupadas.
     */
    public AvailabilityResponse getAvailability(Long resourceId, LocalDate date) {
        Resource resource = findOrThrow(resourceId);
        LocalDateTime open = date.atTime(resource.getOpenTime());
        LocalDateTime close = date.atTime(resource.getCloseTime());
        int total = resource.getQuantity();

        List<Reservation> occupying = reservationRepository
                .findOccupying(resourceId, ReservationStatus.OCCUPYING, open, close);

        TreeSet<LocalDateTime> points = new TreeSet<>();
        points.add(open);
        points.add(close);
        for (Reservation r : occupying) {
            points.add(max(r.getStartTime(), open));
            points.add(min(r.getEndTime(), close));
        }

        LocalDateTime earliest = open;
        if (date.equals(LocalDate.now(clock))) {
            earliest = max(open, ceilToQuarterHour(LocalDateTime.now(clock)));
        }

        List<LocalDateTime> pts = new ArrayList<>(points);
        List<FreeWindow> free = new ArrayList<>();
        if (resource.getState() == ResourceState.AVAILABLE) {
            for (int i = 0; i < pts.size() - 1; i++) {
                LocalDateTime start = max(pts.get(i), earliest);
                LocalDateTime end = pts.get(i + 1);
                if (!start.isBefore(end)) {
                    continue;
                }
                int used = 0;
                for (Reservation r : occupying) {
                    if (r.getStartTime().isBefore(end) && r.getEndTime().isAfter(pts.get(i))) {
                        used += r.getQuantity();
                    }
                }
                int available = total - used;
                if (available <= 0) {
                    continue;
                }
                FreeWindow last = free.isEmpty() ? null : free.get(free.size() - 1);
                if (last != null && last.end().equals(start) && last.availableQuantity() == available) {
                    free.set(free.size() - 1, new FreeWindow(last.start(), end, available));
                } else {
                    free.add(new FreeWindow(start, end, available));
                }
            }
        }

        List<BusySlot> busy = occupying.stream()
                .map(r -> new BusySlot(r.getStartTime(), r.getEndTime(), r.getQuantity()))
                .toList();

        return new AvailabilityResponse(
                resource.getId(),
                resource.getName(),
                date,
                resource.getOpenTime(),
                resource.getCloseTime(),
                total,
                resource.getMaxDurationMinutes(),
                free,
                busy);
    }

    private Resource findOrThrow(Long id) {
        return resourceRepository.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El recurso no existe"));
    }

    private static String normalizeQuery(String q) {
        return q == null ? "" : q.trim();
    }

    static LocalDateTime ceilToQuarterHour(LocalDateTime time) {
        LocalDateTime truncated = time.withSecond(0).withNano(0);
        int remainder = truncated.getMinute() % 15;
        boolean exact = remainder == 0 && time.getSecond() == 0 && time.getNano() == 0;
        return exact ? truncated : truncated.plusMinutes(15 - remainder);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
