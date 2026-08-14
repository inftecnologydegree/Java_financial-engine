// src/com/fintech/Main.java
package com.fintech;

import com.fintech.model.Account;
import com.fintech.model.Transaction;
import com.fintech.service.FileIngestionService;
import com.fintech.service.TransactionProcessor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        String mockFilePath = "stress_transactions.txt";
        int totalTransactions = 1_000_000;
        int totalAccounts = 1_000; // Expanded from 2 to 1,000 to drastically reduce Lock contention

        try {
            System.out.println("====== [1/4] GERANDO ARQUIVO DE ESTRESSE REALINHADO ======");
            System.out.println("Criando " + totalTransactions + " transações distribuídas...");
            long startGeneration = System.currentTimeMillis();
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(mockFilePath))) {
                for (int i = 1; i <= totalTransactions; i++) {
                    String txId = "TX_" + i;
                    
                    // Distributes transactions evenly across 1000 accounts using modulo math
                    int sourceIdNum = (i % totalAccounts);
                    int targetIdNum = ((i + 1) % totalAccounts);
                    
                    String source = "ACC_" + sourceIdNum;
                    String target = "ACC_" + targetIdNum;
                    String amount = "1.00";
                    
                    writer.write(txId + ";" + source + ";" + target + ";" + amount + "\n");
                    
                    if (i % 200_000 == 0) {
                        writer.write(txId + ";" + source + ";" + target + ";" + amount + "\n"); // Idempotency check
                    }
                }
            }
            long endGeneration = System.currentTimeMillis();
            System.out.printf("✅ Arquivo gerado em %.2f segundos.%n", (endGeneration - startGeneration) / 1000.0);

            // Populating 1,000 accounts in memory
            Map<String, Account> databaseMock = new HashMap<>();
            for (int i = 0; i < totalAccounts; i++) {
                String accId = "ACC_" + i;
                databaseMock.put(accId, new Account(accId, new BigDecimal("1000000.00")));
            }

            System.out.println("\n====== [2/4] INGESTÃO DE DADOS (ESTILO 1BRC) ======");
            long startIngestion = System.currentTimeMillis();
            FileIngestionService ingestionService = new FileIngestionService();
            List<Transaction> transactions = ingestionService.parseTransactionFile(mockFilePath);
            long endIngestion = System.currentTimeMillis();
            System.out.printf("✅ Ingestão concluída em %.2f segundos.%n", (endIngestion - startIngestion) / 1000.0);

            System.out.println("\n====== [3/4] PROCESSAMENTO EM MASSA (VIRTUAL THREADS) ======");
            System.out.println("Executando com otimização de concorrência por hardware...");
            
            System.gc(); // Clean up memory before starting the heavy CPU phase
            long startProcessing = System.currentTimeMillis();

            TransactionProcessor processor = new TransactionProcessor(databaseMock);
            processor.processAll(transactions);

            long endProcessing = System.currentTimeMillis();
            System.out.printf("🚀 SUCESSO ABSOLUTO: 1 Milhão de transações processadas em %.2f segundos!%n", (endProcessing - startProcessing) / 1000.0);

            System.out.println("\n====== [4/4] VERIFICAÇÃO DE AMOSTRA DE SALDOS ======");
            System.out.println("Saldo da ACC_0: R$ " + databaseMock.get("ACC_0").getBalance());
            System.out.println("Saldo da ACC_500: R$ " + databaseMock.get("ACC_500").getBalance());

        } catch (Exception e) {
            System.err.println("Erro crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
