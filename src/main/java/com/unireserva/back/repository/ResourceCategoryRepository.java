package com.unireserva.back.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unireserva.back.model.ResourceCategory;

public interface ResourceCategoryRepository extends JpaRepository<ResourceCategory, Long> {
    List<ResourceCategory> findAllByOrderByNameAsc();

    Optional<ResourceCategory> findByNameIgnoreCase(String name);
}
