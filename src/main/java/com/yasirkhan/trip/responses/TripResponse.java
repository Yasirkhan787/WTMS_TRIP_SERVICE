package com.yasirkhan.trip.responses;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TripResponse {

    private UUID tripId;

    private UUID scheduleId;

    private String slipId;

    private String vehicleNo;

    private LocalDateTime loadTime;

    private Double loadWeight;

    private Double actualDistance;

    private LocalDateTime emptyTime;

    private Double emptyWeight;

    private Double netWeight;

    private Double fuelConsumed;

    private String encodedPolyline;

    private String status;
}
