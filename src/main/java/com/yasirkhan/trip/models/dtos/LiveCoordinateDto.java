package com.yasirkhan.trip.models.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiveCoordinateDto {
    private String vehicleNo;
    private double latitude;
    private double longitude;
    private double speed;
    private boolean engine;
}