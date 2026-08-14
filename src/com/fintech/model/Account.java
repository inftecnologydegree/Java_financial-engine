// src/com/fintech/model/Account.java
package com.fintech.model;

import java.math.BigDecimal;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Representa uma conta bancária com controle estrito de concorrência.
 */
public class Account {
    private final String id;
    private BigDecimal balance;
    // Lock exclusivo para evitar race conditions no saldo desta conta
    private final Lock lock = new ReentrantLock();

    public Account(String id, BigDecimal initialBalance) {
        this.id = id;
        this.balance = initialBalance;
    }

    public String getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public Lock getLock() { return lock; }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
