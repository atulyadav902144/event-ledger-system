package com.ledger.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.gateway.client.AccountServiceClient;
import com.ledger.gateway.entity.EventRecord;
import com.ledger.gateway.repository.EventRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    // This formatter EXACTLY matches the @JsonFormat annotation in the EventRecord entity
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    // ========== IDEMPOTENCY TESTS ==========

    @Test
    void testIdempotency_DuplicateEventReturnsSameStatus() throws Exception {
        Map<String, Object> event = createEventPayload("evt-001", "acct-123", "CREDIT", "150.00");

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")));

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("IGNORED")));

        assertEquals(1, eventRepository.count());
    }

    // ========== VALIDATION TESTS ==========

    @Test
    void testValidation_RejectInvalidTransactionType() throws Exception {
        Map<String, Object> event = createEventPayload("evt-004", "acct-123", "INVALID", "100.00");

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Type must be CREDIT or DEBIT")));
    }

    // ========== EVENT LISTING & ORDERING TESTS ==========

    @Test
    void testEventListing_EventsOrderedByTimestamp() throws Exception {
        Instant ts1 = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant ts3 = ts1.plus(2, ChronoUnit.HOURS);
        Instant ts2 = ts1.plus(1, ChronoUnit.HOURS);

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

        mockMvc.perform(get("/events?accountId=acct-111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].eventId", equalTo("evt-1")))
                .andExpect(jsonPath("$[1].eventId", equalTo("evt-2")))
                .andExpect(jsonPath("$[2].eventId", equalTo("evt-3")));
    }

    // ========== HELPER METHODS ==========

    private Map<String, Object> createEventPayload(String eventId, String accountId, String type, String amount) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", eventId);
        event.put("accountId", accountId);
        event.put("type", type);
        event.put("amount", new BigDecimal(amount));
        event.put("currency", "USD");
        event.put("eventTimestamp", FORMATTER.format(Instant.now()));
        return event;
    }
}