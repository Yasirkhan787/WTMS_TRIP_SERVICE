package com.yasirkhan.trip.models.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yasirkhan.trip.models.enums.EventStatus;
import com.yasirkhan.trip.models.enums.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleResponseEventDto {
    private EventType type;
    private EventStatus eventTypeStatus;
    private ScheduleResponse scheduleData;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScheduleResponse {
        // Core Schedule Fields
        private UUID scheduleId;
        private String scheduleName;
        private String vehicleNo;
        private UUID driverId;
        private UUID routeId;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate scheduleDate;

        private UUID templateId;
        private String status;

        // Enriched Fields (from your ScheduleService enrichment)
        private String driverName;
        private String driverPhoneNo;
        private String driverStatus;
        private String vehicleStatus;

        private String routeName;
        private String routePath;
        private UUID tehsilId;
        private String tehsilName;

        private String destinationYardId;
        private String destinationYardName;
        private String destinationYardPolygonPath;
        private String destinationYardCenterLat;
        private String destinationYardCenterLng;
    }
}