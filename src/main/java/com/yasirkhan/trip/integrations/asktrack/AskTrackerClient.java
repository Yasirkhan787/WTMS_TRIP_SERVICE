package com.yasirkhan.trip.integrations.asktrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class AskTrackerClient {

    private final String baseUrl;
    private final String apiKey;
    private final boolean isMockEnabled;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;


    public AskTrackerClient(
            @Value("${asktrack.base-url:https://askteckbypass.onrender.com}") String baseUrl,
            @Value("${asktrack.api-key:my-super-secret-key-123}") String apiKey,
            @Value("${asktrack.mock.enabled:false}") boolean isMockEnabled,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {

        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.isMockEnabled = isMockEnabled;
        this.objectMapper = objectMapper;

        // Build the RestClient ONCE with the default configuration
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .build();
    }

    // Now returns the strictly-typed AskTrackSlipDto list
    public List<AskTrackerTripsResponseDto.AskTrackSlipDto> fetchDailyTrips(String wmc, LocalDate date) {

        if (isMockEnabled) {
            log.info("MOCK MODE ENABLED: Reading trips from local JSON file.");
            try (InputStream inputStream = new ClassPathResource("mock-asktrack-trips.json").getInputStream()) {
                AskTrackerTripsResponseDto mockResponse = objectMapper.readValue(inputStream, AskTrackerTripsResponseDto.class);
                return mockResponse.getData() != null ? mockResponse.getData() : Collections.emptyList();
            } catch (Exception e) {
                log.error("Failed to read mock data: {}", e.getMessage());
                return Collections.emptyList();
            }
        }

        String formattedDate = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        // Only append the URI path, as baseUrl is already configured
        String uri = String.format("/api/trip-data?wmc=%s&from=%s&to=%s", wmc, formattedDate, formattedDate);

        try {
            AskTrackerTripsResponseDto response =
                    restClient
                            .get()
                            .uri(uri)
                            .retrieve()
                            // Handle 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found
                            .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.error("AskTrack API Client Error ({}): {}", res.getStatusCode(), new String(res.getBody().readAllBytes()));
                        throw new AskTrackIntegrationException("Client error fetching daily trips");
                    })
                    // Handle 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("AskTrack API Server Error ({}): {}", res.getStatusCode(), new String(res.getBody().readAllBytes()));
                        throw new AskTrackIntegrationException("Server error fetching daily trips");
                    })
                    // Automatically deserialize to your exact DTO
                    .body(AskTrackerTripsResponseDto.class);

            if (response != null && "success".equalsIgnoreCase(response.getStatus()) && response.getData() != null) {
                return response.getData();
            }
            log.warn("AskTrack API returned empty or unsuccessful response.");
            return Collections.emptyList();

        } catch (AskTrackIntegrationException e) {
            // Business decision: return an empty list so the cron job doesn't crash
            return Collections.emptyList();
        } catch (RestClientException e) {
            // Handles network drops, connect timeouts, and read timeouts
            log.error("Network or timeout error while connecting to AskTrack API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public TrackerResponseDto fetchTrackerData(String trackerId, LocalDateTime loadTime, LocalDateTime emptyTime) {
        if (isMockEnabled) {
            log.info("MOCK MODE ENABLED: Reading crawler data from local JSON file.");
            try (InputStream inputStream = new ClassPathResource("mock-crawler-data.json").getInputStream()) {
                return objectMapper.readValue(inputStream, TrackerResponseDto.class);
            } catch (Exception e) {
                log.error("Failed to read mock crawler data: {}", e.getMessage());
                return null;
            }
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a");
        String fr = loadTime.format(formatter);
        String tr = emptyTime.format(formatter);

        String uri = String.format("/api/crawler/track/%s?fr=%s&tr=%s&single=yes", trackerId, fr, tr);

        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.error("Crawler Client Error ({}): {}", res.getStatusCode(), new String(res.getBody().readAllBytes()));
                        throw new AskTrackIntegrationException("Client error fetching crawler data");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("Crawler Server Error ({}): {}", res.getStatusCode(), new String(res.getBody().readAllBytes()));
                        throw new AskTrackIntegrationException("Server error fetching crawler data");
                    })
                    .body(TrackerResponseDto.class);

        } catch (AskTrackIntegrationException e) {
            return null;
        } catch (RestClientException e) {
            log.error("Network or timeout error while connecting to Crawler API: {}", e.getMessage());
            return null;
        }
    }
}