package com.unireserva.back.repository;

import com.unireserva.back.model.Roles;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolesRepository extends JpaRepository<Roles, Long> {
    Optional<Roles> findByNameIgnoreCase(String name);
}
