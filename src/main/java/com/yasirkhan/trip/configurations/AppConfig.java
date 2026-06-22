package com.yasirkhan.trip.configurations;

import com.yasirkhan.trip.services.TripService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling
public class AppConfig {

    private final TripService tripService;

    public AppConfig(TripService tripService) {
        this.tripService = tripService;
    }

    @Scheduled(fixedRate = 1800000)
    public void syncAskTrackData() {
        tripService.syncDailyTrips("rwmc");
    }
}