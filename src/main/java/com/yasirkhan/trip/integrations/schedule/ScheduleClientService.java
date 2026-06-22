package com.yasirkhan.trip.integrations.schedule;

import com.yasirkhan.trip.configurations.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient(name = "schedule-service", path = "/schedule", configuration = FeignConfig.class)
public interface ScheduleClientService {

    @GetMapping("/active-for-trip")
    ScheduleResponseDto findActiveScheduleForTrip(
            @RequestParam("vehicleNo") String vehicleNo,
            @RequestParam("targetDate") String targetDate,
            @RequestParam("targetTime") String targetTime
    );
}