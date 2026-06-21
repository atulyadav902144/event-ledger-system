package com.ledger.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.account.dto.TransactionRequest;
import com.ledger.account.entity.Account;
import com.ledger.account.entity.Transaction;
import com.ledger.account.service.AccountManagerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountManagerService accountService;

    @Test
    void testApplyTransaction() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setType("CREDIT");
        request.setAmount(new BigDecimal("100.00"));

        Transaction mockedTransaction = new Transaction();
        mockedTransaction.setId(1L);
        mockedTransaction.setType("CREDIT");
        mockedTransaction.setAmount(new BigDecimal("100.00"));
        mockedTransaction.setTransactionTimestamp(LocalDateTime.now());

        when(accountService.applyTransaction("acct-123", "CREDIT", new BigDecimal("100.00"))).thenReturn(mockedTransaction);

        mockMvc.perform(post("/accounts/acct-123/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(100.0));
    }

    @Test
    void testApplyTransaction_InvalidType() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setType("INVALID");
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/accounts/acct-123/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testApplyTransaction_InvalidAmount() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setType("CREDIT");
        request.setAmount(new BigDecimal("-100.00"));

        mockMvc.perform(post("/accounts/acct-123/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetAccountDetails() throws Exception {
        Account account = new Account();
        account.setAccountId("acct-123");
        account.setBalance(new BigDecimal("100.00"));
        account.setTransactions(Collections.emptyList());

        when(accountService.getAccountDetails("acct-123")).thenReturn(account);

        mockMvc.perform(get("/accounts/acct-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-123"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/accounts/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}