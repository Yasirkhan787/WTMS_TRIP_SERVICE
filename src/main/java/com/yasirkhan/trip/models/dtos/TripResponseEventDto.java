package com.yasirkhan.trip.models.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yasirkhan.trip.models.enums.EventStatus;
import com.yasirkhan.trip.models.enums.EventType;
import com.yasirkhan.trip.responses.TripResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TripResponseEventDto {
    private EventType type;
    private EventStatus eventTypeStatus;
    private TripResponse tripData;
}
