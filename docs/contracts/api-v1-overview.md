# 🌐 Contratos REST v1 (`api-v1-overview.md`)

> **Versão do Contrato:** `v1.0-frozen` (Semanas 1, 2, 3 e 4 congeladas)  
> **Especificação OpenAPI (`ledger-core-api`):** [openapi-ledger-core-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-ledger-core-v1.yaml)  
> **Especificação OpenAPI (`risk-engine-ai`):** [openapi-risk-engine-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-risk-engine-v1.yaml)  
> **Especificação OpenAPI (`webhook-dispatcher`):** [openapi-webhook-dispatcher-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-webhook-dispatcher-v1.yaml)  
> **Fixture de Contrato Cruzado (`risk-engine-ai`):** [risk-evaluate-contract.json](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/contract-samples/risk-evaluate-contract.json)  
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
| `resource-not-found` | `https://ledgerpulse.dev/errors/resource-not-found` | `404` | `Resource Not Found` | Entidade (`account`, `payment`, `ledgerTransaction`, `merchant`, `webhookEndpoint`, `webhookDelivery`) não encontrada. |
| `idempotency-conflict` | `https://ledgerpulse.dev/errors/idempotency-conflict` | `409` | `Idempotency Key Conflict` | **Decisão congelada (`409` em vez de `422`):** mesma `Idempotency-Key` reutilizada com payload divergente (ou requisição concorrente ainda em processamento). |
| `invalid-payment-state` | `https://ledgerpulse.dev/errors/invalid-payment-state` | `409` | `Invalid Payment State` | Transição de estado inválida ou tentativa de decisão manual quando o pagamento não se encontra em `MANUAL_REVIEW`. |
| `invalid-delivery-state` | `https://ledgerpulse.dev/errors/invalid-delivery-state` | `409` | `Invalid Delivery State` | Tentativa de replay manual na DLQ quando a entrega não se encontra no estado `DLQ`. |
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

> **Nota de Transição Semana 2 → Semana 3:** Durante a Semana 2 (Dias 11–14), o `ledger-core-api` operou com um avaliador *stub* retornando `APPROVE`. A partir da **Semana 3 (Dia 18)**, `POST /api/v1/payments` chama `risk-engine-ai` (`POST /v1/risk/evaluate`) protegido por **Resilience4j** (Timeout `1.5s`, Retry curto e Circuit Breaker), enviando o pagamento para `MANUAL_REVIEW` quando `decision = "REVIEW"` ou via fallback (`RISK_ENGINE_UNAVAILABLE`), e para `DECLINED` quando `decision = "BLOCK"`.

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

### 4.10 `GET /api/v1/risk/reviews` (JWT `ANALYST` ou `ADMIN`; `VIEWER` → `403 Forbidden`)

**Query params opcionais:** `page` (`0`), `size` (`20`), `merchantId` (`UUID`), `minRiskScore` (`0..100`), `from` (`ISO-8601 UTC`), `to` (`ISO-8601 UTC`).

**Response `200 OK`:**
```json
{
  "items": [
    {
      "paymentId": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
      "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
      "payerId": "payer-98765",
      "amountCents": 185000,
      "platformFeeCents": 4625,
      "netMerchantCents": 180375,
      "currency": "BRL",
      "paymentMethod": "CARD",
      "description": "Compra de alto valor fora do horário padrão",
      "status": "MANUAL_REVIEW",
      "riskScore": 58,
      "riskDecision": "REVIEW",
      "reasonCodes": [
        "NIGHT_ANOMALY",
        "ML_ANOMALY_DETECTED"
      ],
      "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
      "createdAt": "2026-11-22T02:15:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 4.11 `POST /api/v1/risk/reviews/{paymentId}/decision` (JWT `ANALYST` ou `ADMIN`; `VIEWER` → `403 Forbidden`)

- Caso o pagamento não exista → `404 Not Found` (`resource-not-found`).
- Caso o pagamento não esteja em `MANUAL_REVIEW` → `409 Conflict` (`invalid-payment-state`).
- Caso `justification` tenha menos de `5` ou mais de `500` caracteres → `400 Bad Request` (`validation-error`).

**Request:**
```json
{
  "decision": "APPROVE",
  "justification": "Titular confirmou a operação via contato telefônico antifraude."
}
```

**Response `200 OK` (quando `decision = "APPROVE"` → `paymentStatus = "SETTLED"`):**
```json
{
  "decisionId": "d1a2b3c4-1111-4222-8333-444455556666",
  "paymentId": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
  "decision": "APPROVE",
  "paymentStatus": "SETTLED",
  "ledgerTransactionId": "f9e8d7c6-5b4a-4321-8765-112233445566",
  "justification": "Titular confirmou a operação via contato telefônico antifraude.",
  "reviewedByUserId": "11111111-1111-4111-8111-111111111112",
  "reviewedByEmail": "analyst@ledgerpulse.dev",
  "reviewedAt": "2026-11-22T12:45:00Z"
}
```

> Quando `decision = "DECLINE"`, `paymentStatus` é `"DECLINED"` e `ledgerTransactionId` é `null`.

---

## 5. Contratos JSON Congelados — `risk-engine-ai` (`:8001`) — Semana 3

> **Especificação OpenAPI (`risk-engine-ai`):** [openapi-risk-engine-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-risk-engine-v1.yaml)  
> **Fixture de Contrato Cruzado:** [risk-evaluate-contract.json](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/contract-samples/risk-evaluate-contract.json)  
> **Regra de Naming:** o contrato `POST /v1/risk/evaluate` utiliza **`snake_case`** estrito (Pydantic v2), com valores monetários sempre em centavos inteiros (`amount_cents`).

### 5.1 Vocabulário Oficial de `reason_codes` (Congelado)

| Código | Emissor | Semântica |
| :--- | :--- | :--- |
| `VELOCITY_SPIKE` | `risk-engine-ai` | Elevado volume de transações em janela de 1 hora (`velocity_1h`). |
| `HIGH_TICKET_NEW_ACCOUNT` | `risk-engine-ai` | Valor elevado (`amount_cents`) em conta de pagador recém-criada (`account_age_days`). |
| `NIGHT_ANOMALY` | `risk-engine-ai` | Transação realizada em faixa horária atípica de madrugada (`hour_of_day`). |
| `FOREIGN_IP_HIGH_AMOUNT` | `risk-engine-ai` | IP fora do país padrão (`ip_country != "BR"`) combinado com ticket alto. |
| `ML_ANOMALY_DETECTED` | `risk-engine-ai` | `IsolationForest` classificou o vetor de features como anomalia estatística. |
| `ML_HIGH_FRAUD_PROBABILITY` | `risk-engine-ai` | `RandomForestClassifier` apontou alta probabilidade de fraude. |
| `RISK_ENGINE_UNAVAILABLE` | `ledger-core-api` (Fallback) | Emitido pelo fallback Resilience4j quando `risk-engine-ai` está indisponível/timeout (força `MANUAL_REVIEW`). |

### 5.2 Mapa e Payloads Exatos — `risk-engine-ai`

| Método & Rota | Resumo | Status |
| :--- | :--- | :---: |
| `GET /health` | Healthcheck do serviço FastAPI e status do modelo `.joblib`. | `200 OK` |
| `GET /v1/risk/model-info` | Metadados do modelo treinado (algoritmo, features, métricas de treino). | `200 OK` |
| `POST /v1/risk/evaluate` | Avalia risco híbrido (ML + Regras determinísticas) em `snake_case`. | `200 OK` |
| `GET /metrics` | Métricas Prometheus/OpenMetrics do serviço Python. | `200 OK` |

#### `GET /health` (`200 OK`)
```json
{
  "status": "ok",
  "modelLoaded": true,
  "modelVersion": "v1.0.0"
}
```

#### `GET /v1/risk/model-info` (`200 OK`)
```json
{
  "modelVersion": "v1.0.0",
  "algorithm": "Hybrid(IsolationForest+RandomForest+Rules)",
  "features": [
    "amount_cents",
    "velocity_1h",
    "hour_of_day",
    "account_age_days",
    "payment_method_code",
    "is_foreign_ip"
  ],
  "metrics": {
    "precision": 0.91,
    "recall": 0.89,
    "f1Score": 0.90,
    "rocAuc": 0.96
  },
  "trainedAt": "2026-11-22T12:00:00Z"
}
```

#### `POST /v1/risk/evaluate` (`snake_case` estrito)

**Request:**
```json
{
  "payment_id": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
  "merchant_id": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "payer_id": "payer-98765",
  "amount_cents": 15000,
  "payment_method": "CARD",
  "ip_country": "BR",
  "velocity_1h": 1,
  "hour_of_day": 14,
  "account_age_days": 180
}
```

**Regras de Validação de Entrada (`POST /v1/risk/evaluate`):**
- `payment_id`, `merchant_id`: UUID string obrigatório.
- `payer_id`: string não vazia.
- `amount_cents`: inteiro estritamente positivo (`> 0`).
- `payment_method`: `"CARD"` | `"PIX"` | `"BOLETO"`.
- `ip_country`: string ISO-3166-1 alpha-2 (`2` letras maiúsculas, ex.: `"BR"`).
- `velocity_1h`: inteiro `>= 0`.
- `hour_of_day`: inteiro `0..23`.
- `account_age_days`: inteiro `>= 0`.

**Response `200 OK`:**
- `risk_score`: inteiro `0..100`
- `decision`: `"APPROVE"` | `"REVIEW"` | `"BLOCK"`
- `reason_codes`: lista de strings do vocabulário oficial
- `model_version`: string

```json
{
  "risk_score": 58,
  "decision": "REVIEW",
  "reason_codes": [
    "NIGHT_ANOMALY",
    "ML_ANOMALY_DETECTED"
  ],
  "model_version": "v1.0.0"
}
```

---

## 6. Contratos JSON Congelados — `webhook-dispatcher` (`:8082`) — Semana 4

> **Especificação OpenAPI (`webhook-dispatcher`):** [openapi-webhook-dispatcher-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-webhook-dispatcher-v1.yaml)  
> **Regra de Naming e Segurança:** todos os JSONs em **`camelCase`**, erros em RFC 7807 `ProblemDetail` (`application/problem+json`), parâmetro de rota oficial **`{deliveryId}`** para replay de DLQ e **proibição estrita de expor o `secret` em texto puro** nas respostas REST (expor apenas `secretPreview` mascarado).

### 6.1 Mapa de Endpoints — `webhook-dispatcher`

| Método & Rota | Auth / Role | Resumo |
| :--- | :--- | :--- |
| `GET /api/v1/health` | Público | Status operacional, versão e indicação de Virtual Threads habilitadas. |
| `POST /api/v1/webhooks/endpoints` | JWT (`ADMIN`) | Cadastra URL de destino e *secret* HMAC-SHA256 de um merchant (`201 Created`). |
| `GET /api/v1/webhooks/endpoints` | JWT | Lista endpoints cadastrados paginados (`PageResponse`), com filtros opcionais `merchantId` e `status`. |
| `GET /api/v1/webhooks/deliveries` | JWT | Lista entregas paginadas (`PageResponse`) com histórico detalhado de `attempts`, latência, status HTTP e assinatura HMAC. |
| `POST /api/v1/webhooks/dlq/{deliveryId}/replay` | JWT (`ADMIN`/`ANALYST`) | Reenfileira/executa replay imediato de uma entrega em estado `DLQ` (`409 invalid-delivery-state` caso não esteja em `DLQ`, `404` se inexistente). |
| `GET /api/v1/sink/mode` | JWT | Consulta o modo atual do simulador **Merchant Webhook Sink**. |
| `PUT /api/v1/sink/mode` | JWT (`ADMIN`/`ANALYST`) | Alterna o comportamento do Webhook Sink (`OK_200`, `FAIL_500`, `TIMEOUT`). |
| `POST /api/v1/sink/test` | Público / Dev | Simulador local (**Merchant Webhook Sink**) que recebe webhooks assinados com `X-LedgerPulse-Signature`, `X-LedgerPulse-Event-Id` e `X-Correlation-Id`. |

### 6.2 `GET /api/v1/health` (Público)

**Response `200 OK`:**
```json
{
  "status": "UP",
  "service": "webhook-dispatcher",
  "version": "0.1.0",
  "virtualThreadsEnabled": true,
  "timestamp": "2026-11-23T12:00:00Z"
}
```

### 6.3 `POST /api/v1/webhooks/endpoints` (JWT `ADMIN`) e `GET /api/v1/webhooks/endpoints` (JWT)

- Token com role `ANALYST` ou `VIEWER` no `POST` → `403 Forbidden` (`forbidden`).
- **Nunca** retornar `secret` em texto puro na resposta (retornar apenas `secretPreview`, ex.: `"whsec_****2026"`).

**Request (`POST /api/v1/webhooks/endpoints`):**
```json
{
  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "targetUrl": "http://localhost:8082/api/v1/sink/test",
  "secret": "whsec_ledgerpulse_dev_secret_2026",
  "description": "Merchant Webhook Sink Local"
}
```

**Response `201 Created` (`POST /api/v1/webhooks/endpoints`):**
```json
{
  "id": "33333333-3333-4333-8333-333333333333",
  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
  "targetUrl": "http://localhost:8082/api/v1/sink/test",
  "secretPreview": "whsec_****2026",
  "description": "Merchant Webhook Sink Local",
  "status": "ACTIVE",
  "createdAt": "2026-11-23T12:00:00Z",
  "updatedAt": "2026-11-23T12:00:00Z"
}
```

**Query params opcionais (`GET /api/v1/webhooks/endpoints`):** `page` (`0`), `size` (`20`), `merchantId` (`UUID`), `status` (`ACTIVE` | `PAUSED`).

**Response `200 OK` (`GET /api/v1/webhooks/endpoints`):**
```json
{
  "items": [
    {
      "id": "33333333-3333-4333-8333-333333333333",
      "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
      "targetUrl": "http://localhost:8082/api/v1/sink/test",
      "secretPreview": "whsec_****2026",
      "description": "Merchant Webhook Sink Local",
      "status": "ACTIVE",
      "createdAt": "2026-11-23T12:00:00Z",
      "updatedAt": "2026-11-23T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 6.4 `GET /api/v1/webhooks/deliveries` (JWT)

**Query params opcionais:** `page` (`0`), `size` (`20`), `status` (`PENDING` | `RETRYING` | `DELIVERED` | `DLQ`), `merchantId` (`UUID`), `eventType` (`string`), `eventId` (`UUID`).

**Response `200 OK`:**
```json
{
  "items": [
    {
      "id": "44444444-4444-4444-8444-444444444444",
      "eventId": "c7a615f1-82b3-4e11-9c2a-5d2d1b63e701",
      "eventType": "payment.settled",
      "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
      "endpointId": "33333333-3333-4333-8333-333333333333",
      "targetUrl": "http://localhost:8082/api/v1/sink/test",
      "correlationId": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab",
      "status": "DLQ",
      "attemptCount": 4,
      "maxAttempts": 4,
      "lastHttpStatus": 500,
      "lastLatencyMs": 18,
      "lastError": "HTTP 500 Internal Server Error from target endpoint",
      "signatureHeader": "t=1763900400,v1=3b8f6e1c2a9d4e7f0123456789abcdef0123456789abcdef0123456789abcdef",
      "createdAt": "2026-11-23T12:00:00Z",
      "updatedAt": "2026-11-23T12:02:35Z",
      "deliveredAt": null,
      "attempts": [
        {
          "attemptNumber": 1,
          "httpStatus": 500,
          "latencyMs": 14,
          "signatureHeader": "t=1763900245,v1=1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809",
          "errorMessage": "HTTP 500 Internal Server Error from target endpoint",
          "attemptedAt": "2026-11-23T12:00:00Z"
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 6.5 `POST /api/v1/webhooks/dlq/{deliveryId}/replay` (JWT `ADMIN` ou `ANALYST`; `VIEWER` → `403 Forbidden`)

- Se `deliveryId` não existir → `404 Not Found` (`resource-not-found`).
- Se a entrega não estiver em `DLQ` → `409 Conflict` (`invalid-delivery-state`).

**Response `200 OK`:**
```json
{
  "deliveryId": "44444444-4444-4444-8444-444444444444",
  "eventId": "c7a615f1-82b3-4e11-9c2a-5d2d1b63e701",
  "status": "DELIVERED",
  "replayedAt": "2026-11-23T12:05:00Z",
  "httpStatus": 200,
  "latencyMs": 11,
  "errorMessage": null
}
```

### 6.6 `GET /api/v1/sink/mode`, `PUT /api/v1/sink/mode` e `POST /api/v1/sink/test`

#### `GET /api/v1/sink/mode` (JWT) e `PUT /api/v1/sink/mode` (JWT `ADMIN` ou `ANALYST`; `VIEWER` → `403 Forbidden`)

**Request (`PUT /api/v1/sink/mode`):**
```json
{
  "mode": "FAIL_500"
}
```

**Response `200 OK` (`GET` e `PUT /api/v1/sink/mode`):**
```json
{
  "mode": "FAIL_500",
  "timeoutDelayMs": 2500,
  "updatedAt": "2026-11-23T12:00:00Z"
}
```

#### `POST /api/v1/sink/test` (Público / Dev — Merchant Webhook Sink)
Recebe o payload JSON do evento assinado com os headers `X-LedgerPulse-Signature`, `X-LedgerPulse-Event-Id` e `X-Correlation-Id`:
- Quando `mode == "OK_200"` → responde **`200 OK`** imediatamente:
```json
{
  "received": true,
  "mode": "OK_200",
  "eventId": "c7a615f1-82b3-4e11-9c2a-5d2d1b63e701",
  "signaturePresent": true,
  "receivedAt": "2026-11-23T12:05:00Z"
}
```
- Quando `mode == "FAIL_500"` → responde **`500 Internal Server Error`**:
```json
{
  "received": false,
  "mode": "FAIL_500",
  "eventId": "c7a615f1-82b3-4e11-9c2a-5d2d1b63e701",
  "error": "Simulated merchant webhook sink failure (HTTP 500)"
}
```
- Quando `mode == "TIMEOUT"` → aguarda `timeoutDelayMs` (`2500` ms, acima do timeout HTTP padrão do dispatcher) e responde **`504 Gateway Timeout`**:
```json
{
  "received": false,
  "mode": "TIMEOUT",
  "eventId": "c7a615f1-82b3-4e11-9c2a-5d2d1b63e701",
  "error": "Simulated merchant webhook sink timeout (HTTP 504)"
}
```
