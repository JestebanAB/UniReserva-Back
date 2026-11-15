package com.unireserva.back.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unireserva.back.model.AgentInteraction;

public interface AgentInteractionRepository extends JpaRepository<AgentInteraction, Long> {
}
