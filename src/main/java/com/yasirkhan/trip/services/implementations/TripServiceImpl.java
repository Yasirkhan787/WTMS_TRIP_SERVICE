package com.yasirkhan.trip.services.implementations;

import com.yasirkhan.trip.integrations.asktrack.AskTrackerClient;
import com.yasirkhan.trip.integrations.asktrack.AskTrackerTripsResponseDto;
import com.yasirkhan.trip.integrations.asktrack.TrackerResponseDto;
import com.yasirkhan.trip.models.dtos.TripResponseEventDto;
import com.yasirkhan.trip.models.entities.TripWeight;
import com.yasirkhan.trip.models.enums.EventStatus;
import com.yasirkhan.trip.models.enums.EventType;
import com.yasirkhan.trip.models.enums.Status;
import com.yasirkhan.trip.repositories.TripWeightRepository;
import com.yasirkhan.trip.responses.TripResponse;
import com.yasirkhan.trip.responses.TripSummaryResponse;
import com.yasirkhan.trip.services.TripService;
import com.yasirkhan.trip.utils.ResponseConversion;
import com.yasirkhan.trip.utils.SpatialUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class TripServiceImpl implements TripService {

    private final AskTrackerClient askTrackClient;
    private final TripWeightRepository tripWeightRepository;
    // REMOVED: ScheduleClientService
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public TripServiceImpl(AskTrackerClient askTrackClient,
                           TripWeightRepository tripWeightRepository,
                           RedisTemplate<String, Object> redisTemplate,
                           ApplicationEventPublisher eventPublisher) {
        this.askTrackClient = askTrackClient;
        this.tripWeightRepository = tripWeightRepository;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void syncDailyTrips(String wmc) {
        LocalDate today = LocalDate.of(2026, 6, 6); // Switch to LocalDate.now() for prod

        // Pre-fetch valid vehicles from local cache
        Set<String> validVehicles = new HashSet<>();
        try {
            Set<String> keys = redisTemplate.keys("wtms:vehicle:*");
            if (keys != null) {
                for (String key : keys) {
                    validVehicles.add(key.replace("wtms:vehicle:", ""));
                }
            }
            log.info("Loaded {} registered WTMS vehicles from local Redis cache.", validVehicles.size());
        } catch (Exception e) {
            log.error("Failed to read from Redis. Aborting sync.", e);
            return;
        }

        // Fetch daily trips using typed DTOs
        List<AskTrackerTripsResponseDto.AskTrackSlipDto> dailySlips = askTrackClient.fetchDailyTrips(wmc, today);

        for (AskTrackerTripsResponseDto.AskTrackSlipDto slip : dailySlips) {

            // Safe extraction of slipId
            String slipId = slip.getSlipID_Load() != null ? slip.getSlipID_Load() : slip.getFWB_SlipID();
            if (slipId == null) continue;

            String vehicleNo = slip.getVehicle_Name();

            // Instant Redis Filter
            if (vehicleNo == null || !validVehicles.contains(vehicleNo)) continue;

            // Check if trip is finished
            if (slip.getEmptyTime() == null || slip.getEmptyTime().trim().isEmpty() || slip.getEmptyWeight() == null) {
                log.info("Slip {} is still in progress (truck hasn't weighed out). Skipping...", slipId);
                continue;
            }

            // Idempotency Check
            if (tripWeightRepository.existsBySlipId(slipId)) continue;

            // Extract core data cleanly via Getters
            double loadWeight = slip.getLoadWeight();
            double emptyWeight = slip.getEmptyWeight();
            double netWeight = slip.getNetWeight();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
            LocalDateTime loadTime = LocalDateTime.parse(slip.getLoadDate() + " " + slip.getLoadTime(), formatter);
            LocalDateTime emptyTime = LocalDateTime.parse(slip.getEmptyDate() + " " + slip.getEmptyTime(), formatter);

            LocalDate tripDate = loadTime.toLocalDate();
            LocalTime tripTime = loadTime.toLocalTime();

            // NEW: Resolve Schedule from Redis instead of Feign Client
            UUID activeScheduleId = resolveActiveScheduleIdFromRedis(vehicleNo, tripDate, tripTime);

            if (activeScheduleId == null) {
                log.warn("Orphaned Trip: Slip {} found for vehicle {} at {}, but no active schedule matched in Redis.", slipId, vehicleNo, tripTime);
                continue;
            }

            // Extract Actual Distance & Construct LINESTRING
            String trackerId = "00033650";
            Object cachedTrackerId = redisTemplate.opsForHash().get("wtms:vehicle:" + vehicleNo, "trackingId"); // Corrected key field

            if (cachedTrackerId != null) {
                try {
                    trackerId = cachedTrackerId.toString();
                } catch (NumberFormatException e) {
                    log.warn("Invalid trackingId format in Redis for vehicle {}, falling back to 00033650", vehicleNo);
                }
            }

            double actualDistanceKm = 0.0;
            LineString lineString = null;

            TrackerResponseDto trackerResponse = askTrackClient.fetchTrackerData(trackerId, loadTime, emptyTime);

            if (trackerResponse != null && trackerResponse.getData() != null) {
                actualDistanceKm = trackerResponse.getData().getTotalKilometers();
                List<TrackerResponseDto.TrackPointDto> trackList = trackerResponse.getData().getTrack();

                if (trackList != null && !trackList.isEmpty()) {
                    List<Coordinate> jtsCoordinates = trackList.stream()
                            .map(point -> new Coordinate(point.getLng(), point.getLat()))
                            .toList();
                    lineString = SpatialUtils.toLineString(jtsCoordinates);
                }
            }

            double mileage = 4.5;
            Object cachedMileage = redisTemplate.opsForHash().get("wtms:vehicle:" + vehicleNo, "mileage");

            if (cachedMileage != null) {
                try {
                    mileage = Double.parseDouble(cachedMileage.toString());
                } catch (NumberFormatException e) {
                    log.warn("Invalid mileage format in Redis for vehicle {}, falling back to 4.5", vehicleNo);
                }
            }

            double fuelConsumed = actualDistanceKm / mileage;

            TripWeight tripRecord = new TripWeight();
            tripRecord.setScheduleId(activeScheduleId);
            tripRecord.setSlipId(slipId);
            tripRecord.setVehicleNo(vehicleNo);
            tripRecord.setLoadTime(loadTime);
            tripRecord.setLoadWeight(loadWeight);
            tripRecord.setEmptyTime(emptyTime);
            tripRecord.setEmptyWeight(emptyWeight);
            tripRecord.setNetWeight(netWeight);
            tripRecord.setActualDistance(actualDistanceKm);
            tripRecord.setFuelConsumed(Math.round(fuelConsumed * 100.0) / 100.0);
            tripRecord.setPath(lineString);
            tripRecord.setStatus(Status.COMPLETED);

            TripWeight savedTrip = tripWeightRepository.save(tripRecord);
            log.info("Successfully automated trip for slip: {}", slipId);

            TripResponse response = ResponseConversion.toTripResponse(savedTrip);
            publishTripEvent(EventType.CREATE, EventStatus.SUCCESS, response);
        }
    }

    @Override
    public TripSummaryResponse getTripsByDate(LocalDate targetDate) {
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();

        List<TripWeight> trips = tripWeightRepository.findByLoadTimeBetween(start, end);
        Double totalKg = tripWeightRepository.calculateTotalTonnageForDate(start, end);
        Double totalTonnage = totalKg != null ? totalKg / 1000.0 : 0.0;

        return ResponseConversion.toTripSummaryResponse(trips, totalTonnage);
    }

    // ==========================================
    //            PRIVATE HELPERS
    // ==========================================

    private void publishTripEvent(EventType type, EventStatus status, TripResponse data) {
        TripResponseEventDto eventDto = TripResponseEventDto.builder()
                .type(type)
                .eventTypeStatus(status)
                .tripData(data)
                .build();
        eventPublisher.publishEvent(eventDto);
    }

    /**
     * Resolves the active Schedule ID directly from the Redis Materialized View.
     * Incorporates the 60-minute buffer logic for hardware syncing and overnight shifts.
     */
    private UUID resolveActiveScheduleIdFromRedis(String vehicleNo, LocalDate tripDate, LocalTime tripTime) {
        String dateString = tripDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dailyIndexKey = "wtms:schedules:daily:" + dateString;

        // 1. Fetch all Schedule IDs assigned for the target date
        Set<Object> activeScheduleIds = redisTemplate.opsForSet().members(dailyIndexKey);

        if (activeScheduleIds == null || activeScheduleIds.isEmpty()) {
            return null; // No schedules found for this date
        }

        for (Object idObj : activeScheduleIds) {
            String scheduleId = idObj.toString();
            String scheduleKey = "wtms:schedule:" + scheduleId;

            // 2. Fetch the Schedule details from Redis
            Map<Object, Object> scheduleData = redisTemplate.opsForHash().entries(scheduleKey);
            if (scheduleData.isEmpty()) continue;

            // 3. Match Vehicle
            String schedVehicle = (String) scheduleData.get("vehicleNo");
            if (!vehicleNo.equalsIgnoreCase(schedVehicle)) continue;

            // 4. Extract Times and Apply 60-Minute Grace Buffer
            LocalTime startTime = LocalTime.parse((String) scheduleData.get("startTime"));
            LocalTime endTime = LocalTime.parse((String) scheduleData.get("endTime"));

            LocalTime bufferedStart = startTime.minusMinutes(60);
            LocalTime bufferedEnd = endTime.plusMinutes(60);

            boolean isOvernightShift = bufferedStart.isAfter(bufferedEnd);

            boolean isMatch = false;
            if (isOvernightShift) {
                isMatch = tripTime.isAfter(bufferedStart) || tripTime.isBefore(bufferedEnd);
            } else {
                isMatch = tripTime.isAfter(bufferedStart) && tripTime.isBefore(bufferedEnd);
            }

            if (isMatch) {
                return UUID.fromString(scheduleId);
            }
        }
        return null;
    }
}