package com.cloudops.repository;

import com.cloudops.domain.model.HealthSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface HealthSignalRepository extends JpaRepository<HealthSignal, UUID> {

    /**
     * Returns the 20 most recent signals for a service, newest first.
     * Spring Data JPA derives this query automatically from the method name.
     */
    List<HealthSignal> findTop20ByServiceIdOrderByRecordedAtDesc(UUID serviceId);

    /**
     * Returns all threshold-breached signals for a service within a time window.
     * Used by the rule-based analyzer to detect sustained breaches.
     */
    @Query("""
            SELECT s FROM HealthSignal s
            WHERE s.service.id = :serviceId
              AND s.thresholdBreached = true
              AND s.recordedAt >= :since
            ORDER BY s.recordedAt DESC
           """)
    List<HealthSignal> findBreachedSignalsSince(
            @Param("serviceId") UUID serviceId,
            @Param("since") Instant since);

    /**
     * Counts breached signals in a rolling window.
     */
    @Query("""
            SELECT COUNT(s) FROM HealthSignal s
            WHERE s.service.id = :serviceId
              AND s.thresholdBreached = true
              AND s.recordedAt >= :since
           """)
    long countBreachedSignalsSince(
            @Param("serviceId") UUID serviceId,
            @Param("since") Instant since);

    /**
     * Deletes signals for a service older than the given cutoff timestamp.
     * Used by the rolling-buffer trim to keep storage bounded.
     * Requires an active transaction — callers must be @Transactional.
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM HealthSignal s
            WHERE s.service.id = :serviceId
              AND s.recordedAt < :cutoff
           """)
    int deleteOlderThan(
            @Param("serviceId") UUID serviceId,
            @Param("cutoff") Instant cutoff);
}
