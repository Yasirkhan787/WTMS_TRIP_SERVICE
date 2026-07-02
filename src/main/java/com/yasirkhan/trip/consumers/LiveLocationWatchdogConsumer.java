package com.yasirkhan.trip.consumers;

import com.yasirkhan.trip.models.dtos.LiveCoordinateDto;
import com.yasirkhan.trip.utils.SpatialUtils;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class LiveLocationWatchdogConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LiveLocationWatchdogConsumer(RedisTemplate<String, Object> redisTemplate, KafkaTemplate<String, Object> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    // Listens to BOTH the real and mock topics
    @KafkaListener(
            topics = {"live-coordinates-topic", "live-coordinates-mock-topic"},
            groupId = "trip-group",
            containerFactory = "listenerContainerFactory"
    )
    public void processLiveCoordinates(LiveCoordinateDto coordinateData) {
        String vehicleNo = coordinateData.getVehicleNo();
        String trackingKey = "wtms:tracking:" + vehicleNo;

        // 1. Check if Trip Service has an active trip for this vehicle
        Map<Object, Object> liveTrip = redisTemplate.opsForHash().entries(trackingKey);
        if (liveTrip == null || liveTrip.isEmpty()) {
            return; // No active trip, ignore telemetry
        }

        String phase = (String) liveTrip.get("currentPhase");
        String routeId = (String) liveTrip.get("routeId");
        String tehsilId = (String) liveTrip.get("tehsilId");

        double currentLat = coordinateData.getLatitude();
        double currentLng = coordinateData.getLongitude();

        // 2. Fetch Route Data (which Trip Service already synced!)
        String routeKey = "wtms:route:" + routeId;
        Map<Object, Object> cachedRoute = redisTemplate.opsForHash().entries(routeKey);
        if (cachedRoute.isEmpty()) return;

        // ====================================================================
        // PHASE A: AT_TCP
        // ====================================================================
        if ("AT_TCP".equalsIgnoreCase(phase)) {
            Object latObj = cachedRoute.get("sourceYardCenterLat");
            Object lngObj = cachedRoute.get("sourceYardCenterLng");
            Object radObj = cachedRoute.get("sourceYardRadiusMeters");

            if (latObj == null || lngObj == null || radObj == null) return;

            double tcpLat = Double.parseDouble(latObj.toString());
            double tcpLng = Double.parseDouble(lngObj.toString());
            double tcpRadius = Double.parseDouble(radObj.toString());

            double distance = SpatialUtils.calculateDistanceInMeters(currentLat, currentLng, tcpLat, tcpLng);
            if (distance > tcpRadius) {
                redisTemplate.opsForHash().put(trackingKey, "currentPhase", "EN_ROUTE");
                log.info("Vehicle {} exited TCP. Phase changed to EN_ROUTE.", vehicleNo);
                sendSystemAlertEvent(tehsilId, vehicleNo, "GEOFENCE", "Trip Started",
                        String.format("Vehicle %s has left the TCP and is en-route.", vehicleNo));
            }
        }
        // ====================================================================
        // PHASE B: EN_ROUTE
        // ====================================================================
        else if ("EN_ROUTE".equalsIgnoreCase(phase)) {

            // 1. Dumpsite Arrival Check
            Object dsLatObj = cachedRoute.get("destinationYardCenterLat");
            Object dsLngObj = cachedRoute.get("destinationYardCenterLng");
            Object dsRadObj = cachedRoute.get("destinationYardRadiusMeters");

            if (dsLatObj != null && dsLngObj != null && dsRadObj != null) {
                double dsLat = Double.parseDouble(dsLatObj.toString());
                double dsLng = Double.parseDouble(dsLngObj.toString());
                double dsRadius = Double.parseDouble(dsRadObj.toString());

                double distanceToDumpsite = SpatialUtils.calculateDistanceInMeters(currentLat, currentLng, dsLat, dsLng);
                if (distanceToDumpsite <= dsRadius) {
                    redisTemplate.opsForHash().put(trackingKey, "currentPhase", "AT_DUMPSITE");
                    log.info("Vehicle {} arrived at Dumpsite.", vehicleNo);
                    sendSystemAlertEvent(tehsilId, vehicleNo, "GEOFENCE", "Arrived at Dumpsite",
                            String.format("Vehicle %s entered the Dumpsite boundary.", vehicleNo));
                    return;
                }
            }

            // 2. Route Deviation Check
            String routePathWkt = (String) cachedRoute.get("path");
            if (routePathWkt != null) {
                LineString assignedPath = SpatialUtils.parseLineString(routePathWkt);
                Point currentPoint = SpatialUtils.createPoint(currentLat, currentLng);

                if (assignedPath != null && currentPoint != null) {
                    boolean isAdherent = assignedPath.isWithinDistance(currentPoint, 0.00045);
                    boolean wasAlreadyOffRoute = Boolean.parseBoolean((String) liveTrip.get("isOffRoute"));

                    if (!isAdherent && !wasAlreadyOffRoute) {
                        int breachCount = Integer.parseInt((String) liveTrip.getOrDefault("offRouteCount", "0")) + 1;
                        redisTemplate.opsForHash().put(trackingKey, "offRouteCount", String.valueOf(breachCount));

                        if (breachCount >= 3) {
                            redisTemplate.opsForHash().put(trackingKey, "isOffRoute", "true");
                            sendSystemAlertEvent(tehsilId, vehicleNo, "ROUTE_DEVIATION", "Route Deviation Detected",
                                    String.format("🚨 Alert: Vehicle %s veered off route!", vehicleNo));
                        }
                    } else if (isAdherent && wasAlreadyOffRoute) {
                        redisTemplate.opsForHash().put(trackingKey, "isOffRoute", "false");
                        redisTemplate.opsForHash().put(trackingKey, "offRouteCount", "0");
                        sendSystemAlertEvent(tehsilId, vehicleNo, "ROUTE_DEVIATION", "Route Corrected",
                                String.format("Notice: Vehicle %s returned to the path.", vehicleNo));
                    }
                }
            }
        }
        // ====================================================================
        // PHASE C: RETURNING_TO_TCP
        // ====================================================================
        else if ("RETURNING_TO_TCP".equalsIgnoreCase(phase)) {
            Object tcpLatObj = cachedRoute.get("sourceYardCenterLat");
            Object tcpLngObj = cachedRoute.get("sourceYardCenterLng");
            Object tcpRadObj = cachedRoute.get("sourceYardRadiusMeters");

            if (tcpLatObj != null && tcpLngObj != null && tcpRadObj != null) {
                double tcpLat = Double.parseDouble(tcpLatObj.toString());
                double tcpLng = Double.parseDouble(tcpLngObj.toString());
                double tcpRadius = Double.parseDouble(tcpRadObj.toString());

                double distanceToTcp = SpatialUtils.calculateDistanceInMeters(currentLat, currentLng, tcpLat, tcpLng);
                if (distanceToTcp <= tcpRadius) {
                    redisTemplate.opsForHash().put(trackingKey, "currentPhase", "AT_TCP");
                    log.info("Vehicle {} arrived back at TCP.", vehicleNo);
                    sendSystemAlertEvent(tehsilId, vehicleNo, "GEOFENCE", "Back at TCP",
                            String.format("Vehicle %s has returned to the TCP.", vehicleNo));
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