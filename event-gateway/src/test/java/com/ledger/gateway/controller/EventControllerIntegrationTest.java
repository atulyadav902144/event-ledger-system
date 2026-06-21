package com.ledger.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.gateway.client.AccountServiceClient;
import com.ledger.gateway.entity.EventRecord;
import com.ledger.gateway.entity.EventRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Event Gateway - tests core functionality:
 * - Idempotency (duplicate event submission)
 * - Validation (invalid amount, invalid type)
 * - Event listing and ordering
 */
@SpringBootTest
@AutoConfigureMockMvc
public class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRecordRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountServiceClient accountServiceClient;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    // ========== IDEMPOTENCY TESTS ==========

    @Test
    void testIdempotency_DuplicateEventReturnsSameStatus() throws Exception {
        // Create first event
        Map<String, Object> event = createEventPayload("evt-001", "acct-123", "CREDIT", "150.00");

        // First submission
        MvcResult result1 = mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")))
                .andExpect(jsonPath("$.eventId", equalTo("evt-001")))
                .andExpect(jsonPath("$.traceId").exists())
                .andReturn();

        // Second submission with same eventId
        MvcResult result2 = mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())  // 200 OK, not 201
                .andExpect(jsonPath("$.status", equalTo("IGNORED")))
                .andExpect(jsonPath("$.eventId", equalTo("evt-001")))
                .andExpect(jsonPath("$.message", containsString("already processed")))
                .andReturn();

        // Database should have only 1 event, not 2
        assertEquals(1, eventRepository.count());
    }

    @Test
    void testIdempotency_EventCountStaysSame() throws Exception {
        Map<String, Object> event = createEventPayload("evt-dup-1", "acct-456", "DEBIT", "50.00");

        // Submit 3 times with same eventId
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(event)))
                    .andReturn();
        }

        // Only 1 event should be in database
        assertEquals(1, eventRepository.count());
    }

    // ========== VALIDATION TESTS ==========

    @Test
    void testValidation_RejectNegativeAmount() throws Exception {
        Map<String, Object> event = createEventPayload("evt-002", "acct-123", "CREDIT", "-100.00");

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Amount must be greater than 0")));

        assertEquals(0, eventRepository.count());
    }

    @Test
    void testValidation_RejectZeroAmount() throws Exception {
        Map<String, Object> event = createEventPayload("evt-003", "acct-123", "CREDIT", "0");

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Amount must be greater than 0")));

        assertEquals(0, eventRepository.count());
    }

    @Test
    void testValidation_RejectInvalidTransactionType() throws Exception {
        Map<String, Object> event = createEventPayload("evt-004", "acct-123", "INVALID", "100.00");

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Type must be CREDIT or DEBIT")));

        assertEquals(0, eventRepository.count());
    }

    @Test
    void testValidation_RejectNullAmount() throws Exception {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "evt-005");
        event.put("accountId", "acct-123");
        event.put("type", "CREDIT");
        event.put("amount", null);
        event.put("currency", "USD");
        event.put("eventTimestamp", OffsetDateTime.now().toString());

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isBadRequest());

        assertEquals(0, eventRepository.count());
    }

    // ========== EVENT LISTING & ORDERING TESTS ==========

    @Test
    void testEventListing_EventsOrderedByTimestamp() throws Exception {
        // Submit 3 events out of order by timestamp
        OffsetDateTime ts1 = OffsetDateTime.now();
        OffsetDateTime ts3 = OffsetDateTime.now().plusHours(2);
        OffsetDateTime ts2 = OffsetDateTime.now().plusHours(1);

        EventRecord event1 = new EventRecord();
        event1.setEventId("evt-1");
        event1.setAccountId("acct-111");
        event1.setType("CREDIT");
        event1.setAmount(BigDecimal.valueOf(100));
        event1.setCurrency("USD");
        event1.setEventTimestamp(ts1);
        eventRepository.save(event1);

        EventRecord event3 = new EventRecord();
        event3.setEventId("evt-3");
        event3.setAccountId("acct-111");
        event3.setType("CREDIT");
        event3.setAmount(BigDecimal.valueOf(300));
        event3.setCurrency("USD");
        event3.setEventTimestamp(ts3);
        eventRepository.save(event3);

        EventRecord event2 = new EventRecord();
        event2.setEventId("evt-2");
        event2.setAccountId("acct-111");
        event2.setType("DEBIT");
        event2.setAmount(BigDecimal.valueOf(50));
        event2.setCurrency("USD");
        event2.setEventTimestamp(ts2);
        eventRepository.save(event2);

        // Query events for account
        mockMvc.perform(get("/events?account=acct-111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].eventId", equalTo("evt-1")))  // First in time
                .andExpect(jsonPath("$[1].eventId", equalTo("evt-2")))  // Second in time
                .andExpect(jsonPath("$[2].eventId", equalTo("evt-3"))); // Third in time
    }

    @Test
    void testEventRetrieval_GetEventById() throws Exception {
        EventRecord event = new EventRecord();
        event.setEventId("evt-retrieve");
        event.setAccountId("acct-999");
        event.setType("CREDIT");
        event.setAmount(BigDecimal.valueOf(250));
        event.setCurrency("USD");
        event.setEventTimestamp(OffsetDateTime.now());
        eventRepository.save(event);

        mockMvc.perform(get("/events/evt-retrieve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", equalTo("evt-retrieve")))
                .andExpect(jsonPath("$.accountId", equalTo("acct-999")))
                .andExpect(jsonPath("$.type", equalTo("CREDIT")))
                .andExpect(jsonPath("$.amount", equalTo(250.0)));
    }

    @Test
    void testEventRetrieval_NotFoundReturns404() throws Exception {
        mockMvc.perform(get("/events/evt-nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ========== TRACE ID TESTS ==========

    @Test
    void testTraceId_ResponseIncludesTraceId() throws Exception {
        Map<String, Object> event = createEventPayload("evt-trace-1", "acct-123", "CREDIT", "100.00");

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.traceId", notNullValue()))
                .andExpect(jsonPath("$.traceId", matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                ))); // UUID format
    }

    @Test
    void testTraceId_DuplicateResponseIncludesTraceId() throws Exception {
        Map<String, Object> event = createEventPayload("evt-trace-2", "acct-123", "CREDIT", "100.00");

        // First submission
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andReturn();

        // Duplicate submission should also have traceId in response
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").exists());
    }

    // ========== HELPER METHODS ==========

    private Map<String, Object> createEventPayload(String eventId, String accountId, String type, String amount) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", eventId);
        event.put("accountId", accountId);
        event.put("type", type);
        event.put("amount", new BigDecimal(amount));
        event.put("currency", "USD");
        event.put("eventTimestamp", OffsetDateTime.now().toString());
        return event;
    }
}

