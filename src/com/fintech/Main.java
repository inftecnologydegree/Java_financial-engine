// src/com/fintech/Main.java
package com.fintech;

import com.fintech.model.Account;
import com.fintech.model.Transaction;
import com.fintech.service.FileIngestionService;
import com.fintech.service.TransactionProcessor;

import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        String mockFilePath = "transactions.txt";

        try {
            // Criando massa de teste artificial
            try (FileWriter writer = new FileWriter(mockFilePath)) {
                writer.write("TX999;ACC_A;ACC_B;150.00\n");
                writer.write("TX888;ACC_B;ACC_A;50.00\n");
                writer.write("TX999;ACC_A;ACC_B;150.00\n"); // Linha duplicada propositalmente para testar Idempotência
            }

            // Inicializando massa de contas do banco em memória
            Map<String, Account> databaseMock = new HashMap<>();
            databaseMock.put("ACC_A", new Account("ACC_A", new BigDecimal("1000.00")));
            databaseMock.put("ACC_B", new Account("ACC_B", new BigDecimal("500.00")));

            System.out.println("--- Iniciando Ingestão de Dados (Estilo 1BRC) ---");
            FileIngestionService ingestionService = new FileIngestionService();
            List<Transaction> transactions = ingestionService.parseTransactionFile(mockFilePath);

            System.out.println("--- Processando Transações em Lote (Estilo Rinha/Pix) ---");
            TransactionProcessor processor = new TransactionProcessor(databaseMock);
            processor.processAll(transactions);

            System.out.println("Saldos Finais Consolidados ---");
            databaseMock.values().forEach(acc -> 
                System.out.println("Conta: " + acc.getId() + " | Saldo: R$ " + acc.getBalance())
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
