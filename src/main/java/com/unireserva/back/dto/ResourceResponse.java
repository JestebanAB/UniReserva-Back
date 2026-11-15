package com.unireserva.back.dto;

import java.time.LocalTime;

import com.unireserva.back.model.Resource;
import com.unireserva.back.model.ResourceState;
import com.unireserva.back.model.ResourceType;

public record ResourceResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        ResourceType resourceType,
        String location,
        Integer capacity,
        Integer quantity,
        LocalTime openTime,
        LocalTime closeTime,
        Integer maxDurationMinutes,
        boolean requiresApproval,
        ResourceState state,
        String imageUrl
) {
    public static ResourceResponse from(Resource r) {
        return new ResourceResponse(
                r.getId(),
                r.getCategory().getId(),
                r.getCategory().getName(),
                r.getName(),
                r.getDescription(),
                r.getResourceType(),
                r.getLocation(),
                r.getCapacity(),
                r.getQuantity(),
                r.getOpenTime(),
                r.getCloseTime(),
                r.getMaxDurationMinutes(),
                r.isRequiresApproval(),
                r.getState(),
                r.getImageUrl());
    }
}
