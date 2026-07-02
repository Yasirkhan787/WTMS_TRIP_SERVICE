package com.yasirkhan.trip.requests;

import lombok.Data;

import java.util.UUID;

@Data
public class StartTripRequest {
    private UUID scheduleId;
    private String vehicleNo;
}