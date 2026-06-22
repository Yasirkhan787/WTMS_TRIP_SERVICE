package com.yasirkhan.trip.repositories;

import com.yasirkhan.trip.models.entities.TripWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TripWeightRepository extends JpaRepository<TripWeight, UUID> {

    // Ensures the automated sync doesn't save the same weighbridge slip twice
    boolean existsBySlipId(String slipId);

    // Used for querying all completed trips within a specific shift or day
    List<TripWeight> findByLoadTimeBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);

    // Quick lookup to find a specific trip assigned to a schedule
    TripWeight findByScheduleId(UUID scheduleId);

    // Sums the net weight for a specific date (Efficient SQL calculation)
    @Query("SELECT SUM(t.netWeight) FROM TripWeight t WHERE t.loadTime >= :start AND t.loadTime < :end")
    Double calculateTotalTonnageForDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}