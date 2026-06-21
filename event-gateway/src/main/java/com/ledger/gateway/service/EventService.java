package com.ledger.gateway.service;

import com.ledger.gateway.client.AccountServiceClient;
import com.ledger.gateway.dto.EventResponse;
import com.ledger.gateway.entity.EventRecord;
import com.ledger.gateway.repository.EventRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
    public EventResponse processEvent(EventRecord event, String traceId) {
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
        // Save and allow DataIntegrityViolationException / UnexpectedRollbackException
        // to bubble up and be handled by the global exception handler (treated as IGNORED).
        eventRepository.save(event);

        // 3. Forward to Account Service (Protected by Circuit Breaker)
        try {
            logger.info("Forwarding event {} to Account Service. traceId={}", event.getEventId(), traceId);
            accountServiceClient.postTransaction(event, traceId);
            meterRegistry.counter("events_processed_total", "status", "success").increment();

            return new EventResponse(event.getEventId(), event.getAccountId(), "ACCEPTED");
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