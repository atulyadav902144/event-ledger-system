package com.ledger.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse {
    private String accountId;
    private BigDecimal balance;
    private String currency;
    private List<TransactionResponse> transactions;

    public AccountResponse(String accountId, BigDecimal balance, String currency, List<TransactionResponse> transactions) {
        this.accountId = accountId;
        this.balance = balance;
        this.currency = currency;
        this.transactions = transactions;
    }

    public AccountResponse(String accountId, BigDecimal balance, String currency) {
        this.accountId = accountId;
        this.balance = balance;
        this.currency = currency;
    }
}