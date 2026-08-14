```mermaid
Diagrama de Fluxo de Dados (Dataflow)

 [ Arquivo: transactions.txt ] 
               │
               ▼  (Mapeamento de Memória via OS Kernel)
 ┌────────────────────────────────────────┐
 │   1. Ingestão (FileIngestionService)    │ ──> Usa MappedByteBuffer
 └────────────────────────────────────────┘
               │
               ▼  (Geração de Lista de Records Imutáveis)
 ┌────────────────────────────────────────┐
 │ 2. Distribuição (TransactionProcessor)  │ ──> Dispara uma Virtual Thread
 └────────────────────────────────────────┘     para cada transação
               │
        ┌──────┴──────┐  (Execução Concorrente e Assíncrona)
        ▼             ▼
  [V-Thread 1]   [V-Thread 2] ... [V-Thread N]
        │             │
        ▼             ▼
 ┌────────────────────────────────────────┐
 │      3. Filtro de Idempotência         │ ──> Consulta ConcurrentHashMap
 └────────────────────────────────────────┘     (Bloqueia duplicados)
               │
               ▼  (Ordenação Alfabética de IDs de Conta)
 ┌────────────────────────────────────────┐
 │      4. Prevenção de Deadlock          │ ──> Adquire Lock 1 -> Lock 2
 └────────────────────────────────────────┘     via tryLock() com Timeout
               │
               ▼  (Validação de Saldo e Débito/Crédito)
 ┌────────────────────────────────────────┐
 │       5. Mutação de Estado (A/B)       │ ──> Libera os Locks após a escrita
 └────────────────────────────────────────┘
               │
               ▼
   [ Estado Consolidado em Memória ]

-------------------------------------------------------------------------------------------------

Diagrama de Sequência e Ciclo de Vida do Lock

V-Thread (TX)             Idempotency Set            Account A (Lock 1)        Account B (Lock 2)
     │                           │                           │                         │
     │─── 1. add(txId) ─────────>│                           │                         │
     │    (Verifica duplicado)   │                           │                         │
     │<── true (Permite) ────────│                           │                         │
     │                           │                           │                         │
     │─── 2. tryLock(5s) ───────────────────────────────────>│                         │
     │    (Tenta travar menor ID)│                           │                         │
     │<── true (Sucesso) ────────────────────────────────────│                         │
     │                           │                           │                         │
     │─── 3. tryLock(5s) ─────────────────────────────────────────────────────────────>│
     │    (Tenta travar maior ID)│                           │                         │
     │<── true (Sucesso) ──────────────────────────────────────────────────────────────│
     │                           │                           │                         │
     │─── 4. Executa Regra de Negócio (Débito A / Crédito B) ───────────────────────────┐
     │    ──────────────────────────────────────────────────────────────────────────────┘
     │                           │                           │                         │
     │─── 5. unlock() ────────────────────────────────────────────────────────────────>│
     │    (Libera maior ID)      │                           │                         │
     │                           │                           │                         │
     │─── 6. unlock() ──────────────────────────────────────>│                         │
     │    (Libera menor ID)      │                           │                         │

```
