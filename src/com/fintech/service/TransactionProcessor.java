// src/com/fintech/service/TransactionProcessor.java
package com.fintech.service;

import com.fintech.model.Account;
import com.fintech.model.Transaction;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class TransactionProcessor {
    // Cache de Idempotência: Garante que o mesmo ID de transação não rode duas vezes
    private final Set<String> processedTransactions = ConcurrentHashMap.newKeySet();
    private final Map<String, Account> accounts;

    public TransactionProcessor(Map<String, Account> accounts) {
        this.accounts = accounts;
    }

    public void processAll(java.util.List<Transaction> transactions) throws InterruptedException {
        // Inicializa um Executor que cria uma Virtual Thread para cada tarefa enviada
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Transaction tx : transactions) {
                executor.submit(() -> {
                    try {
                        processTransaction(tx);
                    } catch (Exception e) {
                        System.err.println("Erro ao processar TX " + tx.transactionId() + ": " + e.getMessage());
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        }
    }

    private void processTransaction(Transaction tx) throws Exception {
        // 1. Verificação de Idempotência
        if (!processedTransactions.add(tx.transactionId())) {
            System.out.println("⚠️ [Idempotência] Transação duplicada ignorada: " + tx.transactionId());
            return;
        }

        Account source = accounts.get(tx.sourceAccountId());
        Account target = accounts.get(tx.targetAccountId());

        if (source == null || target == null) throw new IllegalArgumentException("Conta inválida.");

        // 2. Prevenção de Deadlock: Sempre adquire travas na mesma ordem determinística (Ordem Alfabética de ID)
        Account firstLock = source.getId().compareTo(target.getId()) < 0 ? source : target;
        Account secondLock = firstLock == source ? target : source;

        Lock lock1 = firstLock.getLock();
        Lock lock2 = secondLock.getLock();

        // Tenta adquirir os locks de forma resiliente
        if (lock1.tryLock(5, TimeUnit.SECONDS)) {
            try {
                if (lock2.tryLock(5, TimeUnit.SECONDS)) {
                    try {
                        // 3. Regra de Negócio Crítica (Consistência Isolada)
                        if (source.getBalance().compareTo(tx.amount()) < 0) {
                            throw new IllegalStateException("Saldo insuficiente na conta: " + source.getId());
                        }
                        source.debit(tx.amount());
                        target.credit(tx.amount());
                        System.out.println("✅ [Sucesso] TX " + tx.transactionId() + ": " + tx.amount() + " de " + source.getId() + " para " + target.getId());
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
