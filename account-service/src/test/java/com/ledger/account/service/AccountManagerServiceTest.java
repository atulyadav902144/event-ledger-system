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
    void testApplyTransaction_CreatesAccountAndTransaction() {
        Account newAccount = new Account();
        newAccount.setAccountId("new-acct");
        newAccount.setTransactions(new ArrayList<>());

        when(accountRepository.findByAccountId("new-acct")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(newAccount);

        accountService.applyTransaction("new-acct", "CREDIT", new BigDecimal("100.00"));

        verify(accountRepository).save(argThat(account -> "new-acct".equals(account.getAccountId())));
        verify(transactionRepository).save(argThat(transaction ->
                "CREDIT".equals(transaction.getType()) &&
                new BigDecimal("100.00").equals(transaction.getAmount())
        ));
    }

    @Test
    void testApplyTransaction_UpdatesBalanceCorrectly() {
        Account account = new Account();
        account.setAccountId("acct-123");
        account.setBalance(new BigDecimal("100.00"));
        account.setTransactions(new ArrayList<>());

        Transaction existingTransaction = new Transaction();
        existingTransaction.setType("CREDIT");
        existingTransaction.setAmount(new BigDecimal("100.00"));
        account.getTransactions().add(existingTransaction);


        when(accountRepository.findByAccountId("acct-123")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        accountService.applyTransaction("acct-123", "DEBIT", new BigDecimal("50.00"));

        verify(accountRepository).save(argThat(savedAccount ->
                savedAccount.getBalance().compareTo(new BigDecimal("50.00")) == 0
        ));
    }

    @Test
    void testGetAccountDetails_ReturnsCorrectAccount() {
        Account account = new Account();
        account.setAccountId("acct-details");
        account.setBalance(new BigDecimal("250.75"));
        account.setTransactions(Collections.emptyList());

        when(accountRepository.findByAccountId("acct-details")).thenReturn(Optional.of(account));

        Account result = accountService.getAccountDetails("acct-details");

        assertEquals("acct-details", result.getAccountId());
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