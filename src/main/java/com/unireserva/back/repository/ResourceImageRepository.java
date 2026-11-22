package com.unireserva.back.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unireserva.back.model.ResourceImage;

public interface ResourceImageRepository extends JpaRepository<ResourceImage, Long> {
}
