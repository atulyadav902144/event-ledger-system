package com.ledger.gateway.controller;

import com.ledger.gateway.dto.EventResponse;
import com.ledger.gateway.entity.EventRecord;
import com.ledger.gateway.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.MDC;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final Counter requestCounter;
    private final Counter errorCounter;

    public EventController(EventService eventService, MeterRegistry meterRegistry) {
        this.eventService = eventService;
        this.requestCounter = Counter.builder("events_requests_total").description("Total incoming requests to /events").register(meterRegistry);
        this.errorCounter = Counter.builder("events_errors_total").description("Total errors handling /events requests").register(meterRegistry);
    }

    @PostMapping
    public ResponseEntity<?> submitEvent(@RequestBody EventRecord event) {
        // increment request counter; errors are counted in the global handler
        requestCounter.increment();

        // Get the traceId populated by the MdcFilter
        String traceId = MDC.get("traceId");
        EventResponse result = eventService.processEvent(event, traceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventRecord> getEventById(@PathVariable String id) {
        requestCounter.increment();
        return eventService.getEventById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<EventRecord>> getAccountEvents(@RequestParam String accountId) {
        requestCounter.increment();
        List<EventRecord> events = eventService.getEventsByAccountId(accountId);
        if (events == null || events.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(events);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "Event-Gateway");
        
        try {
            health.put("totalEvents", eventService.getEventCount());
        } catch (Exception e) {
            // In case repository not available
            health.put("totalEvents", null);
        }

        long totalRequests = (long) requestCounter.count();
        long totalErrors = (long) errorCounter.count();
        double errorRate = totalRequests > 0 ? (double) totalErrors / totalRequests * 100 : 0.0;

        health.put("totalRequests", totalRequests);
        health.put("totalErrors", totalErrors);
        health.put("errorRate", String.format("%.2f%%", errorRate));

        return ResponseEntity.ok(health);
    }
}