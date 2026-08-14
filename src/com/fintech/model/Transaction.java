// src/com/fintech/model/Transaction.java
package com.fintech.model;

import java.math.BigDecimal;

/**
 * Representa uma transferência financeira Pix/TED.
 * O uso de records garante imutabilidade nativa para segurança entre threads.
 */
public record Transaction(
    String transactionId, // Chave de idempotência
    String sourceAccountId,
    String targetAccountId,
    BigDecimal amount
) {}
