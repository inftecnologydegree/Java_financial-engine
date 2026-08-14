// src/com/fintech/service/TransactionProcessor.java
package com.fintech.service;

import com.fintech.model.Account;
import com.fintech.model.Transaction;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class TransactionProcessor {
    private final Set<String> processedTransactions = ConcurrentHashMap.newKeySet();
    private final Map<String, Account> accounts;

    public TransactionProcessor(Map<String, Account> accounts) {
        this.accounts = accounts;
    }

    public void processAll(java.util.List<Transaction> transactions) throws InterruptedException {
        // LIMITER: Allows only 500 Virtual Threads to actively process transactions at the same time.
        // This prevents the CPU from drowning in queue management.
        Semaphore concurrencyLimiter = new Semaphore(500);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Transaction tx : transactions) {
                // Wait for a slot to open up before submitting the task
                concurrencyLimiter.acquire(); 

                executor.submit(() -> {
                    try {
                        processTransaction(tx);
                    } catch (Exception e) {
                        System.err.println("Erro ao processar TX " + tx.transactionId() + ": " + e.getMessage());
                    } finally {
                        // Release the slot so the next transaction can start
                        concurrencyLimiter.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        }
    }

    private void processTransaction(Transaction tx) throws Exception {
        if (!processedTransactions.add(tx.transactionId())) {
            return; // Silent ignore for duplicates to clean up logs
        }

        Account source = accounts.get(tx.sourceAccountId());
        Account target = accounts.get(tx.targetAccountId());

        if (source == null || target == null) throw new IllegalArgumentException("Conta inválida.");

        // Lock ordering strategy to prevent deadlocks
        Account firstLock = source.getId().compareTo(target.getId()) < 0 ? source : target;
        Account secondLock = firstLock == source ? target : source;

        Lock lock1 = firstLock.getLock();
        Lock lock2 = secondLock.getLock();

        // 5 Seconds timeout
        if (lock1.tryLock(5, TimeUnit.SECONDS)) {
            try {
                if (lock2.tryLock(5, TimeUnit.SECONDS)) {
                    try {
                        if (source.getBalance().compareTo(tx.amount()) < 0) {
                            throw new IllegalStateException("Saldo insuficiente.");
                        }
                        source.debit(tx.amount());
                        target.credit(tx.amount());
                    } finally {
                        lock2.unlock();
                    }
                } else {
                    throw new RuntimeException("Timeout ao tentar travar a segunda conta: " + secondLock.getId());
                }
            } finally {
                lock1.unlock();
            }
        } else {
            throw new RuntimeException("Timeout ao tentar travar a primeira conta: " + firstLock.getId());
        }
    }
}
