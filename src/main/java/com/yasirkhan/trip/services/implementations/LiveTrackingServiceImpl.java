package com.yasirkhan.trip.services.implementations;

import com.yasirkhan.trip.models.dtos.SystemAlertEventDto;
import com.yasirkhan.trip.models.dtos.ScheduleUpdateEventDto;
import com.yasirkhan.trip.requests.DelayReportRequest;
import com.yasirkhan.trip.requests.StartTripRequest;
import com.yasirkhan.trip.services.LiveTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class LiveTrackingServiceImpl implements LiveTrackingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LiveTrackingServiceImpl(RedisTemplate<String, Object> redisTemplate, KafkaTemplate<String, Object> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void initializeTripState(StartTripRequest request) {
        String vehicleNo = request.getVehicleNo();
        String trackingKey = "wtms:tracking:" + vehicleNo;
        String scheduleKey = "wtms:schedule:" + request.getScheduleId();

        Map<Object, Object> scheduleData = redisTemplate.opsForHash().entries(scheduleKey);
        if (scheduleData.isEmpty()) {
            throw new RuntimeException("Schedule not found or not active in cache.");
        }

        String currentStatus = (String) scheduleData.get("status");

        if ("COMPLETED".equalsIgnoreCase(currentStatus) || "DELAYED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalStateException("Cannot start a trip for a closed schedule.");
        }

        if ("ASSIGNED".equalsIgnoreCase(currentStatus)) {
            String startTimeStr = (String) scheduleData.get("startTime");
            if (startTimeStr != null && !startTimeStr.isEmpty()) {
                LocalTime startTime = LocalTime.parse(startTimeStr);
                if (LocalTime.now().isBefore(startTime.minusMinutes(30))) {
                    throw new IllegalStateException("Too early to start this trip. Please wait until 30 minutes before shift starts.");
                }
            }

            // 1. Instantly update Redis for real-time compliance watchdogs
            redisTemplate.opsForHash().put(scheduleKey, "status", "ACTIVE");

            // 2. Broadcast typed DTO via Kafka to update PostgreSQL database asynchronously
            ScheduleUpdateEventDto statusEvent = ScheduleUpdateEventDto.builder()
                    .scheduleId(request.getScheduleId())
                    .shiftStatus("ACTIVE")
                    .build();

            kafkaTemplate.send("schedule-update-topic", statusEvent);
            log.info("Driver preemptively activated ASSIGNED schedule {}. Sent DTO update to Kafka.", request.getScheduleId());
        }

        String routeId = scheduleData.get("routeId").toString();
        String routeKey = "wtms:route:" + routeId;

        Map<Object, Object> routeData = redisTemplate.opsForHash().entries(routeKey);
        if (routeData.isEmpty()) {
            throw new RuntimeException("Route spatial data not found in cache.");
        }

        Map<String, String> liveState = new HashMap<>();
        liveState.put("scheduleId", request.getScheduleId().toString());
        liveState.put("driverId", scheduleData.get("driverId").toString());
        liveState.put("routeId", routeId);
        liveState.put("tehsilId", routeData.get("tehsilId").toString());
        liveState.put("sourceYardId", routeData.get("sourceYardId").toString());
        liveState.put("destinationYardId", routeData.get("destinationYardId").toString());
        liveState.put("currentPhase", "AT_TCP");
        liveState.put("isOffRoute", "false");
        liveState.put("offRouteCount", "0");

        redisTemplate.opsForHash().putAll(trackingKey, liveState);
        log.info("Live tracking state initialized for Vehicle: {}", vehicleNo);
    }

    @Override
    public void registerManualDelay(DelayReportRequest request) {
        String trackingKey = "wtms:tracking:" + request.getVehicleNo();
        Map<Object, Object> activeState = redisTemplate.opsForHash().entries(trackingKey);
        if (activeState.isEmpty()) {
            throw new RuntimeException("Cannot report delay: Vehicle is not currently on an active trip.");
        }

        String tehsilId = activeState.get("tehsilId").toString();
        String title = "Delay: " + request.getDelayReason();
        String details = request.getAdditionalDetails() != null ? request.getAdditionalDetails() : "No additional notes provided.";
        String message = String.format("Vehicle %s reported a delay. Reason: %s. Notes: %s",
                request.getVehicleNo(), request.getDelayReason(), details);

        sendSystemAlertEvent(tehsilId, request.getVehicleNo(), "EMERGENCY", title, message);
        log.info("Delay reported for Vehicle {}. Supervisor alerted.", request.getVehicleNo());
    }

    @Override
    public void terminateTripState(String vehicleNo) {
        String trackingKey = "wtms:tracking:" + vehicleNo;
        Boolean exists = redisTemplate.hasKey(trackingKey);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.opsForHash().put(trackingKey, "currentPhase", "RETURNING_TO_TCP");
            redisTemplate.opsForHash().put(trackingKey, "isOffRoute", "false");
            redisTemplate.opsForHash().put(trackingKey, "offRouteCount", "0");
            log.info("Trip closed at dumpsite weighbridge. Vehicle {} set to RETURNING_TO_TCP.", vehicleNo);
        }
    }

    private void sendSystemAlertEvent(String tehsilId, String vehicleNo, String eventType, String title, String message) {
        SystemAlertEventDto alertEvent = SystemAlertEventDto.builder()
                .eventType(eventType)
                .targetTehsilId(tehsilId)
                .vehicleNo(vehicleNo)
                .title(title)
                .message(message)
                .build();
        kafkaTemplate.send("system-alerts-topic", alertEvent);
    }
}