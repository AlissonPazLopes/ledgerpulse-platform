# ADR-002 — Double-Entry Ledger Imutável, Precisão em Centavos (`BIGINT`) e Transactional Outbox

- **Status:** Aceito
- **Data:** 2026-11-20
- **Decisor:** Chat 01 (Arquiteto de Software & Contratos)

---

## 1. Contexto

Sistemas de pagamento não podem tolerar inconsistências silenciosas causadas por:
- Erros de arredondamento de ponto flutuante (`float`/`double`);
- Alteração ou exclusão destrutiva de histórico financeiro (`UPDATE`/`DELETE`);
- Condições de corrida (*race conditions*) ao atualizar saldos concorrentes;
- *Dual-write problem*: confirmar uma transação no PostgreSQL e falhar ao publicar o evento correspondente no RabbitMQ (ou publicar o evento antes do rollback SQL).

---

## 2. Decisão

1. **Precisão Monetária Estrita (`amount_cents` / `BIGINT`):**
   - Todos os valores monetários são armazenados no PostgreSQL como `BIGINT` representando centavos inteiros (`amount_cents`), mapeados em Java para `long` (`amountCents`), em Python para `int` (`amount_cents`) e em TypeScript para `number` inteiro.
   - O uso de `float` ou `double` para dinheiro é proibido em qualquer camada.
2. **Double-Entry Ledger Imutável (Partidas Dobradas):**
   - Toda movimentação financeira cria uma `ledger_transaction` composta por N `ledger_entries` (`DEBIT` e `CREDIT`).
   - Invariante obrigatória validada em domínio e serviço:
     $$\sum \text{amount\_cents (DEBIT)} = \sum \text{amount\_cents (CREDIT)}$$
   - A tabela `ledger_entries` é estritamente **append-only**. Estornos (`REFUND`) e ajustes sempre inserem novas linhas compensatórias revertendo o efeito contábil.
3. **Controle de Concorrência Otimista (`@Version`):**
   - A entidade `Account` possui coluna `version` (`@Version` JPA) para detectar atualizações concorrentes de saldo (`available_balance_cents` / `pending_balance_cents`) e impedir *lost updates*.
4. **Transactional Outbox Pattern:**
   - Todo evento destinado ao RabbitMQ é persistido primeiro na tabela `outbox_events` dentro da **mesma `@Transactional`** que grava `payments` e `ledger_entries`.
   - Um worker `@Scheduled` (`OutboxRelayWorker`) lê os registros `PENDING` em ordem, publica no RabbitMQ e marca como `PUBLISHED` (garantia de entrega *at-least-once*).

---

## 3. Alternativas Consideradas

- **Atualizar apenas coluna `balance` na tabela `accounts` sem `ledger_entries`:** Rejeitado porque perde rastreabilidade e auditabilidade contábil.
- **Usar `NUMERIC(19,2)` / `BigDecimal` em todos os payloads JSON:** Útil para taxas percentuais complexas, mas centavos inteiros em `BIGINT` + `long` eliminam ambiguidades de serialização JSON entre Java, Python e TypeScript. Taxas percentuais continuam podendo usar basis points inteiros (`fee_bps`, ex.: `250` = `2,50%`).
- **Publicar no RabbitMQ direto dentro do `PaymentService`:** Rejeitado por vulnerabilidade clássica de *dual-write*.

---

## 4. Consequências

### Positivas
- Auditoria e conciliação diária tornam-se determinísticas: o saldo de qualquer conta deve sempre bater com a soma histórica dos seus `ledger_entries`.
- Zero perda silenciosa de eventos mesmo se o RabbitMQ reiniciar durante picos de checkout.
- Diferencial técnico claro em entrevistas de Fintech/Banco/Meios de Pagamento.

### Trade-offs / Cuidados
- Como o Outbox garante entrega *at-least-once*, os consumidores no `webhook-dispatcher` **devem ser idempotentes** verificando `eventId` único antes de disparar entregas duplicadas.
- Estornos parciais exigem cálculo determinístico da reversão proporcional do split (tratando resto de divisão inteira de centavos de forma explícita e determinística).
