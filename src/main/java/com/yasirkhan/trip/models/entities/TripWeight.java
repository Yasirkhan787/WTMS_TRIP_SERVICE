package com.yasirkhan.trip.models.entities;

import com.yasirkhan.trip.models.enums.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.LineString;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "WTMS_TRIP_WEIGHTS")
public class TripWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID scheduleId;

    @Column(unique = true)
    private String slipId;

    @Column(nullable = false)
    private String vehicleNo;

    @Column(nullable = false)
    private LocalDateTime loadTime;

    @Column(nullable = false)
    private Double loadWeight;

    private LocalDateTime emptyTime;

    private Double emptyWeight;

    private Double netWeight;

    private Double actualDistance;

    private Double fuelConsumed;

    @Column(columnDefinition = "geometry(LineString, 4326)")
    private LineString path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
}