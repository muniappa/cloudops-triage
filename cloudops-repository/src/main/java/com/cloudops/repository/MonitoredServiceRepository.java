package com.cloudops.repository;

import com.cloudops.domain.enums.ServiceHealthStatus;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, UUID> {

    Optional<MonitoredService> findByName(String name);

    List<MonitoredService> findAllByHealthStatus(ServiceHealthStatus healthStatus);

    boolean existsByName(String name);

    /**
     * Returns the most recent signals for a service, ordered newest first.
     * Pass {@code Pageable.ofSize(n)} to limit results — avoids non-standard
     * LIMIT clause in JPQL.
     */
    @Query("""
            SELECT s FROM MonitoredService ms
            JOIN ms.recentSignals s
            WHERE ms.id = :serviceId
            ORDER BY s.recordedAt DESC
           """)
    List<HealthSignal> findRecentSignals(@Param("serviceId") UUID serviceId, Pageable pageable);
}
