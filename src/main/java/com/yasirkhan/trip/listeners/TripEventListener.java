package com.yasirkhan.trip.listeners;


import com.yasirkhan.trip.models.dtos.TripResponseEventDto;
import com.yasirkhan.trip.producers.TripEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class TripEventListener {

    private final TripEventProducer producer;

    public TripEventListener(TripEventProducer producer) {
        this.producer = producer;
    }

    // This ensures Kafka message is sent ONLY AFTER DB commit is successful
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTripResponseEvent(TripResponseEventDto eventDto) {
        try {
            producer.sendTripResponseEvent(eventDto);
            log.info("Successfully published Kafka event for Trip ID: {}", eventDto.getTripData().getTripId());
        } catch (Exception e) {
            // Note: Since DB is already committed, if Kafka fails here,
            // you might want to log it deeply or implement a retry mechanism.
            log.error("Failed to publish Kafka event for Trip ID: {}", eventDto.getTripData().getTripId(), e);
        }
    }
}