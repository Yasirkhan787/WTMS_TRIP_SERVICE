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
            map.put("status", routeData.getStatus());
            map.put("sourceYardId", routeData.getSourceYardId() != null ? routeData.getSourceYardId().toString() : null);
            map.put("sourceYardName", routeData.getSourceYardName());
            map.put("sourceYardType", routeData.getSourceYardType());
            map.put("destinationYardId", routeData.getDestinationYardId() != null ? routeData.getDestinationYardId().toString() : null);
            map.put("destinationYardName", routeData.getDestinationYardName());
            map.put("destinationYardType", routeData.getDestinationYardType());

            if (event.getSourceYardData() != null) {
                RouteResponseEventDto.YardResponse srcYard = event.getSourceYardData();
                map.put("sourceYardBoundaryType", srcYard.getBoundaryType());
                map.put("sourceYardRadiusMeters", srcYard.getRadiusMeters() != null ? String.valueOf(srcYard.getRadiusMeters()) : null);

                if (srcYard.getCenterCoords() != null) {
                    map.put("sourceYardCenterLat", String.valueOf(srcYard.getCenterCoords().getLat()));
                    map.put("sourceYardCenterLng", String.valueOf(srcYard.getCenterCoords().getLng()));
                }
            }

            if (event.getDestinationYardData() != null) {
                RouteResponseEventDto.YardResponse destYard = event.getDestinationYardData();
                map.put("destinationYardBoundaryType", destYard.getBoundaryType());
                map.put("destinationYardRadiusMeters", destYard.getRadiusMeters() != null ? String.valueOf(destYard.getRadiusMeters()) : null);
                map.put("destinationYardPolygonPath", destYard.getPolygonPath() != null ? destYard.getPolygonPath() : null);

                if (destYard.getCenterCoords() != null) {
                    map.put("destinationYardCenterLat", String.valueOf(destYard.getCenterCoords().getLat()));
                    map.put("destinationYardCenterLng", String.valueOf(destYard.getCenterCoords().getLng()));
                }
            }

            String redisKey = "wtms:route:" + routeId;
            redisTemplate.opsForHash().putAll(redisKey, map);
        }
    }
}