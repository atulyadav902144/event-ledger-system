package com.ledger.gateway.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String status;
    private String message;
    private Instant timestamp;
}