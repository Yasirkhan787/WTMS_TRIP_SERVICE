package com.yasirkhan.trip.services.jobs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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

            // Driver is on the clock but has NO active trip. Check their telemetry speed.
            Object speedObj = redisTemplate.opsForValue().get("wtms:telemetry:speed:" + vehicleNo);

            if (speedObj != null) {
                double currentSpeed = Double.parseDouble(speedObj.toString());

                if (currentSpeed == 0.0) {
                    // Increment Idle Counter
                    String idleKey = "wtms:idle:" + vehicleNo;
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

                        // Reset counter to avoid spamming every 5 mins
                        redisTemplate.delete(idleKey);
                    }
                } else {
                    // Truck is moving, reset the idle counter
                    redisTemplate.delete("wtms:idle:" + vehicleNo);
                }
            }
        }
    }

    private void sendSystemAlertEvent(String tehsilId, String vehicleNo, String eventType, String title, String message) {
        Map<String, Object> alertEvent = new HashMap<>();
        alertEvent.put("eventType", eventType);
        alertEvent.put("targetTehsilId", tehsilId);
        alertEvent.put("vehicleNo", vehicleNo);
        alertEvent.put("title", title);
        alertEvent.put("message", message);

        kafkaTemplate.send("system-alerts-topic", alertEvent);
    }
}