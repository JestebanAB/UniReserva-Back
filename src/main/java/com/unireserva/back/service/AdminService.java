package com.unireserva.back.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.unireserva.back.dto.ReservationResponse;
import com.unireserva.back.dto.ResourceResponse;
import com.unireserva.back.dto.ResourceUpsertRequest;
import com.unireserva.back.model.Reservation;
import com.unireserva.back.model.ReservationStatus;
import com.unireserva.back.model.Resource;
import com.unireserva.back.model.ResourceCategory;
import com.unireserva.back.model.ResourceState;
import com.unireserva.back.model.ResourceType;
import com.unireserva.back.repository.ReservationRepository;
import com.unireserva.back.repository.ResourceCategoryRepository;
import com.unireserva.back.repository.ResourceRepository;

@Service
@Transactional
public class AdminService {
    private final ResourceRepository resourceRepository;
    private final ResourceCategoryRepository categoryRepository;
    private final ReservationRepository reservationRepository;

    public AdminService(
            ResourceRepository resourceRepository,
            ResourceCategoryRepository categoryRepository,
            ReservationRepository reservationRepository) {
        this.resourceRepository = resourceRepository;
        this.categoryRepository = categoryRepository;
        this.reservationRepository = reservationRepository;
    }

    public ResourceResponse createResource(ResourceUpsertRequest request) {
        Resource resource = Resource.builder().build();
        apply(resource, request);
        resourceRepository.save(resource);
        return ResourceResponse.from(resource);
    }

    public ResourceResponse updateResource(Long id, ResourceUpsertRequest request) {
        Resource resource = resourceRepository.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El recurso no existe"));
        apply(resource, request);
        return ResourceResponse.from(resource);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listReservations(ReservationStatus status) {
        return reservationRepository.findAllForAdmin(status).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    public ReservationResponse approve(Long reservationId) {
        return changePendingStatus(reservationId, ReservationStatus.CONFIRMED);
    }

    public ReservationResponse reject(Long reservationId) {
        return changePendingStatus(reservationId, ReservationStatus.REJECTED);
    }

    private ReservationResponse changePendingStatus(Long reservationId, ReservationStatus newStatus) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe"));
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden aprobar o rechazar reservas pendientes");
        }
        reservation.setStatus(newStatus);
        return ReservationResponse.from(reservation);
    }

    private void apply(Resource resource, ResourceUpsertRequest request) {
        if (!request.closeTime().isAfter(request.openTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora de cierre debe ser posterior a la de apertura");
        }
        ResourceCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría no existe"));

        resource.setCategory(category);
        resource.setName(request.name().trim());
        resource.setDescription(request.description());
        resource.setResourceType(request.resourceType());
        resource.setLocation(request.location());
        resource.setCapacity(request.capacity());
        resource.setQuantity(request.resourceType() == ResourceType.SPACE ? 1 : request.quantity());
        resource.setOpenTime(request.openTime());
        resource.setCloseTime(request.closeTime());
        resource.setMaxDurationMinutes(request.maxDurationMinutes());
        resource.setRequiresApproval(request.requiresApproval());
        resource.setState(request.state() == null ? ResourceState.AVAILABLE : request.state());
        resource.setImageUrl(request.imageUrl());
    }
}
