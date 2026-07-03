package com.yasirkhan.trip.consumers;

import com.yasirkhan.trip.models.dtos.ScheduleResponseEventDto.ScheduleDataDto;
import com.yasirkhan.trip.models.dtos.ScheduleResponseEventDto;
import com.yasirkhan.trip.models.enums.EventStatus; // <-- Make sure to import the Enum!
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ScheduleEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    public ScheduleEventConsumer(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(
            topics = "schedule-response-topic",
            groupId = "trip-group",
            containerFactory = "listenerContainerFactory"
    )
    public void handleScheduleEvent(ScheduleResponseEventDto event) {
        log.info("Received Schedule event from Kafka for Vehicle No: {} with status: {}",
                event.getScheduleData().getVehicleNo(), event.getEventTypeStatus());

        // FIXED: Properly compare the Enum type instead of a String!
        if (event.getEventTypeStatus() != EventStatus.SUCCESS || event.getScheduleData() == null) {
            log.warn("Skipping Schedule cache sync because event status was not SUCCESS or data was null.");
            return;
        }

        ScheduleDataDto schedule = event.getScheduleData();
        String scheduleId = schedule.getScheduleId().toString();
        String scheduleKey = "wtms:schedule:" + scheduleId;

        String dateString = schedule.getScheduleDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dailyIndexKey = "wtms:schedules:daily:" + dateString;

        String status = schedule.getScheduleStatus();

        // If the schedule is active/ongoing, sync it to the Daily Index
        if ("ASSIGNED".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status)) {

            Map<String, String> data = new HashMap<>();
            data.put("vehicleNo", schedule.getVehicleNo());
            data.put("driverId", schedule.getDriverId() != null ? schedule.getDriverId().toString() : "");
            data.put("routeId", schedule.getRouteId() != null ? schedule.getRouteId().toString() : "");
            data.put("tehsilId", schedule.getTehsilId() != null ? schedule.getTehsilId().toString() : "");
            data.put("startTime", schedule.getStartTime() != null ? schedule.getStartTime().toString() : "");
            data.put("endTime", schedule.getEndTime() != null ? schedule.getEndTime().toString() : "");
            data.put("status", status);

            // 1. Save Hash
            redisTemplate.opsForHash().putAll(scheduleKey, data);
            redisTemplate.expire(scheduleKey, 48, TimeUnit.HOURS);

            // 2. Add to Daily Watchdog Index
            redisTemplate.opsForSet().add(dailyIndexKey, scheduleId);
            redisTemplate.expire(dailyIndexKey, 48, TimeUnit.HOURS);

            log.info("Synced active Schedule {} to Redis. Status: {}", scheduleId, status);
        }
        // If the schedule is finished/cancelled, clean up the cache
        else {
            // Remove from the daily watchdog index so it stops tracking
            redisTemplate.opsForSet().remove(dailyIndexKey, scheduleId);

            // Delete the schedule details
            redisTemplate.delete(scheduleKey);

            // Clean up any lingering active tracking or idle penalty states for this vehicle
            redisTemplate.delete("wtms:tracking:" + schedule.getVehicleNo());
            redisTemplate.delete("wtms:idle:" + schedule.getVehicleNo());

            log.info("Removed inactive Schedule {} from Redis. Status: {}", scheduleId, status);
        }
    }
}