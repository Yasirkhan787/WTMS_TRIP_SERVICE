package com.yasirkhan.trip.consumers;

import com.yasirkhan.trip.models.dtos.VehicleResponseEventDto;
import com.yasirkhan.trip.models.enums.EventStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
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
        log.info("Received vehicle event from Kafka for Vehicle No: {} with status: {}", event.getVehicleNo(), event.getEventTypeStatus());

        if (EventStatus.SUCCESS.equals(event.getEventTypeStatus())) {
            String vehicleNo = event.getVehicleNo();

            Map<String, Object> map = new HashMap<>();
            map.put("tehsilId", event.getTehsilId());
            map.put("trackingId", event.getTrackingId());
            map.put("mileage", event.getMileage());
            map.put("status", event.getStatus());

            String redisKey = "wtms:vehicle:" + vehicleNo;
            redisTemplate.opsForHash().putAll(redisKey, map);

            log.info("Successfully synced vehicle {} to Redis cache under key: {}", vehicleNo, redisKey);
        } else {
            log.warn("Skipping vehicle cache sync because event status was not SUCCESS");
        }
    }
}