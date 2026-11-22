package com.unireserva.back.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.unireserva.back.model.Resource;
import com.unireserva.back.model.ResourceCategory;
import com.unireserva.back.model.ResourceImage;
import com.unireserva.back.repository.ReservationRepository;
import com.unireserva.back.repository.ResourceCategoryRepository;
import com.unireserva.back.repository.ResourceImageRepository;
import com.unireserva.back.repository.ResourceRepository;

class AdminServiceTest {
    private ResourceRepository resourceRepository;
    private ResourceImageRepository resourceImageRepository;
    private AdminService service;
    private Resource resource;

    @BeforeEach
    void setUp() {
        resourceRepository = mock(ResourceRepository.class);
        resourceImageRepository = mock(ResourceImageRepository.class);
        service = new AdminService(
                resourceRepository,
                mock(ResourceCategoryRepository.class),
                mock(ReservationRepository.class),
                resourceImageRepository);
        resource = Resource.builder()
                .id(42L)
                .category(ResourceCategory.builder().id(1L).name("Auditorios").build())
                .name("Auditorio principal")
                .build();
        when(resourceRepository.findDetailedById(42L)).thenReturn(Optional.of(resource));
        when(resourceImageRepository.findById(42L)).thenReturn(Optional.empty());
    }

    @Test
    void storesImageBytesAndSetsItsResourceUrl() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile image = new MockMultipartFile("image", "auditorio.png", "image/png", png);

        service.uploadResourceImage(42L, image);

        org.mockito.ArgumentCaptor<ResourceImage> savedImage =
                org.mockito.ArgumentCaptor.forClass(ResourceImage.class);
        verify(resourceImageRepository).save(savedImage.capture());
        assertEquals(42L, savedImage.getValue().getResource().getId());
        assertEquals("image/png", savedImage.getValue().getContentType());
        assertArrayEquals(png, savedImage.getValue().getImageData());
        assertEquals("/api/resources/42/image", resource.getImageUrl());
    }

    @Test
    void rejectsContentThatDoesNotMatchItsImageType() {
        MockMultipartFile invalidImage =
                new MockMultipartFile("image", "not-an-image.png", "image/png", new byte[] {1, 2, 3});

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.uploadResourceImage(42L, invalidImage));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatusCode());
    }
}
