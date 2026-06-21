package com.ledger.gateway.client;

import com.ledger.gateway.entity.EventRecord;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AccountServiceClient {
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${account.service.url:http://localhost:8081}")
    private String accountServiceUrl;
    
    private final Logger logger = LoggerFactory.getLogger(AccountServiceClient.class);

    // Post a transaction to Account Service, propagating a trace id header
    @CircuitBreaker(name = "accountService", fallbackMethod = "fallbackPostEvent")
    public void postTransaction(EventRecord event, String traceId) {
        String url = accountServiceUrl + "/accounts/{accountId}/transactions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (traceId != null) {
            headers.add("X-Trace-Id", traceId);
        }

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("type", event.getType());
        body.put("amount", event.getAmount());
        body.put("currency", event.getCurrency());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        logger.debug("Forwarding transaction to Account Service for account={} traceId={}", event.getAccountId(), traceId);
        restTemplate.postForObject(url, request, Void.class, event.getAccountId());
    }

    // Fallback signature must include original parameters + Throwable
    public void fallbackPostEvent(EventRecord event, String traceId, Throwable t) {
        logger.warn("Account Service unavailable for event={} traceId={}", event != null ? event.getEventId() : null, traceId);
        throw new RuntimeException("Account Service is currently unavailable. Event not processed.", t);
    }
}