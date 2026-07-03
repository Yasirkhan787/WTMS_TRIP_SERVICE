package com.yasirkhan.trip.models.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yasirkhan.trip.models.enums.EventStatus;
import com.yasirkhan.trip.models.enums.EventType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleResponseEventDto {
    private EventType type;
    private EventStatus eventTypeStatus;
    private ScheduleDataDto scheduleData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScheduleDataDto {
        private UUID scheduleId;
        private String vehicleNo;
        private UUID driverId;
        private UUID routeId;
        private UUID tehsilId;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
        private LocalDate scheduleDate;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        private LocalTime startTime;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        private LocalTime endTime;

        private String scheduleStatus;
    }
}