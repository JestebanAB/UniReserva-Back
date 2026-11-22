package com.unireserva.back.service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.unireserva.back.dto.ReservationResponse;
import com.unireserva.back.dto.ResourceResponse;
import com.unireserva.back.dto.ResourceUpsertRequest;
import com.unireserva.back.model.Reservation;
import com.unireserva.back.model.ReservationStatus;
import com.unireserva.back.model.Resource;
import com.unireserva.back.model.ResourceCategory;
import com.unireserva.back.model.ResourceImage;
import com.unireserva.back.model.ResourceState;
import com.unireserva.back.model.ResourceType;
import com.unireserva.back.repository.ReservationRepository;
import com.unireserva.back.repository.ResourceCategoryRepository;
import com.unireserva.back.repository.ResourceImageRepository;
import com.unireserva.back.repository.ResourceRepository;

@Service
@Transactional
public class AdminService {
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;

    private final ResourceRepository resourceRepository;
    private final ResourceCategoryRepository categoryRepository;
    private final ReservationRepository reservationRepository;
    private final ResourceImageRepository resourceImageRepository;

    public AdminService(
            ResourceRepository resourceRepository,
            ResourceCategoryRepository categoryRepository,
            ReservationRepository reservationRepository,
            ResourceImageRepository resourceImageRepository) {
        this.resourceRepository = resourceRepository;
        this.categoryRepository = categoryRepository;
        this.reservationRepository = reservationRepository;
        this.resourceImageRepository = resourceImageRepository;
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

    public ResourceResponse uploadResourceImage(Long resourceId, MultipartFile image) {
        if (image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar una imagen");
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen no puede superar 5 MB");
        }

        byte[] imageData;
        try {
            imageData = image.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen", exception);
        }

        String contentType = image.getContentType();
        if (!isSupportedImage(contentType, imageData)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "La imagen debe ser PNG, JPEG, GIF o WebP");
        }

        Resource resource = resourceRepository.findDetailedById(resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El recurso no existe"));
        ResourceImage resourceImage = resourceImageRepository.findById(resourceId).orElseGet(ResourceImage::new);
        resourceImage.setResource(resource);
        resourceImage.setContentType(contentType);
        resourceImage.setImageData(imageData);
        resourceImageRepository.save(resourceImage);
        resource.setImageUrl("/api/resources/" + resourceId + "/image");
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

    private static boolean isSupportedImage(String contentType, byte[] data) {
        if (contentType == null) {
            return false;
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> startsWith(data, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "image/jpeg" -> startsWith(data, 0xff, 0xd8, 0xff);
            case "image/gif" -> startsWith(data, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
                    || startsWith(data, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61);
            case "image/webp" -> startsWith(data, 0x52, 0x49, 0x46, 0x46)
                    && data.length >= 12
                    && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50;
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xff) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
