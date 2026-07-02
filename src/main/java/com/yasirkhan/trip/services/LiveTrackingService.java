package com.yasirkhan.trip.services;

import com.yasirkhan.trip.requests.DelayReportRequest;
import com.yasirkhan.trip.requests.StartTripRequest;

public interface LiveTrackingService {

    void initializeTripState(StartTripRequest request);

    void registerManualDelay(DelayReportRequest request);

    void terminateTripState(String vehicleNo);
}
