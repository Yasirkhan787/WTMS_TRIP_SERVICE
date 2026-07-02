package com.yasirkhan.trip.requests;

import lombok.Data;

@Data
public class DelayReportRequest {
    private String vehicleNo;
    private String delayReason; // e.g., "Heavy Traffic", "Breakdown", "Fuel Stop"
    private String additionalDetails; // Optional notes
    private Double currentLat;
    private Double currentLng;
}