package com.ledger.gateway.controller;

import com.ledger.gateway.entity.EventRecord;
import com.ledger.gateway.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.MDC;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<?> submitEvent(@RequestBody EventRecord event) {
        requestCount.incrementAndGet();
        
        try {
            // Get the traceId populated by the MdcFilter
            String traceId = MDC.get("traceId");
            Map<String, Object> result = eventService.processEvent(event, traceId);
            
            if ("IGNORED".equals(result.get("status"))) {
                return ResponseEntity.ok().body(result);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
            
        } catch (IllegalArgumentException e) {
             errorCount.incrementAndGet();
             throw e; // Handled by GlobalExceptionHandler -> 400 Bad Request
        } catch (Exception e) {
             errorCount.incrementAndGet();
             throw e; // Handled by GlobalExceptionHandler -> 503 or 500
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventRecord> getEventById(@PathVariable String id) {
        requestCount.incrementAndGet();
        return eventService.getEventById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<EventRecord>> getAccountEvents(@RequestParam String accountId) {
        requestCount.incrementAndGet();
        List<EventRecord> events = eventService.getEventsByAccountId(accountId);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Event-Gateway");
        
        try {
            health.put("totalEvents", eventService.getEventCount());
        } catch (Exception e) {
            // In case repository not available
            health.put("totalEvents", null);
        }

        long totalRequests = requestCount.get();
        long totalErrors = errorCount.get();
        double errorRate = totalRequests > 0 ? (double) totalErrors / totalRequests * 100 : 0.0;

        health.put("totalRequests", totalRequests);
        health.put("totalErrors", totalErrors);
        health.put("errorRate", String.format("%.2f%%", errorRate));

        return ResponseEntity.ok(health);
    }
}