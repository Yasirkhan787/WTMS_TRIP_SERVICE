package com.yasirkhan.trip.responses;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TripSummaryResponse {

    private List<TripResponse> trips;
    private Double totalTonnage;

}
