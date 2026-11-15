package com.unireserva.back.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.unireserva.back.model.Resource;
import com.unireserva.back.model.ResourceState;
import com.unireserva.back.model.ResourceType;

import jakarta.persistence.LockModeType;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /** Catálogo filtrable. Los filtros en null se ignoran; q usa cadena vacía para "sin búsqueda". */
    @Query("""
            SELECT r FROM Resource r JOIN FETCH r.category c
            WHERE (:state IS NULL OR r.state = :state)
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:type IS NULL OR r.resourceType = :type)
              AND (:q = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(r.description) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY c.name, r.name
          """)
    List<Resource> search(
            @Param("state") ResourceState state,
            @Param("categoryId") Long categoryId,
            @Param("type") ResourceType type,
            @Param("q") String q);

    @Query("SELECT r FROM Resource r JOIN FETCH r.category WHERE r.id = :id")
    Optional<Resource> findDetailedById(@Param("id") Long id);

    /**
     * Bloquea la fila del recurso mientras dura la transacción. Así dos personas que reservan
     * el mismo recurso al mismo tiempo se procesan una después de la otra y no se doble-reserva.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Resource r JOIN FETCH r.category WHERE r.id = :id")
    Optional<Resource> findByIdForUpdate(@Param("id") Long id);
}
