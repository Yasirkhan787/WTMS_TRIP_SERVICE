package com.yasirkhan.trip.services.implementations;

import com.yasirkhan.trip.requests.DelayReportRequest;
import com.yasirkhan.trip.requests.StartTripRequest;
import com.yasirkhan.trip.services.LiveTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

        // Verify the schedule exists in Redis
        Map<Object, Object> scheduleData = redisTemplate.opsForHash().entries(scheduleKey);
        if (scheduleData.isEmpty()) {
            throw new RuntimeException("Schedule not found or not active in cache.");
        }

        String routeId = scheduleData.get("routeId").toString();
        String routeKey = "wtms:route:" + routeId;

        // Fetch the Route & Yard data populated by your Fleet Service
        Map<Object, Object> routeData = redisTemplate.opsForHash().entries(routeKey);
        if (routeData.isEmpty()) {
            throw new RuntimeException("Route spatial data not found in cache.");
        }

        // Initialize the Live State Machine
        Map<String, String> liveState = new HashMap<>();
        liveState.put("scheduleId", request.getScheduleId().toString());
        liveState.put("driverId", scheduleData.get("driverId").toString());
        liveState.put("routeId", routeId);
        liveState.put("tehsilId", routeData.get("tehsilId").toString());

        liveState.put("sourceYardId", routeData.get("sourceYardId").toString());
        liveState.put("destinationYardId", routeData.get("destinationYardId").toString());

        // Start phase is at the Temporary Collection Point (Source Yard)
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
        String driverId = activeState.get("driverId").toString();

        // Build the System Alert Event to send to the Notification Service via Kafka
        Map<String, Object> alertEvent = new HashMap<>();
        alertEvent.put("eventType", "EMERGENCY"); // Categorizes as ACTION_REQUIRED in your Notification DB
        alertEvent.put("targetTehsilId", tehsilId);
        alertEvent.put("vehicleNo", request.getVehicleNo());
        alertEvent.put("title", "Delay: " + request.getDelayReason()); // e.g., "Delay: Heavy Traffic"

        String details = request.getAdditionalDetails() != null ? request.getAdditionalDetails() : "No additional notes provided.";
        alertEvent.put("message", String.format("Vehicle %s reported a delay. Reason: %s. Notes: %s",
                request.getVehicleNo(), request.getDelayReason(), details));

        // Fire to Kafka so the NotificationConsumer picks it up and alerts the Supervisor!
        kafkaTemplate.send("system-alerts-topic", alertEvent);

        log.info("Delay reported for Vehicle {}. Supervisor alerted.", request.getVehicleNo());
    }

    @Override
    public void terminateTripState(String vehicleNo) {
        String trackingKey = "wtms:tracking:" + vehicleNo;

        // Instead of deleting, loop the state back to returning
        Boolean exists = redisTemplate.hasKey(trackingKey);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.opsForHash().put(trackingKey, "currentPhase", "RETURNING_TO_TCP");
            redisTemplate.opsForHash().put(trackingKey, "isOffRoute", "false");
            redisTemplate.opsForHash().put(trackingKey, "offRouteCount", "0");
            log.info("Trip closed at dumpsite weighbridge. Vehicle {} set to RETURNING_TO_TCP.", vehicleNo);
        }
    }
}