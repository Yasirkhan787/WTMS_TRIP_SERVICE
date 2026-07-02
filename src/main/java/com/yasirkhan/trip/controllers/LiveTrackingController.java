package com.yasirkhan.trip.controllers;

import com.yasirkhan.trip.requests.DelayReportRequest;
import com.yasirkhan.trip.requests.StartTripRequest;
import com.yasirkhan.trip.services.LiveTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trip/live")
public class LiveTrackingController {

    private final LiveTrackingService trackingService;

    public LiveTrackingController(LiveTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    // Triggered when Driver clicks "START TRIP"
    @PostMapping("/start")
    public ResponseEntity<?> startLiveTrip(@RequestBody StartTripRequest request) {
        trackingService.initializeTripState(request);
        return ResponseEntity.ok("Trip started successfully. Tracking active.");
    }

    // Triggered when Driver clicks "REPORT DELAY" -> "SUBMIT REPORT"
    @PostMapping("/delay")
    public ResponseEntity<?> reportDelay(@RequestBody DelayReportRequest request) {
        trackingService.registerManualDelay(request);
        return ResponseEntity.ok("Delay reported successfully. Supervisor notified.");
    }

    // Triggered when Driver clicks "END TRIP"
    @PostMapping("/end/{vehicleNo}")
    public ResponseEntity<?> endLiveTrip(@PathVariable String vehicleNo) {
        trackingService.terminateTripState(vehicleNo);
        return ResponseEntity.ok("Trip ended successfully.");
    }
}