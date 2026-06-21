package com.ledger.account.service;

import com.ledger.account.entity.Account;
import com.ledger.account.entity.AccountRepository;
import com.ledger.account.entity.Transaction;
import com.ledger.account.entity.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class AccountManagerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountManagerService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction applyTransaction(String accountId, String type, BigDecimal amount, String currency) {
        Account account = accountRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    Account newAccount = new Account();
                    newAccount.setAccountId(accountId);
                    newAccount.setCurrency(currency); // Set currency on first transaction
                    return accountRepository.save(newAccount);
                });

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);

        // Add the new transaction to the in-memory list for calculation before saving
        account.getTransactions().add(transaction);

        // Recalculate balance from all transactions (including the new one) to ensure consistency
        BigDecimal newBalance = account.getTransactions().stream()
                .map(t -> "CREDIT".equalsIgnoreCase(t.getType()) ? t.getAmount() : t.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        account.setBalance(newBalance);

        // Saving the account will also save the new transaction due to CascadeType.ALL
        accountRepository.save(account);

        return transaction;
    }

    public Account getAccountDetails(String accountId) {
        return accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }
}