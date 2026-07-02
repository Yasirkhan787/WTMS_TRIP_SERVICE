package com.yasirkhan.trip.models.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yasirkhan.trip.models.enums.EventStatus;
import com.yasirkhan.trip.models.enums.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteResponseEventDto {
    private EventType type;
    private EventStatus eventTypeStatus;
    private RouteResponse routeData;
    private YardResponse yardData;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteResponse {
        private UUID routeId;
        private String routeName;
        private UUID tehsilId;
        private String tehsilName;

        private UUID sourceYardId;
        private String sourceYardName;
        private String sourceYardType;

        private UUID destinationYardId;
        private String destinationYardName;
        private String destinationYardType;

        private String path; // The LineString WKT format
        private String estimatedDistance;
        private String estimatedTime;
        private String status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class YardResponse {

        private UUID yardId;
        private String yardName;
        private String yardType;
        private String status;

        private UUID tehsilId;
        private String tehsilName;

        private String boundaryType;
        private CoordinateDto centerCoords;
        private Double radiusMeters;
        private String polygonPath;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoordinateDto {

        private Double lat;

        private Double lng;
    }


}
