package com.ledger.gateway.service;

import com.ledger.gateway.client.AccountServiceClient;
import com.ledger.gateway.entity.EventRecord;
import com.ledger.gateway.entity.EventRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EventService {

    private final EventRecordRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final MeterRegistry meterRegistry;
    private final Logger logger = LoggerFactory.getLogger(EventService.class);

    public EventService(EventRecordRepository eventRepository, AccountServiceClient accountServiceClient, MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Map<String, Object> processEvent(EventRecord event, String traceId) {
        // 1. Validation
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            meterRegistry.counter("events_validation_failed_total", "reason", "invalid_amount").increment();
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (!"CREDIT".equals(event.getType()) && !"DEBIT".equals(event.getType())) {
            meterRegistry.counter("events_validation_failed_total", "reason", "invalid_type").increment();
            throw new IllegalArgumentException("Type must be CREDIT or DEBIT");
        }

        // 2. Idempotency Check via Database Constraint
        try {
            eventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            // Event ID already exists. Return 200 OK without re-processing (Idempotent)
            meterRegistry.counter("events_processed_duplicate_total").increment();
            Map<String, Object> ignored = new HashMap<>();
            ignored.put("status", "IGNORED");
            ignored.put("message", "Event already processed");
            ignored.put("eventId", event.getEventId());
            ignored.put("traceId", traceId);
            return ignored;
        }

        // 3. Forward to Account Service (Protected by Circuit Breaker)
        try {
            logger.info("Forwarding event {} to Account Service. traceId={}", event.getEventId(), traceId);
            accountServiceClient.postTransaction(event, traceId);
            meterRegistry.counter("events_processed_total", "status", "success").increment();

            Map<String, Object> accepted = new HashMap<>();
            accepted.put("status", "ACCEPTED");
            accepted.put("eventId", event.getEventId());
            accepted.put("traceId", traceId);
            return accepted;
        } catch (Exception e) {
             meterRegistry.counter("events_processed_total", "status", "failed").increment();
             logger.error("Failed to forward event {} to Account Service traceId={}", event.getEventId(), traceId, e);
             throw e; // Rethrow to let global exception handler map to 503 or 500
        }
    }

    public Optional<EventRecord> getEventById(String eventId) {
        return eventRepository.findByEventId(eventId);
    }

    public List<EventRecord> getEventsByAccountId(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
    }

    public long getEventCount() {
        return eventRepository.count();
    }
}