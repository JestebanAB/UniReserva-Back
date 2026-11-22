package com.unireserva.back.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resource_images")
@Getter
@Setter
@NoArgsConstructor
public class ResourceImage {
    @Id
    @Column(name = "resource_id")
    private Long resourceId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id")
    private Resource resource;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "image_data", nullable = false, columnDefinition = "bytea")
    private byte[] imageData;
}
