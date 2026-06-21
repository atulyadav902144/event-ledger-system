package com.ledger.account.service;

import com.ledger.account.entity.Account;
import com.ledger.account.entity.AccountRepository;
import com.ledger.account.entity.Transaction;
import com.ledger.account.entity.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AccountManagerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountManagerService accountService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testApplyTransaction_CreatesAccountAndSetsCorrectBalance() {
        Account newAccount = new Account();
        newAccount.setAccountId("new-acct");
        newAccount.setTransactions(new ArrayList<>());

        when(accountRepository.findByAccountId("new-acct")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(newAccount);

        accountService.applyTransaction("new-acct", "CREDIT", new BigDecimal("100.00"), "USD");

        verify(accountRepository).save(argThat(account ->
                "new-acct".equals(account.getAccountId()) &&
                "USD".equals(account.getCurrency()) &&
                account.getBalance().compareTo(new BigDecimal("100.00")) == 0
        ));
    }

    @Test
    void testApplyTransaction_CreditUpdatesBalanceCorrectly() {
        Account account = new Account();
        account.setAccountId("acct-123");
        account.setBalance(new BigDecimal("100.00"));
        account.setCurrency("USD");
        account.setTransactions(new ArrayList<>());

        Transaction existingTransaction = new Transaction();
        existingTransaction.setType("CREDIT");
        existingTransaction.setAmount(new BigDecimal("100.00"));
        account.getTransactions().add(existingTransaction);

        when(accountRepository.findByAccountId("acct-123")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        accountService.applyTransaction("acct-123", "CREDIT", new BigDecimal("50.00"), "USD");

        verify(accountRepository).save(argThat(savedAccount ->
                savedAccount.getBalance().compareTo(new BigDecimal("150.00")) == 0
        ));
    }

    @Test
    void testApplyTransaction_DebitUpdatesBalanceCorrectly() {
        Account account = new Account();
        account.setAccountId("acct-123");
        account.setBalance(new BigDecimal("100.00"));
        account.setCurrency("USD");
        account.setTransactions(new ArrayList<>());

        Transaction existingTransaction = new Transaction();
        existingTransaction.setType("CREDIT");
        existingTransaction.setAmount(new BigDecimal("100.00"));
        account.getTransactions().add(existingTransaction);

        when(accountRepository.findByAccountId("acct-123")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        accountService.applyTransaction("acct-123", "DEBIT", new BigDecimal("75.00"), "USD");

        verify(accountRepository).save(argThat(savedAccount ->
                savedAccount.getBalance().compareTo(new BigDecimal("25.00")) == 0
        ));
    }

    @Test
    void testApplyTransaction_MismatchedCurrencyThrowsException() {
        Account account = new Account();
        account.setAccountId("acct-multi-currency");
        account.setCurrency("USD");

        when(accountRepository.findByAccountId("acct-multi-currency")).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class, () ->
                accountService.applyTransaction("acct-multi-currency", "CREDIT", new BigDecimal("100.00"), "EUR")
        );
    }

    @Test
    void testGetAccountDetails_ReturnsCorrectAccount() {
        Account account = new Account();
        account.setAccountId("acct-details");
        account.setBalance(new BigDecimal("250.75"));
        account.setCurrency("USD");
        account.setTransactions(Collections.emptyList());

        when(accountRepository.findByAccountId("acct-details")).thenReturn(Optional.of(account));

        Account result = accountService.getAccountDetails("acct-details");

        assertEquals("acct-details", result.getAccountId());
        assertEquals("USD", result.getCurrency());
        assertEquals(0, new BigDecimal("250.75").compareTo(result.getBalance()));
    }

    @Test
    void testGetAccountDetails_AccountNotFoundThrowsException() {
        when(accountRepository.findByAccountId("nonexistent")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                accountService.getAccountDetails("nonexistent")
        );
    }
}