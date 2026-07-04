package com.yasirkhan.trip.models.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemAlertEventDto {
    private String eventType;
    private String targetTehsilId;
    private String vehicleNo;
    private String title;
    private String message;
}