package com.yasirkhan.trip.consumers;

import com.yasirkhan.trip.models.dtos.RouteResponseEventDto;
import com.yasirkhan.trip.models.enums.EventStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class RouteEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    public RouteEventConsumer(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(
            topics = "route-response-topic",
            groupId = "trip-group",
            containerFactory = "listenerContainerFactory"
    )
    public void handleRouteResponse(RouteResponseEventDto event) {

        if (EventStatus.SUCCESS.equals(event.getEventTypeStatus())) {

            RouteResponseEventDto.RouteResponse routeData = event.getRouteData();

            if (routeData == null || routeData.getRouteId() == null) {
                return;
            }

            UUID routeId = routeData.getRouteId();
            Map<String, Object> map = new HashMap<>();
            map.put("routeName", routeData.getRouteName());
            map.put("path", routeData.getPath());
            map.put("tehsilId", routeData.getTehsilId() != null ? routeData.getTehsilId().toString() : null);
            map.put("tehsilName", routeData.getTehsilName());
            map.put("sourceYardId", routeData.getSourceYardId() != null ? routeData.getSourceYardId().toString() : null);
            map.put("sourceYardName", routeData.getSourceYardName());
            map.put("sourceYardType", routeData.getSourceYardType());
            map.put("destinationYardId", routeData.getDestinationYardId() != null ? routeData.getDestinationYardId().toString() : null);
            map.put("destinationYardName", routeData.getDestinationYardName());
            map.put("destinationYardType", routeData.getDestinationYardType());
            map.put("status", routeData.getStatus());

            if (event.getYardData() != null) {
                RouteResponseEventDto.YardResponse yard = event.getYardData();
                map.put("destinationYardBoundaryType", yard.getBoundaryType());
                map.put("destinationYardRadiusMeters", yard.getRadiusMeters() != null ? String.valueOf(yard.getRadiusMeters()) : null);
                map.put("destinationYardPolygonPath", yard.getPolygonPath() != null ? yard.getPolygonPath() : null);

                if (yard.getCenterCoords() != null) {
                    map.put("destinationYardCenterLat", String.valueOf(yard.getCenterCoords().getLat()));
                    map.put("destinationYardCenterLng", String.valueOf(yard.getCenterCoords().getLng()));
                }
            }

            String redisKey = "wtms:route:" + routeId;
            redisTemplate.opsForHash().putAll(redisKey, map);
        }
    }
}