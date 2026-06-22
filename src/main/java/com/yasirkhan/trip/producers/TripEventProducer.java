package com.yasirkhan.trip.producers;

import com.yasirkhan.trip.models.dtos.TripResponseEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TripEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TripEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // Send Vehicle Created/Updated Response Event
    public void sendTripResponseEvent(TripResponseEventDto eventDto) {
        kafkaTemplate.send("trip-response-topic", eventDto).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("SUCCESS: Trip Response {} event sent for Trip ID: {} (Partition: {}, Offset: {})",
                        eventDto.getType(),
                        eventDto.getTripData().getTripId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("FAILED to send Trip Response {} event for Trip ID: {}. Reason: {}",
                        eventDto.getType(),
                        eventDto.getTripData().getTripId(),
                        ex.getMessage());
            }
        });
    }
}
