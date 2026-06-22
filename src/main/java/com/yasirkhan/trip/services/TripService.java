package com.yasirkhan.trip.services;

import com.yasirkhan.trip.responses.TripSummaryResponse;

import java.time.LocalDate;

public interface TripService {

    void syncDailyTrips(String wmc);

    TripSummaryResponse getTripsByDate(LocalDate targetDate);
}
