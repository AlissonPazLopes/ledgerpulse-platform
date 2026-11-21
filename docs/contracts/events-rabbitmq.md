# 🐇 Contrato de Mensageria & Eventos RabbitMQ (`events-rabbitmq.md`)

> **Versão do Contrato:** `v1.0-frozen`  
> **Responsável:** Chat 01 (Arquiteto de Software & Contratos)  
> **Garantia:** Transactional Outbox (*at-least-once* na publicação) + Idempotência por `eventId` no consumidor.

---

## 1. Topologia de Exchanges e Filas

| Componente | Tipo | Nome Oficial | Função |
| :--- | :--- | :--- | :--- |
| **Exchange Principal** | `topic` (durable) | `ledger.events` | Recebe todos os eventos publicados pelo `OutboxRelayWorker` do `ledger-core-api`. |
| **Exchange de Retry** | `direct` (durable) | `ledger.retry` | Roteia mensagens falhas para as filas de atraso com TTL exponencial (`retry.5s`, `retry.30s`, `retry.120s`). |
| **Dead-Letter Exchange** | `direct` (durable) | `ledger.dlx` | Encaminha mensagens esgotadas (após 3 retentativas) para a DLQ final. |
| **Fila Principal de Webhooks** | `queue` (durable) | `ledger.webhooks.dispatch` | Consumida pelo `webhook-dispatcher` (ligada à `ledger.events`). |
| **Fila Retry 1 (5s)** | `queue` (durable) | `ledger.retry.5s` | `x-message-ttl: 5000`, `x-dead-letter-exchange: ""` e `x-dead-letter-routing-key: ledger.webhooks.dispatch`. |
| **Fila Retry 2 (30s)** | `queue` (durable) | `ledger.retry.30s` | `x-message-ttl: 30000`, `x-dead-letter-exchange: ""` e `x-dead-letter-routing-key: ledger.webhooks.dispatch`. |
| **Fila Retry 3 (2min)** | `queue` (durable) | `ledger.retry.120s` | `x-message-ttl: 120000`, `x-dead-letter-exchange: ""` e `x-dead-letter-routing-key: ledger.webhooks.dispatch`. |
| **Dead-Letter Queue (DLQ)** | `queue` (durable) | `ledger.webhooks.dlq` | Armazena eventos que falharam em todas as tentativas até replay manual via API/UI. |

> **Nota de Escalonamento de Retries (`x-death`) e Retorno Direto:**
> - **Escalonamento sem loop infinito (`5s → 30s → 120s → DLQ`):** ao falhar na entrega HTTP (`5xx` ou timeout), o consumidor do `webhook-dispatcher` inspeciona o header nativo `x-death` do RabbitMQ (somando `count` de passagens pelas filas de retry) e republica explicitamente na exchange `ledger.retry` com a routing key do próximo degrau (`retry.5s` → `retry.30s` → `retry.120s`), dando `ACK` na mensagem original. Após esgotar a 3ª tentativa (ou em erro permanente `4xx` não recuperável), publica na exchange `ledger.dlx` para estacionar em `ledger.webhooks.dlq`.
> - **Retorno direto à fila principal:** ao expirar o TTL nas filas `ledger.retry.*`, a mensagem retorna **diretamente** para `ledger.webhooks.dispatch` via *default exchange* (`x-dead-letter-exchange: ""` + `x-dead-letter-routing-key: ledger.webhooks.dispatch`) — e **não** via `ledger.events` — para evitar *re-broadcast* acidental caso outros consumidores assinem o topic exchange no futuro.

---

## 2. Política de Retry Exponencial (5s → 30s → 120s → DLQ)

```mermaid
flowchart LR
    PUB["ledger.events (Topic)"] --> MAIN["ledger.webhooks.dispatch"]
    MAIN -->|"HTTP 2xx"| OK["Entrega Confirmada (DELIVERED)"]
    MAIN -->|"Falha #1 (x-death=0) -> retry.5s"| R1["ledger.retry.5s (TTL 5s)"]
    R1 -->|"Retorno direto (default exchange)"| MAIN
    MAIN -->|"Falha #2 (x-death=1) -> retry.30s"| R2["ledger.retry.30s (TTL 30s)"]
    R2 -->|"Retorno direto (default exchange)"| MAIN
    MAIN -->|"Falha #3 (x-death=2) -> retry.120s"| R3["ledger.retry.120s (TTL 120s)"]
    R3 -->|"Retorno direto (default exchange)"| MAIN
    MAIN -->|"Falha #4 (Exausto -> ledger.dlx)"| DLQ["ledger.webhooks.dlq"]
    DLQ -->|"Replay Manual"| MAIN
```

---

## 3. Routing Keys Oficiais (`eventType`)

| Routing Key / `eventType` | Produtor | Quando é Emitido |
| :--- | :--- | :--- |
| `payment.created` | `ledger-core-api` | Cobrança registrada antes da conclusão ou quando entra em análise. |
| `payment.manual_review_required` | `ledger-core-api` | Motor de risco retornou `REVIEW` ou houve fallback de Circuit Breaker. |
| `payment.settled` | `ledger-core-api` | Pagamento autorizado e partidas dobradas + split gravados no Ledger. |
| `payment.declined` | `ledger-core-api` | Pagamento bloqueado pelo antifraude ou reprovado na revisão manual. |
| `payment.refunded` | `ledger-core-api` | Estorno total ou parcial liquidado com lançamentos compensatórios. |
| `risk.feedback.recorded` | `ledger-core-api` | Analista concluiu revisão manual (útil para retreino futuro de ML). |

---

## 4. Envelope JSON Padrão do Evento

Todo evento gravado em `outbox_events` e enviado para `ledger.events` deve obedecer exatamente a este envelope:

```json
{
  "eventId": "c7a615f1-82b3-4e11-9c2a-5d2d1b63e701",
  "eventType": "payment.settled",
  "schemaVersion": "1.0",
  "occurredAt": "2026-11-20T22:31:05Z",
  "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "payload": {
    "paymentId": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
    "ledgerTransactionId": "f9e8d7c6-5b4a-4321-8765-112233445566",
    "amountCents": 15000,
    "platformFeeCents": 375,
    "netMerchantCents": 14625,
    "currency": "BRL",
    "status": "SETTLED",
    "riskScore": 14,
    "reasonCodes": []
  }
}
```

### Conexão Transactional Outbox (`ledger_core.outbox_events` → RabbitMQ)
Conforme a modelagem `V3` em [01-modelo-dados-v1.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/architecture/01-modelo-dados-v1.md), todo evento é gravado em `ledger_core.outbox_events` na mesma `@Transactional` do domínio e publicado de forma assíncrona pelo `OutboxRelayWorker`:
- **Mapeamento para o RabbitMQ:**
  - `outbox_events.id` → `eventId` no envelope JSON (e propriedade AMQP `messageId`).
  - `outbox_events.event_type` → `routingKey` na exchange `ledger.events` (`payment.created`, `payment.settled`, `payment.declined`, `payment.manual_review_required`, `payment.refunded`, `risk.feedback.recorded`).
  - `outbox_events.payload_json` → corpo da mensagem AMQP (`application/json`), acompanhado do header `X-Correlation-Id` (originado de `outbox_events.correlation_id`).
- **Política do `OutboxRelayWorker` (`@Scheduled`):**
  1. Busca registros com `status = 'PENDING'` ordenados por `created_at ASC`.
  2. **Sucesso na publicação AMQP:** atualiza `status` para `'PUBLISHED'` e preenche `published_at`.
  3. **Falha de envio ao broker:** incrementa `attempts` e registra a mensagem de erro em `last_error`, mantendo `'PENDING'` para retentativa no próximo ciclo.
  4. **Limite esgotado:** ao atingir o máximo de tentativas configurado (ex.: `5`), transiciona `status` para `'FAILED'` para evitar travamento da fila de relay.

### Regras de Consumidor Idempotente (`webhook-dispatcher`)
1. Ao receber mensagem da fila, verificar na tabela `webhook_deliveries` se `(eventId, endpointId)` já possui registro `DELIVERED`.
2. Calcular assinatura HMAC-SHA256 sobre `timestamp + "." + rawJsonBody` usando o `secret` do endpoint cadastrado.
3. Enviar header `X-LedgerPulse-Signature: t=<unix_epoch>,v1=<hex_hmac>`, além de `X-LedgerPulse-Event-Id` e `X-Correlation-Id`.
