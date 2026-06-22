package com.yasirkhan.trip.controllers;

import com.yasirkhan.trip.responses.TripSummaryResponse;
import com.yasirkhan.trip.services.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/trip")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    // Get All Trips by date
    @GetMapping
    public ResponseEntity<TripSummaryResponse> getTripSummary(@RequestParam(required = false) String date) {

        LocalDate targetDate = (date == null) ? LocalDate.now() : LocalDate.parse(date);
        return ResponseEntity.ok(tripService.getTripsByDate(targetDate));
    }
}
