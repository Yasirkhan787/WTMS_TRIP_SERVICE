package com.yasirkhan.trip.consumers;

import com.yasirkhan.trip.models.dtos.ScheduleResponseEventDto;
import com.yasirkhan.trip.models.enums.EventStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
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
    public void handleScheduleResponse(ScheduleResponseEventDto event) {

        if (EventStatus.SUCCESS.equals(event.getEventTypeStatus())) {

            ScheduleResponseEventDto.ScheduleResponse scheduleData = event.getScheduleData();

            if (scheduleData == null || scheduleData.getScheduleId() == null) {
                return;
            }

            UUID scheduleId = scheduleData.getScheduleId();
            Map<String, Object> map = new HashMap<>();

            map.put("scheduleName", scheduleData.getScheduleName());
            map.put("vehicleNo", scheduleData.getVehicleNo());
            map.put("driverId", scheduleData.getDriverId() != null ? scheduleData.getDriverId().toString() : "");
            map.put("routeId", scheduleData.getRouteId() != null ? scheduleData.getRouteId().toString() : "");
            map.put("scheduleDate", scheduleData.getScheduleDate() != null ? scheduleData.getScheduleDate().toString() : "");
            map.put("templateId", scheduleData.getTemplateId() != null ? scheduleData.getTemplateId().toString() : "");
            map.put("status", scheduleData.getStatus());

            String redisKey = "wtms:schedule:" + scheduleId;
            redisTemplate.opsForHash().putAll(redisKey, map);
        }
    }
}