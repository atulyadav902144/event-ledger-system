package com.ledger.gateway.entity;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Map;

@Data
@Entity
@Table(name = "events", indexes = {@Index(name = "idx_event_id", columnList = "eventId", unique = true)})
public class EventRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String eventId; 
    
    private String accountId;
    private String type;
    private BigDecimal amount;
    private String currency;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant eventTimestamp;

    @Convert(converter = MetadataConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata;
}