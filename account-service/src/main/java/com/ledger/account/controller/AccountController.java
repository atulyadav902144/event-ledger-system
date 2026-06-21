package com.ledger.account.controller;

import com.ledger.account.dto.TransactionRequest;
import com.ledger.account.dto.TransactionResponse;
import com.ledger.account.entity.Account;
import com.ledger.account.entity.Transaction;
import com.ledger.account.service.AccountManagerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountManagerService accountService;
    private final Logger logger = LoggerFactory.getLogger(AccountController.class);
    private final AtomicLong requestCount = new AtomicLong(0);


    public AccountController(AccountManagerService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(@PathVariable String accountId, @Valid @RequestBody TransactionRequest payload,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        if (traceId != null) MDC.put("traceId", traceId);
        try {
            logger.info("Applying transaction to account={} type={} amount={} traceId={}", accountId, payload.getType(), payload.getAmount(), traceId);
            Transaction transaction = accountService.applyTransaction(accountId, payload.getType(), payload.getAmount());
            TransactionResponse response = new TransactionResponse(
                    transaction.getId(),
                    transaction.getType(),
                    transaction.getAmount(),
                    transaction.getTransactionTimestamp()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<Map<String, Object>> getAccountBalance(@PathVariable String accountId,
                                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        if (traceId != null) MDC.put("traceId", traceId);
        try {
            Account account = accountService.getAccountDetails(accountId);
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", account.getAccountId());
            response.put("balance", account.getBalance());
            response.put("currency", "USD");
            return ResponseEntity.ok(response);
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<Map<String, Object>> getAccountDetails(@PathVariable String accountId,
                                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        if (traceId != null) MDC.put("traceId", traceId);
        try {
            Account account = accountService.getAccountDetails(accountId);

            Map<String, Object> response = new HashMap<>();
            response.put("accountId", account.getAccountId());
            response.put("balance", account.getBalance());
            response.put("currency", "USD");
            response.put("transactions", account.getTransactions().stream().map(t -> {
                Map<String, Object> transactionMap = new HashMap<>();
                transactionMap.put("type", t.getType());
                transactionMap.put("amount", t.getAmount());
                transactionMap.put("timestamp", t.getTransactionTimestamp());
                return transactionMap;
            }).collect(Collectors.toList()));

            return ResponseEntity.ok(response);
        } finally {
            MDC.clear();
        }
    }



    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Account-Service");
        // Simple custom metric: total requests handled by this instance
        health.put("totalRequests", requestCount.incrementAndGet());
        return ResponseEntity.ok(health);
    }
}