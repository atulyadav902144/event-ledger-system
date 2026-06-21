package com.ledger.account.entity;

import lombok.Data;
import lombok.ToString;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "accounts", indexes = {@Index(name = "idx_account_id", columnList = "accountId")})
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountId;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @ToString.Exclude
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions = new ArrayList<>();
}