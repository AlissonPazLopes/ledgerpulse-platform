# 🌐 Visão Geral dos Contratos REST v1 (`api-v1-overview.md`)

> **Versão do Contrato:** `v1`  
> **Responsável:** Chat 01 (Arquiteto de Software & Contratos)  
> **Padrão de Erro:** RFC 7807 / RFC 9457 (`application/problem+json`)

---

## 1. Convenções Globais

1. **Base Paths:**
   - `ledger-core-api` (`:8080`): `/api/v1/...`
   - `risk-engine-ai` (`:8001`): `/v1/...` (e `/health`, `/metrics`)
   - `webhook-dispatcher` (`:8082`): `/api/v1/...`
2. **Representação Monetária:**
   - Todo valor monetário usa **centavos inteiros** (`amountCents` em JSON camelCase nos serviços Java; `amount_cents` em snake_case interno no Python, sempre mapeado explicitamente pelo cliente Java).
   - Moeda em código ISO-4217 (`currency`: `"BRL"` por padrão).
   - Taxas percentuais em *basis points* inteiros (`feeBps`, ex.: `250` = `2.50%`).
3. **Datas e Identificadores:**
   - IDs de negócio em `UUID v4` (ou `UUID v7` ordenável).
   - Timestamps em ISO-8601 UTC (`2026-11-20T22:30:00Z`).
4. **Headers Padrão:**
   - `Authorization: Bearer <jwt>` — obrigatório para endpoints do Console Web / Operadores (`ADMIN`, `ANALYST`, `VIEWER`).
   - `X-API-Key: <merchant_api_key>` — obrigatório para integrações S2S de criação de pagamentos/estornos por *merchants*.
   - `Idempotency-Key: <uuid_ou_chave_unica>` — **obrigatório** em `POST /api/v1/payments` e `POST /api/v1/payments/{id}/refunds`.
   - `X-Correlation-Id: <uuid>` — opcional na entrada (gerado automaticamente pelo filtro se ausente) e sempre devolvido na resposta e propagado ao Python e RabbitMQ.
   - `X-LedgerPulse-Signature: t=<unix_epoch>,v1=<hmac_sha256_hex>` — enviado pelo `webhook-dispatcher` em toda entrega HTTP de webhook.

---

## 2. Contrato Padrão de Erro (RFC 7807 `ProblemDetail`)

Todas as respostas `4xx` e `5xx` dos serviços Java devem seguir o formato estruturado abaixo (`Content-Type: application/problem+json`):

```json
{
  "type": "https://ledgerpulse.dev/errors/idempotency-conflict",
  "title": "Idempotency Key Conflict",
  "status": 409,
  "detail": "Idempotency-Key já utilizada anteriormente com payload divergente.",
  "instance": "/api/v1/payments",
  "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
  "timestamp": "2026-11-20T22:30:00Z",
  "errors": [
    {
      "field": "amountCents",
      "message": "must be greater than 0"
    }
  ]
}
```

---

## 3. Endpoints Principais — `ledger-core-api` (`:8080`)

| Domínio | Método & Rota | Auth / Role | Resumo |
| :--- | :--- | :--- | :--- |
| **Health** | `GET /api/v1/health` | Público | Status operacional, versão e conectividade básica. |
| **Auth** | `POST /api/v1/auth/login` | Público | Autentica operador e emite JWT + perfil (`ADMIN`, `ANALYST`, `VIEWER`). |
| **Auth** | `GET /api/v1/auth/me` | JWT | Retorna dados e permissões do usuário autenticado. |
| **Accounts** | `POST /api/v1/accounts` | JWT (`ADMIN`) | Cria conta (`MERCHANT`, `PLATFORM_FEE`, `ESCROW`, `RECIPIENT`) com moeda e saldo inicial `0`. |
| **Accounts** | `GET /api/v1/accounts` | JWT | Lista contas paginadas com saldo disponível (`availableBalanceCents`) e bloqueado (`pendingBalanceCents`). |
| **Accounts** | `GET /api/v1/accounts/{id}` | JWT | Detalhes da conta e versão de concorrência (`version`). |
| **Ledger** | `GET /api/v1/ledger/transactions` | JWT | Lista transações contábeis paginadas com filtros por data, conta e tipo. |
| **Ledger** | `GET /api/v1/ledger/transactions/{id}` | JWT | Detalha todas as `ledger_entries` (`DEBIT`/`CREDIT`) da transação provando balanço zerado. |
| **Payments** | `POST /api/v1/payments` | `X-API-Key` ou JWT + `Idempotency-Key` | Cria cobrança, consulta `risk-engine-ai`, executa Split + Ledger (ou envia para `MANUAL_REVIEW`) e grava Outbox. |
| **Payments** | `GET /api/v1/payments` | JWT | Lista pagamentos com filtros por `status`, `merchantId` e período. |
| **Payments** | `GET /api/v1/payments/{id}` | JWT ou `X-API-Key` | Consulta pagamento, split calculado, score de risco e `reasonCodes`. |
| **Refunds** | `POST /api/v1/payments/{id}/refunds` | `X-API-Key` ou JWT (`ADMIN`/`ANALYST`) + `Idempotency-Key` | Executa estorno total ou parcial via lançamentos compensatórios imutáveis. |
| **Risk Review** | `GET /api/v1/risk/reviews` | JWT (`ANALYST`/`ADMIN`) | Fila paginada de pagamentos aguardando revisão manual (`MANUAL_REVIEW`). |
| **Risk Review** | `POST /api/v1/risk/reviews/{paymentId}/decision` | JWT (`ANALYST`/`ADMIN`) | Analista decide `APPROVE` ou `DECLINE` com justificativa, liquidando ou recusando no Ledger. |
| **Reconciliation** | `POST /api/v1/reconciliation/run` | JWT (`ADMIN`) | Dispara verificação contábil sob demanda (`accounts` vs `ledger_entries` vs `payments`). |
| **Reconciliation** | `GET /api/v1/reconciliation/reports` | JWT | Lista relatórios periódicos de conciliação e status de integridade. |
| **Reconciliation** | `GET /api/v1/reconciliation/export.csv` | JWT | Exporta relatório de conciliação e lançamentos em CSV. |
| **SSE Stream** | `GET /api/v1/stream/events` | JWT | Stream `text/event-stream` em tempo real para o Dashboard Executivo. |

---

## 4. Endpoints Principais — `risk-engine-ai` (`:8001`)

| Método & Rota | Resumo | Request / Response Principal |
| :--- | :--- | :--- |
| `GET /health` | Healthcheck do serviço FastAPI e status do modelo `.joblib` carregado. | `{ "status": "ok", "modelLoaded": true, "modelVersion": "v1.0.0" }` |
| `GET /v1/risk/model-info` | Metadados do modelo treinado (algoritmo, features, métricas de treino). | `{ "algorithm": "Hybrid(IsolationForest+RandomForest+Rules)", "features": [...] }` |
| `POST /v1/risk/evaluate` | Avalia risco de pagamento antes da liquidação. | Entrada: `payment_id`, `merchant_id`, `payer_id`, `amount_cents`, `payment_method`, `ip_country`, `velocity_1h`, `hour_of_day`, `account_age_days`. Saída: `risk_score` (`0..100`), `decision` (`APPROVE`/`REVIEW`/`BLOCK`), `reason_codes` (`list[str]`), `model_version`. |
| `GET /metrics` | Métricas Prometheus do serviço Python. | Formato OpenMetrics / Prometheus text. |

---

## 5. Endpoints Principais — `webhook-dispatcher` (`:8082`)

| Método & Rota | Auth / Role | Resumo |
| :--- | :--- | :--- |
| `POST /api/v1/webhooks/endpoints` | JWT (`ADMIN`) | Cadastra URL de destino e *secret* HMAC-SHA256 de um merchant. |
| `GET /api/v1/webhooks/endpoints` | JWT | Lista endpoints cadastrados e status (`ACTIVE`/`PAUSED`). |
| `GET /api/v1/webhooks/deliveries` | JWT | Lista histórico de tentativas de entrega (`PENDING`, `DELIVERED`, `RETRYING`, `DLQ`), latência e código HTTP. |
| `POST /api/v1/webhooks/dlq/{deliveryId}/replay` | JWT (`ADMIN`/`ANALYST`) | Reenfileira manualmente uma entrega que caiu em DLQ para nova tentativa imediata. |
| `POST /api/v1/sink/test` | Público / Dev | Simulador local (**Merchant Webhook Sink**) configurável via query/body ou endpoint de modo (`OK_200`, `FAIL_500`, `TIMEOUT`) para demonstrar retries e DLQ sem depender de serviço externo. |
| `PUT /api/v1/sink/mode` | JWT / Dev | Alterna o comportamento do Webhook Sink (`OK_200`, `FAIL_500`, `TIMEOUT`). |
