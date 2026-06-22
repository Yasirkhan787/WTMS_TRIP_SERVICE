package com.yasirkhan.trip.consumers;

import com.yasirkhan.trip.models.dtos.VehicleResponseEventDto;
import com.yasirkhan.trip.models.enums.EventStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class VehicleEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    public VehicleEventConsumer(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(
            topics = "vehicle-response-topic",
            groupId = "trip-group",
            containerFactory = "listenerContainerFactory"
    )
    public void handleVehicleResponse(VehicleResponseEventDto event) {

        if (EventStatus.SUCCESS.equals(event.getEventTypeStatus())) {
            String vehicleNo = event.getVehicleNo();

            Map<String, Object> map = new HashMap<>();
            map.put("tehsilId", event.getTehsilId());
            map.put("status", event.getStatus());

            // Save Hash to Redis (Using vehicleNo as the unique identifier)
            String redisKey = "wtms:vehicle:" + vehicleNo;
            redisTemplate.opsForHash().putAll(redisKey, map);
        }
    }
}