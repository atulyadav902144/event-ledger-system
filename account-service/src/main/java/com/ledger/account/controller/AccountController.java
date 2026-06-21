package com.ledger.account.controller;

import com.ledger.account.dto.AccountResponse;
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
import java.util.LinkedHashMap;
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
    public ResponseEntity<TransactionResponse> applyTransaction(@PathVariable String accountId, @Valid @RequestBody TransactionRequest payload) {
        requestCount.incrementAndGet();
        String traceId = MDC.get("traceId");
        logger.info("Applying transaction to account={} type={} amount={} currency={} traceId={}", accountId, payload.getType(), payload.getAmount(), payload.getCurrency(), traceId);
        Transaction transaction = accountService.applyTransaction(accountId, payload.getType(), payload.getAmount(), payload.getCurrency());
        TransactionResponse response = new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getTransactionTimestamp()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<?> getAccountBalance(@PathVariable String accountId) {
        requestCount.incrementAndGet();
        Account account = accountService.getAccountDetails(accountId);
        AccountResponse response = new AccountResponse(account.getAccountId(), account.getBalance(), account.getCurrency());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountDetails(@PathVariable String accountId) {
        requestCount.incrementAndGet();
        Account account = accountService.getAccountDetails(accountId);
        AccountResponse response = new AccountResponse(
                account.getAccountId(),
                account.getBalance(),
                account.getCurrency(),
                account.getTransactions().stream()
                        .map(t -> new TransactionResponse(
                                t.getId(),
                                t.getType(),
                                t.getAmount(),
                                t.getCurrency(),
                                t.getTransactionTimestamp()))
                        .collect(Collectors.toList())
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck()
    {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "Account-Service");
        // Simple custom metric: total requests handled by this instance
        health.put("totalRequests", requestCount.get());
        return ResponseEntity.ok(health);
    }
}