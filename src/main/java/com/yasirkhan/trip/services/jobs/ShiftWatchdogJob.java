package com.yasirkhan.trip.services.jobs;

import com.yasirkhan.trip.models.dtos.SystemAlertEventDto; // <-- Make sure you have this DTO in your Trip Service!
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ShiftWatchdogJob {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ShiftWatchdogJob(RedisTemplate<String, Object> redisTemplate, KafkaTemplate<String, Object> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    public void monitorPassiveShifts() {
        log.info("Running Tier 1 Passive Shift Watchdog via Redis Index...");

        String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dailyIndexKey = "wtms:schedules:daily:" + todayDate;

        // Fetch all Schedule IDs assigned for today from the Redis Set in O(1) time
        Set<Object> activeScheduleIds = redisTemplate.opsForSet().members(dailyIndexKey);

        if (activeScheduleIds == null || activeScheduleIds.isEmpty()) {
            return; // No schedules assigned for today
        }

        LocalTime now = LocalTime.now();

        for (Object idObj : activeScheduleIds) {
            String scheduleId = idObj.toString();
            String scheduleKey = "wtms:schedule:" + scheduleId;

            // Fetch the Schedule details directly from Redis Hash
            Map<Object, Object> scheduleData = redisTemplate.opsForHash().entries(scheduleKey);
            if (scheduleData.isEmpty()) continue;

            String vehicleNo = (String) scheduleData.get("vehicleNo");
            String tehsilId = (String) scheduleData.get("tehsilId");

            // Safely parse times from the cached String format
            LocalTime startTime = LocalTime.parse((String) scheduleData.get("startTime"));
            LocalTime endTime = LocalTime.parse((String) scheduleData.get("endTime"));

            // Time Window Check: Is the current time inside the Shift Template window?
            if (now.isBefore(startTime) || now.isAfter(endTime)) {
                continue; // Outside shift hours, ignore
            }

            // BLIND SPOT CHECK: If trackingKey exists, the driver is actively working. Skip them.
            String trackingKey = "wtms:tracking:" + vehicleNo;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(trackingKey))) {
                continue;
            }

            // ===============================================================
            // TIER 1 EVALUATION: Driver is on the clock but has NO active trip
            // ===============================================================

            // FIXED: Read from the correct live telemetry hash synced by LiveLocationWatchdogConsumer
            Object speedObj = redisTemplate.opsForHash().get("wtms:live:vehicle:" + vehicleNo, "speed");

            if (speedObj != null) {
                double currentSpeed = Double.parseDouble(speedObj.toString());

                String idleKey = "wtms:idle:" + vehicleNo;
                String penaltyKey = "wtms:flags:idle_penalty:" + vehicleNo;

                if (currentSpeed == 0.0) {

                    // ANTI-SPAM GATE: Have we already alerted the supervisor for this specific parking incident?
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(penaltyKey))) {
                        continue; // Vehicle is still parked. Do not spam. Wait for them to move.
                    }

                    // Increment Idle Counter
                    Long idleMinutes = redisTemplate.opsForValue().increment(idleKey, 5); // Add 5 mins
                    redisTemplate.expire(idleKey, 4, TimeUnit.HOURS); // Clean up memory automatically later

                    if (idleMinutes != null && idleMinutes >= 20) {

                        // FIRE IDLE ALERT: Truck parked for 20+ mins during shift
                        sendSystemAlertEvent(
                                tehsilId,
                                vehicleNo,
                                "EMERGENCY",
                                "Unplanned Idle Detected",
                                String.format("Vehicle %s has been stationary for 20+ minutes during scheduled shift without starting a trip.", vehicleNo)
                        );

                        // Set the Penalty Flag for 12 hours so we don't alert again until they move!
                        redisTemplate.opsForValue().set(penaltyKey, "true", 12, TimeUnit.HOURS);

                        // Reset counter since alert was fired
                        redisTemplate.delete(idleKey);
                    }
                } else {
                    // Truck is moving! Reset EVERYTHING so they can be tracked fairly again.
                    redisTemplate.delete(idleKey);
                    redisTemplate.delete(penaltyKey);
                }
            }
        }
    }

    // FIXED: Safely uses the DTO so the Notification Service doesn't crash on deserialization
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