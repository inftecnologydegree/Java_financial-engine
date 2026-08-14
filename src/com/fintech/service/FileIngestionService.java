// src/com/fintech/service/FileIngestionService.java
package com.fintech.service;

import com.fintech.model.Transaction;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileIngestionService {

    /**
     * Mapeia um arquivo diretamente em memória para leitura ultrarrápida.
     */
    public List<Transaction> parseTransactionFile(String filePath) throws Exception {
        List<Transaction> transactions = new ArrayList<>();
        
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r");
             FileChannel channel = file.getChannel()) {
            
            long fileSize = channel.size();
            // Mapeia o arquivo inteiro em memória (Modo Read-Only)
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            
            StringBuilder lineBuilder = new StringBuilder();
            while (buffer.hasRemaining()) {
                char c = (char) buffer.get();
                if (c == '\n' || !buffer.hasRemaining()) {
                    if (c != '\n') lineBuilder.append(c);
                    String line = lineBuilder.toString().trim();
                    if (!line.isEmpty()) {
                        transactions.add(parseLine(line));
                    }
                    lineBuilder.setLength(0); // Reseta o builder para a próxima linha
                } else {
                    lineBuilder.append(c);
                }
            }
        }
        return transactions;
    }

    // Espera formato: id_transacao;conta_origem;conta_destino;valor
    private Transaction parseLine(String line) {
        String[] tokens = line.split(";");
        return new Transaction(tokens[0], tokens[1], tokens[2], new BigDecimal(tokens[3]));
    }
}
