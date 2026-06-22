package com.yasirkhan.trip.utils;

import com.yasirkhan.trip.models.entities.TripWeight;
import com.yasirkhan.trip.responses.TripResponse;
import com.yasirkhan.trip.responses.TripSummaryResponse;

import java.util.List;
import java.util.stream.Collectors;

public class ResponseConversion {

    public static TripSummaryResponse toTripSummaryResponse(List<TripWeight> trips, Double totalTonnage){

        List<TripResponse> tripResponses =
                trips
                        .stream()
                        .map(ResponseConversion::toTripResponse)
                        .collect(Collectors.toList());

        return TripSummaryResponse
                .builder()
                .trips(tripResponses)
                .totalTonnage(totalTonnage)
                .build();
    }

    public static TripResponse toTripResponse(TripWeight trip){

        return TripResponse
                .builder()
                .tripId(trip.getId())
                .scheduleId(trip.getScheduleId())
                .slipId(trip.getSlipId())
                .vehicleNo(trip.getVehicleNo())
                .loadTime(trip.getLoadTime())
                .loadWeight(trip.getLoadWeight())
                .emptyTime(trip.getEmptyTime())
                .emptyWeight(trip.getEmptyWeight())
                .netWeight(trip.getNetWeight())
                .actualDistance(trip.getActualDistance())
                .fuelConsumed(trip.getFuelConsumed())
                .encodedPolyline(SpatialUtils.toPolyLine(trip.getPath()))
                .status(trip.getStatus().name())
                .build();
    }
}
