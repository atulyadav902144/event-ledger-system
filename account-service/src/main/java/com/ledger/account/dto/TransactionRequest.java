package com.ledger.account.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.math.BigDecimal;

@Data
public class TransactionRequest {

    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "CREDIT|DEBIT", message = "Transaction type must be CREDIT or DEBIT")
    private String type;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;
}