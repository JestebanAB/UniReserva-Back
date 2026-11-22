package com.unireserva.back.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.unireserva.back.dto.AvailabilityResponse;
import com.unireserva.back.dto.CategoryResponse;
import com.unireserva.back.dto.ResourceResponse;
import com.unireserva.back.model.ResourceImage;
import com.unireserva.back.model.ResourceType;
import com.unireserva.back.service.ResourceService;

/** Catálogo de recursos. Cualquier usuario autenticado puede consultarlo. */
@RestController
@RequestMapping("/api")
public class ResourceController {
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return resourceService.getCategories();
    }

    @GetMapping("/resources")
    public List<ResourceResponse> resources(
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "type", required = false) ResourceType type,
            @RequestParam(name = "q", required = false) String q) {
        return resourceService.search(categoryId, type, q);
    }

    @GetMapping("/resources/{id}")
    public ResourceResponse resource(@PathVariable("id") Long id) {
        return resourceService.getById(id);
    }

    @GetMapping("/resources/{id}/image")
    public ResponseEntity<byte[]> resourceImage(@PathVariable("id") Long id) {
        ResourceImage image = resourceService.getImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(image.getImageData());
    }

    @GetMapping("/resources/{id}/availability")
    public AvailabilityResponse availability(
            @PathVariable("id") Long id,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return resourceService.getAvailability(id, date);
    }
}
