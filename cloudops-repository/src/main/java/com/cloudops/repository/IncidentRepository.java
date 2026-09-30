package com.cloudops.repository;

import com.cloudops.domain.enums.IncidentStatus;
import com.cloudops.domain.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findAllByStatusOrderByCreatedAtDesc(IncidentStatus status);

    List<Incident> findAllByOrderByCreatedAtDesc();

    /**
     * Finds open (DRAFT or ACKNOWLEDGED) incidents for a service.
     * Used by the deduplication guard before creating a new draft.
     */
    @Query("""
            SELECT i FROM Incident i
            WHERE i.service.id = :serviceId
              AND i.status IN :openStatuses
            ORDER BY i.createdAt DESC
           """)
    List<Incident> findOpenByServiceId(
            @Param("serviceId") UUID serviceId,
            @Param("openStatuses") List<IncidentStatus> openStatuses);

    /**
     * Finds DRAFT incidents for a service.
     * Targeted by the auto-resolve logic on RECOVERED transition.
     */
    @Query("""
            SELECT i FROM Incident i
            WHERE i.service.id = :serviceId
              AND i.status = :status
           """)
    List<Incident> findByServiceIdAndStatus(
            @Param("serviceId") UUID serviceId,
            @Param("status") IncidentStatus status);

    Optional<Incident> findTopByServiceIdAndStatusOrderByCreatedAtDesc(
            UUID serviceId, IncidentStatus status);
}
