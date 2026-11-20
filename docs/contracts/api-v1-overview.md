# 🌐 Contratos REST v1 (`api-v1-overview.md`)

> **Versão do Contrato:** `v1.0-frozen` (Semanas 1 e 2 congeladas)  
> **Especificação OpenAPI (`ledger-core-api`):** [openapi-ledger-core-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-ledger-core-v1.yaml)  
> **Responsável:** Chat 01 (Arquiteto de Software & Contratos)  
> **Padrão de Erro:** RFC 7807 / RFC 9457 (`application/problem+json`)

---

## 1. Convenções Globais

1. **Base Paths por Serviço:**
   - `ledger-core-api` (`:8080`): `/api/v1/...`
   - `risk-engine-ai` (`:8001`): `/v1/...` (além de `/health` e `/metrics`)
   - `webhook-dispatcher` (`:8082`): `/api/v1/...`
2. **Precisão Monetária:**
   - Todos os valores financeiros usam **centavos inteiros** (`BIGINT` no PostgreSQL, `long` em Java, `int` em Python, `number` inteiro em TypeScript). **É estritamente proibido usar `float` ou `double` para dinheiro.**
   - Moeda em código ISO-4217 (`currency`: `"BRL"` por padrão).
   - Taxas percentuais e percentuais de split em *basis points* inteiros (`defaultFeeBps`, `percentageBps`, ex.: `250` = `2.50%`, `2000` = `20.00%`).
3. **Convenções de Naming e Transporte (Congeladas):**
   - **Serviços Java (`ledger-core-api` e `webhook-dispatcher`):** todos os payloads JSON usam **`camelCase`** (`amountCents`, `platformFeeCents`, `accessToken`, `totalElements`).
   - **Serviço Python (`risk-engine-ai`):** o contrato de avaliação `POST /v1/risk/evaluate` usa **`snake_case`** (`payment_id`, `merchant_id`, `payer_id`, `amount_cents`, `payment_method`, `ip_country`, `velocity_1h`, `hour_of_day`, `account_age_days`, `risk_score`, `reason_codes`, `model_version`), com mapeamento explícito no cliente HTTP do `ledger-core-api`.
   - **Replay de DLQ (`webhook-dispatcher`):** o identificador de rota oficial é **`{deliveryId}`** (`POST /api/v1/webhooks/dlq/{deliveryId}/replay`).
   - **Stream SSE (`GET /api/v1/stream/events`):** o `web-console` deve consumir o stream via `fetch()` + `ReadableStream` enviando o header `Authorization: Bearer <jwt>` (pois a API `EventSource` nativa do browser não suporta headers de autenticação customizados).
4. **Datas e Identificadores:**
   - IDs de negócio em `UUID` (`UUID v4` ou `UUID v7` ordenável).
   - Timestamps em ISO-8601 UTC (`2026-11-20T22:30:00Z`).
5. **Headers Padrão:**
   - `Authorization: Bearer <jwt>` — obrigatório para endpoints do Console Web / Operadores (`ADMIN`, `ANALYST`, `VIEWER`).
   - `X-API-Key: <merchant_api_key>` — obrigatório para integrações S2S de merchants (`GET /api/v1/merchants/me`, `POST /api/v1/payments`, `POST /api/v1/payments/{id}/refunds`).
   - `Idempotency-Key: <uuid_ou_chave_unica>` — **obrigatório** em `POST /api/v1/payments` e `POST /api/v1/payments/{id}/refunds`.
   - `X-Correlation-Id: <uuid>` — opcional na entrada (gerado automaticamente pelo filtro HTTP se ausente), sempre devolvido no header da resposta e propagado nos logs, banco, cliente Python, RabbitMQ e SSE.
   - `X-Idempotent-Replay: true` — header informativo devolvido quando `POST /api/v1/payments` retorna resposta cacheada de replay idêntico.
   - `X-LedgerPulse-Signature: t=<unix_epoch>,v1=<hmac_sha256_hex>` — enviado pelo `webhook-dispatcher` em toda entrega HTTP de webhook.
6. **Wrapper Padrão de Paginação:**
   - Todos os endpoints paginados (`GET /api/v1/accounts`, `GET /api/v1/ledger/transactions`, `GET /api/v1/payments`, etc.) aceitam `?page=0&size=20` (com `size` máximo `100`) e devolvem exatamente este envelope:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

---

## 2. Catálogo Padrão de Erros — RFC 7807 `ProblemDetail` (`application/problem+json`)

Todas as respostas `4xx` e `5xx` dos serviços Java devem retornar `Content-Type: application/problem+json` seguindo a estrutura abaixo (a propriedade `errors` é incluída quando houver erros de validação por campo):

| Código Slug (`type`) | URI Oficial `type` | HTTP Status | `title` Padrão | Quando Ocorre |
| :--- | :--- | :---: | :--- | :--- |
| `validation-error` | `https://ledgerpulse.dev/errors/validation-error` | `400` | `Validation Error` | Bean Validation falhou, JSON inválido, header obrigatório ausente (`Idempotency-Key`) ou regra de domínio de entrada inválida (ex.: soma de splits > valor bruto). |
| `invalid-credentials` | `https://ledgerpulse.dev/errors/invalid-credentials` | `401` | `Invalid Credentials` | Email ou senha incorretos no `POST /api/v1/auth/login`. |
| `unauthorized` | `https://ledgerpulse.dev/errors/unauthorized` | `401` | `Unauthorized` | JWT ausente/expirado/inválido ou `X-API-Key` ausente/inválida. |
| `forbidden` | `https://ledgerpulse.dev/errors/forbidden` | `403` | `Forbidden` | Usuário autenticado sem a role exigida (ex.: `ANALYST`/`VIEWER` em rota exclusiva `ADMIN`) ou acesso a recurso de outro merchant. |
| `resource-not-found` | `https://ledgerpulse.dev/errors/resource-not-found` | `404` | `Resource Not Found` | Entidade (`account`, `payment`, `ledgerTransaction`, `merchant`) não encontrada. |
| `idempotency-conflict` | `https://ledgerpulse.dev/errors/idempotency-conflict` | `409` | `Idempotency Key Conflict` | **Decisão congelada (`409` em vez de `422`):** mesma `Idempotency-Key` reutilizada com payload divergente (ou requisição concorrente ainda em processamento). |
| `optimistic-lock-conflict` | `https://ledgerpulse.dev/errors/optimistic-lock-conflict` | `409` | `Optimistic Lock Conflict` | Colisão de `@Version` em atualização concorrente de conta ou pagamento. |
| `rate-limit-exceeded` | `https://ledgerpulse.dev/errors/rate-limit-exceeded` | `429` | `Rate Limit Exceeded` | Limite do Token Bucket no Redis excedido para a `X-API-Key`. |
| `internal-error` | `https://ledgerpulse.dev/errors/internal-error` | `500` | `Internal Server Error` | Falha inesperada não tratada (sem vazar stacktrace ou segredos ao cliente). |

### Exemplo Canônico (`400 validation-error`)

```json
{
  "type": "https://ledgerpulse.dev/errors/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Um ou mais campos da requisição são inválidos.",
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

### Exemplo Canônico (`409 idempotency-conflict`)

```json
{
  "type": "https://ledgerpulse.dev/errors/idempotency-conflict",
  "title": "Idempotency Key Conflict",
  "status": 409,
  "detail": "Idempotency-Key já utilizada anteriormente com payload divergente.",
  "instance": "/api/v1/payments",
  "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
  "timestamp": "2026-11-20T22:30:00Z"
}
```

---

## 3. Mapa de Endpoints — `ledger-core-api` (`:8080`)

| Domínio | Semana | Método & Rota | Auth / Role | Resumo |
| :--- | :---: | :--- | :--- | :--- |
| **Health** | `S1` | `GET /api/v1/health` | Público | Status operacional e versão da aplicação. |
| **Auth** | `S1` | `POST /api/v1/auth/login` | Público | Autentica operador e emite JWT + perfil (`ADMIN`, `ANALYST`, `VIEWER`). |
| **Auth** | `S1` | `GET /api/v1/auth/me` | JWT | Retorna dados e papel do operador autenticado. |
| **Admin** | `S1` | `GET /api/v1/admin/status` | JWT (`ADMIN`) | Endpoint exclusivo de verificação RBAC `ADMIN` (`403` para `ANALYST`/`VIEWER`). |
| **Merchants** | `S1` | `GET /api/v1/merchants/me` | `X-API-Key` | Retorna dados do merchant autenticado via API Key. |
| **Accounts** | `S2` | `POST /api/v1/accounts` | JWT (`ADMIN`) | Cria conta (`MERCHANT`, `PLATFORM_FEE`, `ESCROW`, `RECIPIENT`) com saldo inicial `0`. |
| **Accounts** | `S2` | `GET /api/v1/accounts` | JWT | Lista contas paginadas com saldos em centavos (`availableBalanceCents`, `pendingBalanceCents`). |
| **Accounts** | `S2` | `GET /api/v1/accounts/{id}` | JWT | Detalhes da conta e `version` de concorrência otimista. |
| **Ledger** | `S2` | `GET /api/v1/ledger/transactions` | JWT | Lista transações contábeis paginadas com filtros. |
| **Ledger** | `S2` | `GET /api/v1/ledger/transactions/{id}` | JWT | Detalha todas as `ledger_entries` (`DEBIT`/`CREDIT`) provando balanço zerado. |
| **Payments** | `S2` | `POST /api/v1/payments` | `X-API-Key` ou JWT + `Idempotency-Key` | Cria cobrança, calcula risco, executa Split + Ledger + Outbox. |
| **Payments** | `S2` | `GET /api/v1/payments` | JWT | Lista pagamentos paginados com filtros por `status`, `merchantId` e período. |
| **Payments** | `S2` | `GET /api/v1/payments/{id}` | JWT ou `X-API-Key` | Consulta pagamento, split calculado, score de risco e `reasonCodes`. |
| **Risk Review** | `S3` | `GET /api/v1/risk/reviews` | JWT (`ANALYST`/`ADMIN`) | Fila paginada de pagamentos em `MANUAL_REVIEW`. |
| **Risk Review** | `S3` | `POST /api/v1/risk/reviews/{paymentId}/decision` | JWT (`ANALYST`/`ADMIN`) | Analista decide `APPROVE` ou `DECLINE` com justificativa. |
| **Refunds** | `S5` | `POST /api/v1/payments/{id}/refunds` | `X-API-Key` ou JWT (`ADMIN`/`ANALYST`) + `Idempotency-Key` | Estorno total ou parcial com lançamentos compensatórios imutáveis. |
| **Reconciliation** | `S5` | `POST /api/v1/reconciliation/run` | JWT (`ADMIN`) | Dispara conciliação contábil sob demanda. |
| **Reconciliation** | `S5` | `GET /api/v1/reconciliation/reports` | JWT | Lista relatórios periódicos de conciliação. |
| **Reconciliation** | `S5` | `GET /api/v1/reconciliation/export.csv` | JWT | Exporta relatório de conciliação em CSV. |
| **SSE Stream** | `S5` | `GET /api/v1/stream/events` | JWT (via `fetch` `ReadableStream`) | Stream `text/event-stream` em tempo real para o Dashboard Executivo. |

---

## 4. Payloads JSON Congelados — Semana 1 e Semana 2 (`ledger-core-api`)

> Os contratos detalhados abaixo correspondem ao arquivo OpenAPI [openapi-ledger-core-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-ledger-core-v1.yaml).

### 4.1 `GET /api/v1/health` (Público)

**Response `200 OK`:**
```json
{
  "status": "UP",
  "service": "ledger-core-api",
  "version": "0.1.0",
  "timestamp": "2026-11-20T22:30:00Z"
}
```

### 4.2 `POST /api/v1/auth/login` (Público)

**Request:**
```json
{
  "email": "admin@ledgerpulse.dev",
  "password": "LedgerPulse@2026"
}
```

**Response `200 OK`:**
```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": {
    "id": "11111111-1111-4111-8111-111111111111",
    "name": "LedgerPulse Admin",
    "email": "admin@ledgerpulse.dev",
    "role": "ADMIN"
  }
}
```

**Response `401 Unauthorized` (`application/problem+json`):**
```json
{
  "type": "https://ledgerpulse.dev/errors/invalid-credentials",
  "title": "Invalid Credentials",
  "status": 401,
  "detail": "Credenciais inválidas para o email informado.",
  "instance": "/api/v1/auth/login",
  "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
  "timestamp": "2026-11-20T22:30:00Z"
}
```

### 4.3 `GET /api/v1/auth/me` (Header `Authorization: Bearer <jwt>`)

**Response `200 OK`:**
```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "name": "LedgerPulse Admin",
  "email": "admin@ledgerpulse.dev",
  "role": "ADMIN"
}
```

### 4.4 `GET /api/v1/admin/status` (Header `Authorization: Bearer <jwt>`, role `ADMIN` exclusiva)

- Sem token ou token inválido → `401 Unauthorized` (`unauthorized`)
- Token válido com role `ANALYST` ou `VIEWER` → `403 Forbidden` (`forbidden`)

**Response `200 OK`:**
```json
{
  "status": "OK",
  "scope": "ADMIN",
  "checkedAt": "2026-11-20T22:30:00Z"
}
```

### 4.5 `GET /api/v1/merchants/me` (Header `X-API-Key: <merchant_api_key>`)

- Sem `X-API-Key` ou chave desconhecida/suspensa → `401 Unauthorized` (`unauthorized`)

**Response `200 OK`:**
```json
{
  "id": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "name": "Acme Fintech Store",
  "defaultFeeBps": 250,
  "status": "ACTIVE"
}
```

### 4.6 `POST /api/v1/accounts` (JWT `ADMIN`)

**Request:**
> `merchantId` é obrigatório para `MERCHANT` e `RECIPIENT`, e opcional (`null`) para contas sistêmicas globais (`PLATFORM_FEE`, `ESCROW`).

```json
{
  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "accountType": "RECIPIENT",
  "holderName": "Partner Seller Alpha",
  "currency": "BRL"
}
```

**Response `201 Created`:**
```json
{
  "id": "22222222-2222-4222-8222-222222222222",
  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "accountType": "RECIPIENT",
  "holderName": "Partner Seller Alpha",
  "currency": "BRL",
  "availableBalanceCents": 0,
  "pendingBalanceCents": 0,
  "status": "ACTIVE",
  "version": 0,
  "createdAt": "2026-11-20T22:30:00Z",
  "updatedAt": "2026-11-20T22:30:00Z"
}
```

### 4.7 `GET /api/v1/accounts` e `GET /api/v1/accounts/{id}` (JWT)

**Query params opcionais (`GET /api/v1/accounts`):** `page` (`0`), `size` (`20`), `accountType`, `merchantId`.

**Response `200 OK` (`GET /api/v1/accounts`):**
```json
{
  "items": [
    {
      "id": "22222222-2222-4222-8222-222222222222",
      "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
      "accountType": "RECIPIENT",
      "holderName": "Partner Seller Alpha",
      "currency": "BRL",
      "availableBalanceCents": 3000,
      "pendingBalanceCents": 0,
      "status": "ACTIVE",
      "version": 1,
      "createdAt": "2026-11-20T22:30:00Z",
      "updatedAt": "2026-11-20T22:31:05Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**Response `200 OK` (`GET /api/v1/accounts/{id}`):** retorna o mesmo objeto de conta acima (ou `404 resource-not-found`).

### 4.8 `GET /api/v1/ledger/transactions` e `GET /api/v1/ledger/transactions/{id}` (JWT)

**Query params opcionais (`GET /api/v1/ledger/transactions`):** `page` (`0`), `size` (`20`), `accountId`, `referenceType`, `referenceId`, `from`, `to`.

**Response `200 OK` (`GET /api/v1/ledger/transactions`):**
```json
{
  "items": [
    {
      "id": "f9e8d7c6-5b4a-4321-8765-112233445566",
      "referenceType": "PAYMENT_SETTLEMENT",
      "referenceId": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
      "description": "Liquidação de pagamento com split de marketplace",
      "currency": "BRL",
      "totalDebitCents": 15000,
      "totalCreditCents": 15000,
      "balanced": true,
      "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
      "createdAt": "2026-11-20T22:31:05Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**Response `200 OK` (`GET /api/v1/ledger/transactions/{id}`):**
```json
{
  "id": "f9e8d7c6-5b4a-4321-8765-112233445566",
  "referenceType": "PAYMENT_SETTLEMENT",
  "referenceId": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
  "description": "Liquidação de pagamento com split de marketplace",
  "currency": "BRL",
  "totalDebitCents": 15000,
  "totalCreditCents": 15000,
  "balanced": true,
  "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
  "createdAt": "2026-11-20T22:31:05Z",
  "entries": [
    {
      "id": "a1111111-1111-4111-8111-111111111111",
      "accountId": "00000000-0000-4000-8000-000000000001",
      "accountType": "ESCROW",
      "direction": "DEBIT",
      "amountCents": 15000,
      "balanceAfterCents": -15000,
      "createdAt": "2026-11-20T22:31:05Z"
    },
    {
      "id": "a2222222-2222-4222-8222-222222222222",
      "accountId": "00000000-0000-4000-8000-000000000002",
      "accountType": "PLATFORM_FEE",
      "direction": "CREDIT",
      "amountCents": 375,
      "balanceAfterCents": 375,
      "createdAt": "2026-11-20T22:31:05Z"
    },
    {
      "id": "a3333333-3333-4333-8333-333333333333",
      "accountId": "7b92c3e0-1f42-4876-a1e2-901283471234",
      "accountType": "MERCHANT",
      "direction": "CREDIT",
      "amountCents": 11625,
      "balanceAfterCents": 11625,
      "createdAt": "2026-11-20T22:31:05Z"
    },
    {
      "id": "a4444444-4444-4444-8444-444444444444",
      "accountId": "22222222-2222-4222-8222-222222222222",
      "accountType": "RECIPIENT",
      "direction": "CREDIT",
      "amountCents": 3000,
      "balanceAfterCents": 3000,
      "createdAt": "2026-11-20T22:31:05Z"
    }
  ]
}
```

### 4.9 `POST /api/v1/payments`, `GET /api/v1/payments` e `GET /api/v1/payments/{id}`

> **Nota de Transição Semana 2 → Semana 3:** Durante a Semana 2 (Dias 11–14), antes da subida do `risk-engine-ai` na Semana 3 (Dias 15–18), o `ledger-core-api` opera com um avaliador de risco *stub* atrás da interface de domínio retornando `decision = "APPROVE"`, `riskScore = 0` e `reasonCodes = []`.

**Request (`POST /api/v1/payments` — Header obrigatório `Idempotency-Key`):**
> Quando autenticado via `X-API-Key`, o `merchantId` é resolvido pela chave (e se enviado no body, deve coincidir com o merchant autenticado). `splits` aceita `splitType: "PERCENTAGE"` (`percentageBps` obrigatório) ou `"FIXED"` (`amountCents` obrigatório); se omitido ou vazio, 100% do líquido após `platformFeeCents` vai para a conta `MERCHANT`.

```json
{
  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "payerId": "payer-98765",
  "amountCents": 15000,
  "currency": "BRL",
  "paymentMethod": "CARD",
  "description": "Pedido #4021",
  "splits": [
    {
      "recipientAccountId": "22222222-2222-4222-8222-222222222222",
      "splitType": "PERCENTAGE",
      "percentageBps": 2000
    }
  ]
}
```

**Response `201 Created` (`POST /api/v1/payments`) / `200 OK` (`GET /api/v1/payments/{id}`):**
```json
{
  "id": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "payerId": "payer-98765",
  "amountCents": 15000,
  "platformFeeCents": 375,
  "netMerchantCents": 11625,
  "currency": "BRL",
  "paymentMethod": "CARD",
  "description": "Pedido #4021",
  "status": "SETTLED",
  "riskScore": 14,
  "riskDecision": "APPROVE",
  "reasonCodes": [],
  "ledgerTransactionId": "f9e8d7c6-5b4a-4321-8765-112233445566",
  "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
  "splits": [
    {
      "recipientAccountId": "22222222-2222-4222-8222-222222222222",
      "splitType": "PERCENTAGE",
      "percentageBps": 2000,
      "amountCents": 3000
    }
  ],
  "createdAt": "2026-11-20T22:31:05Z",
  "updatedAt": "2026-11-20T22:31:05Z"
}
```

**Response `200 OK` (`GET /api/v1/payments` — query params `page`, `size`, `status`, `merchantId`, `from`, `to`):**
```json
{
  "items": [
    {
      "id": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
      "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
      "payerId": "payer-98765",
      "amountCents": 15000,
      "platformFeeCents": 375,
      "netMerchantCents": 11625,
      "currency": "BRL",
      "paymentMethod": "CARD",
      "description": "Pedido #4021",
      "status": "SETTLED",
      "riskScore": 14,
      "riskDecision": "APPROVE",
      "reasonCodes": [],
      "ledgerTransactionId": "f9e8d7c6-5b4a-4321-8765-112233445566",
      "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
      "splits": [
        {
          "recipientAccountId": "22222222-2222-4222-8222-222222222222",
          "splitType": "PERCENTAGE",
          "percentageBps": 2000,
          "amountCents": 3000
        }
      ],
      "createdAt": "2026-11-20T22:31:05Z",
      "updatedAt": "2026-11-20T22:31:05Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 5. Endpoints Principais — `risk-engine-ai` (`:8001`) — Semana 3

> **Regra de Naming:** o contrato de avaliação `POST /v1/risk/evaluate` utiliza **`snake_case`** (padrão Python / Pydantic v2).

| Método & Rota | Resumo | Request / Response Principal |
| :--- | :--- | :--- |
| `GET /health` | Healthcheck do serviço FastAPI e status do modelo `.joblib` carregado. | `{ "status": "ok", "modelLoaded": true, "modelVersion": "v1.0.0" }` |
| `GET /v1/risk/model-info` | Metadados do modelo treinado (algoritmo, features, métricas de treino). | `{ "algorithm": "Hybrid(IsolationForest+RandomForest+Rules)", "features": [...] }` |
| `POST /v1/risk/evaluate` | Avalia risco de pagamento antes da liquidação (`snake_case`). | Entrada: `payment_id`, `merchant_id`, `payer_id`, `amount_cents`, `payment_method`, `ip_country`, `velocity_1h`, `hour_of_day`, `account_age_days`. Saída: `risk_score` (`0..100`), `decision` (`APPROVE`/`REVIEW`/`BLOCK`), `reason_codes` (`list[str]`), `model_version`. |
| `GET /metrics` | Métricas Prometheus do serviço Python. | Formato OpenMetrics / Prometheus text. |

---

## 6. Endpoints Principais — `webhook-dispatcher` (`:8082`) — Semana 4

> **Regra de Naming:** todos os JSONs em **`camelCase`** e parâmetro de rota oficial **`{deliveryId}`** para replay de DLQ.

| Método & Rota | Auth / Role | Resumo |
| :--- | :--- | :--- |
| `POST /api/v1/webhooks/endpoints` | JWT (`ADMIN`) | Cadastra URL de destino e *secret* HMAC-SHA256 de um merchant. |
| `GET /api/v1/webhooks/endpoints` | JWT | Lista endpoints cadastrados e status (`ACTIVE`/`PAUSED`). |
| `GET /api/v1/webhooks/deliveries` | JWT | Lista histórico paginado de tentativas de entrega (`PENDING`, `DELIVERED`, `RETRYING`, `DLQ`), latência e código HTTP. |
| `POST /api/v1/webhooks/dlq/{deliveryId}/replay` | JWT (`ADMIN`/`ANALYST`) | Reenfileira manualmente uma entrega que caiu em DLQ para nova tentativa imediata. |
| `POST /api/v1/sink/test` | Público / Dev | Simulador local (**Merchant Webhook Sink**) configurável (`OK_200`, `FAIL_500`, `TIMEOUT`) para demonstrar retries e DLQ sem depender de serviço externo. |
| `PUT /api/v1/sink/mode` | JWT / Dev | Alterna o comportamento do Webhook Sink (`OK_200`, `FAIL_500`, `TIMEOUT`). |
