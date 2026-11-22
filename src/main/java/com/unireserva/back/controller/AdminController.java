package com.unireserva.back.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.unireserva.back.dto.ReservationResponse;
import com.unireserva.back.dto.ResourceResponse;
import com.unireserva.back.dto.ResourceUpsertRequest;
import com.unireserva.back.model.ReservationStatus;
import com.unireserva.back.model.ResourceType;
import com.unireserva.back.service.AdminService;
import com.unireserva.back.service.ResourceService;

import jakarta.validation.Valid;

/** Panel de administración. El acceso (rol ADMIN) lo controla SecurityConfig por el prefijo /api/admin. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final ResourceService resourceService;

    public AdminController(AdminService adminService, ResourceService resourceService) {
        this.adminService = adminService;
        this.resourceService = resourceService;
    }

    @GetMapping("/resources")
    public List<ResourceResponse> resources(
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "type", required = false) ResourceType type,
            @RequestParam(name = "q", required = false) String q) {
        return resourceService.searchAll(categoryId, type, q);
    }

    @PostMapping("/resources")
    public ResponseEntity<ResourceResponse> createResource(@Valid @RequestBody ResourceUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createResource(request));
    }

    @PutMapping("/resources/{id}")
    public ResourceResponse updateResource(
            @PathVariable("id") Long id,
            @Valid @RequestBody ResourceUpsertRequest request) {
        return adminService.updateResource(id, request);
    }

    @PostMapping(value = "/resources/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResourceResponse uploadResourceImage(
            @PathVariable("id") Long id,
            @RequestParam("image") MultipartFile image) {
        return adminService.uploadResourceImage(id, image);
    }

    @GetMapping("/reservations")
    public List<ReservationResponse> reservations(
            @RequestParam(name = "status", required = false) ReservationStatus status) {
        return adminService.listReservations(status);
    }

    @PostMapping("/reservations/{id}/approve")
    public ReservationResponse approve(@PathVariable("id") Long id) {
        return adminService.approve(id);
    }

    @PostMapping("/reservations/{id}/reject")
    public ReservationResponse reject(@PathVariable("id") Long id) {
        return adminService.reject(id);
    }
}
