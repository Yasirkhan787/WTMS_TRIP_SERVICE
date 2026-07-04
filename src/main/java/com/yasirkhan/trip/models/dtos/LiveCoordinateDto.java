package com.yasirkhan.trip.models.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiveCoordinateDto {

    @JsonProperty("registrationNumber")
    private String vehicleNo;
    private double latitude;
    private double longitude;
    private double speed;
    private boolean engine;
}