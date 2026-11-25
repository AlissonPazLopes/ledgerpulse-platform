# 🏛️ 00 — Visão Arquitetural da Plataforma (`LedgerPulse v1.0.0`)

> **Versão:** `v1.0.0` (Final · Semanas 1–6)  
> **Responsável:** Chat 01 (Arquiteto de Software & Contratos)  
> **Domínio:** Fintech B2B (Pagamentos, Split de Marketplace, Double-Entry Ledger, Antifraude IA e Webhooks Resilientes)  
> **Padrão de Design:** Clean Room Design (100% original, zero dependência de projetos anteriores)

---

## 1. 🧩 Módulos Executáveis e Infraestrutura (9 Containers)

A plataforma é organizada como um **monorepo poliglota** composto por **4 módulos executáveis** e **5 componentes de infraestrutura/observabilidade**:

| Componente / Container | Caminho | Stack | Responsabilidade Central |
| :--- | :--- | :--- | :--- |
| **`ledger-core-api`** | `services/ledger-core-api` | Java 21, Spring Boot 3.3+, Security 6, JPA, Flyway (`V1`–`V6`), Resilience4j, Micrometer | Núcleo transacional: Contas, Double-Entry Ledger, Pagamentos, Split, Estornos, Outbox, Conciliação, Rate Limiting Redis e SSE. |
| **`risk-engine-ai`** | `services/risk-engine-ai` | Python 3.13/3.12, FastAPI, Pydantic v2, Scikit-Learn | Microsserviço de Antifraude Híbrido (`IsolationForest` + `RandomForest` + Regras determinísticas) com `reason_codes` explicáveis. |
| **`webhook-dispatcher`** | `services/webhook-dispatcher` | Java 21 (Virtual Threads), Spring Boot 3.3+, Spring AMQP, Flyway (`webhooks`) | Consumo de eventos RabbitMQ, assinatura HMAC-SHA256, retries TTL exponenciais (`5s/30s/120s`), DLQ, replay manual e Merchant Webhook Sink. |
| **`web-console`** | `apps/web-console` | React, TypeScript strict, Vite, Tailwind CSS, Recharts | Console administrativo real-time (SSE via `fetch` `ReadableStream`), auditoria contábil, revisão antifraude, conciliação, DLQ e simulador de checkout. |
| **`postgres`** | Container `postgres` | PostgreSQL 16 (`ledgerpulse`: schemas `ledger_core` e `webhooks`) | Fonte da verdade relacional e transacional (Ledger append-only + Outbox + Entregas Webhook). |
| **`redis`** | Container `redis` | Redis 7 | Lock/cache de `Idempotency-Key` e Rate Limiting (Token Bucket Lua por `X-API-Key`). |
| **`rabbitmq`** | Container `rabbitmq` | RabbitMQ 3 + Management UI | Backbone assíncrono de eventos (`ledger.events`), filas de retry com TTL (`ledger.retry.*`) e `ledger.webhooks.dlq`. |
| **`prometheus`** | `infra/prometheus` | Prometheus | Coleta de métricas técnicas e de negócio (`ledger_payments_total`, `risk_evaluation_latency_seconds`, `webhook_dlq_total`, `circuit_breaker_state`). |
| **`grafana`** | `infra/grafana` | Grafana | Dashboards provisionados automaticamente (Zero clique manual ao subir na porta `3000`). |

---

## 2. 🗺️ Diagramas C4 Finais (`v1.0.0`)

### 2.1 C4 Nível 1 — Diagrama de Contexto do Sistema

```mermaid
flowchart TD
    OP["Operador Interno / Analista (Admin, Analyst, Viewer)"] -->|"HTTPS / Browser (JWT + SSE)"| LP["Plataforma LedgerPulse v1.0.0 (Pagamentos, Ledger, IA e Webhooks)"]
    MER["Merchant Integrador / Checkout SDK"] -->|"REST JSON (X-API-Key + Idempotency-Key)"| LP
    LP -->|"HTTP POST Assinado (X-LedgerPulse-Signature HMAC-SHA256)"| DEST["Endpoint Webhook do Merchant / Sink"]
    SRE["Engenheiro / Observabilidade"] -->|"Consulta Métricas e Dashboards (:3000 / :9090)"| LP
```

### 2.2 C4 Nível 2 — Diagrama de Containers (`docker-compose.yml` · 9 Containers)

```mermaid
flowchart LR
    OP["Operador Web"] -->|"HTTP :5173"| WEB["Container: web-console (React + TS + Vite :5173)"]
    MER["Merchant SDK / Chaos Script"] -->|"X-API-Key + Idempotency-Key :8080"| CORE["Container: ledger-core-api (Java 21 + Spring Boot 3.3 :8080)"]
    WEB -->|"JWT REST + SSE ReadableStream"| CORE
    WEB -->|"JWT Webhooks / Sink UI"| HOOK["Container: webhook-dispatcher (Java 21 Virtual Threads :8082)"]

    CORE -->|"JPA + Flyway V1-V6 (schema ledger_core)"| PG["Container: postgres (PostgreSQL 16 :5432)"]
    CORE -->|"Idempotency + Token Bucket Lua"| REDIS["Container: redis (Redis 7 :6379)"]
    CORE -->|"HTTP + Resilience4j Circuit Breaker (1.5s)"| RISK["Container: risk-engine-ai (Python FastAPI + Scikit-Learn :8001)"]
    CORE -->|"OutboxRelayWorker AMQP (ledger.events)"| RMQ["Container: rabbitmq (RabbitMQ 3 + Management :5672 / :15672)"]

    RMQ -->|"dispatch + retry TTL 5s/30s/120s + DLQ"| HOOK
    HOOK -->|"JPA + Flyway (schema webhooks)"| PG
    HOOK -->|"POST HMAC-SHA256"| SINK["Merchant Webhook Sink (/api/v1/sink/test)"]

    PROM["Container: prometheus (Prometheus :9090)"] -->|"Scrape /actuator/prometheus"| CORE
    PROM -->|"Scrape /metrics"| RISK
    PROM -->|"Scrape /actuator/prometheus"| HOOK
    GRAF["Container: grafana (Grafana :3000)"] -->|"PromQL Datasource"| PROM
```

### 2.3 C4 Nível 3 — Diagrama de Componentes (`ledger-core-api`)

```mermaid
flowchart TD
    REQ["Requisições HTTP (:8080)"] --> FILTERS["Camada Segurança & Filtros (CorrelationIdFilter, JwtAuthFilter, ApiKeyAuthFilter, ApiKeyRateLimitFilter)"]
    FILTERS --> CTRL["Controllers REST v1 (Auth, Accounts, Ledger, Payments, RiskReview, Dashboard, Refunds, Reconciliation, SSE)"]
    FILTERS -->|"Token Bucket Lua"| REDIS["Redis 7 (:6379)"]

    CTRL --> IDEMP["IdempotencyService (Redis + Hash SHA-256)"]
    CTRL --> PAY["PaymentService & SplitCalculator (Máquina de Estados)"]
    CTRL --> REV["RiskReviewService (MANUAL_REVIEW -> SETTLED/DECLINED)"]
    CTRL --> REF["RefundService & RefundCalculator (Estorno Proporcional Append-Only)"]
    CTRL --> REC["ReconciliationService (@Scheduled + Manual + Export CSV)"]
    CTRL --> DASH["DashboardService & SseEventHub (Stream Pós-Commit + Heartbeat)"]

    PAY -->|"Timeout 1.5s + CircuitBreaker + Fallback"| RISKCLI["ResilientRiskClient -> risk-engine-ai (:8001)"]
    PAY --> LED["LedgerService (Partidas Dobradas + Optimistic Locking @Version)"]
    REV --> LED
    REF --> LED

    LED --> REPO["Repositórios JPA (schema ledger_core · Flyway V1-V6)"]
    PAY --> OUTBOX["OutboxService (Mesma @Transactional)"]
    REV --> OUTBOX
    REF --> OUTBOX
    OUTBOX --> REPO
    REPO --> PG["PostgreSQL 16 (:5432)"]

    RELAY["OutboxRelayWorker (@Scheduled)"] --> REPO
    RELAY -->|"Publish ledger.events"| RMQ["RabbitMQ 3 (:5672)"]

    CTRL -.->|"Micrometer Counters, Timers & Gauges"| METRICS["Prometheus Registry (/actuator/prometheus)"]
    RISKCLI -.->|"circuit_breaker_state & risk_evaluation_latency"| METRICS
```

---

## 3. 🔄 Fluxo Principal de Pagamento, Risco, Ledger e Webhook

```mermaid
sequenceDiagram
    autonumber
    actor Merchant as "Merchant / Checkout Simulator"
    participant Core as "ledger-core-api (8080)"
    participant Redis as "Redis 7"
    participant Risk as "risk-engine-ai (8001)"
    participant PG as "PostgreSQL 16"
    participant RMQ as "RabbitMQ 3"
    participant Hook as "webhook-dispatcher (8082)"

    Merchant->>Core: POST /api/v1/payments (Idempotency-Key, amountCents)
    Core->>Redis: Verifica Token Bucket (X-API-Key) e Idempotency-Key
    alt Replay idêntico
        Redis-->>Core: Retorna resposta cacheada
        Core-->>Merchant: 201 Cached Response (X-Idempotent-Replay: true)
    else Nova requisição
        Core->>Risk: POST /v1/risk/evaluate (Circuit Breaker 1.5s)
        alt Risk API saudável -> APPROVE
            Risk-->>Core: score=18, decision=APPROVE
            Core->>PG: "@Transactional: Payment(SETTLED) + LedgerEntries + Outbox"
        else Risk API -> REVIEW ou Circuit Breaker Aberto
            Core->>PG: "@Transactional: Payment(MANUAL_REVIEW) + Outbox"
        else Risk API -> BLOCK
            Core->>PG: "@Transactional: Payment(DECLINED) + Outbox"
        end
        Core->>Redis: Salva resposta idempotente (TTL 24h)
        Core-->>Merchant: 201 Created (status + correlationId)
    end

    loop Relay @Scheduled
        Core->>PG: Lê outbox_events PENDING
        Core->>RMQ: Publica em ledger.events
        Core->>PG: Marca outbox_events PUBLISHED
    end

    RMQ-->>Hook: Entrega evento para entrega HMAC
    Hook->>Merchant: POST Webhook + X-LedgerPulse-Signature
    alt Merchant responde 5xx / Timeout
        Hook->>RMQ: Escalonamento x-death (5s -> 30s -> 120s -> DLQ)
    end
```

---

## 4. ⚙️ Máquina de Estados do Pagamento (`PaymentStatus`)

```mermaid
stateDiagram-v2
    [*] --> CREATED: "POST /api/v1/payments"
    CREATED --> RISK_ANALYSIS: "Avaliação iniciada"

    RISK_ANALYSIS --> AUTHORIZED: "decision = APPROVE"
    RISK_ANALYSIS --> MANUAL_REVIEW: "decision = REVIEW ou Fallback Resilience4j"
    RISK_ANALYSIS --> DECLINED: "decision = BLOCK"

    MANUAL_REVIEW --> AUTHORIZED: "Analista aprova revisão"
    MANUAL_REVIEW --> DECLINED: "Analista reprova revisão"

    AUTHORIZED --> SETTLED: "Partidas dobradas + Split confirmados"
    SETTLED --> PARTIALLY_REFUNDED: "Estorno parcial compensatório"
    SETTLED --> REFUNDED: "Estorno total compensatório"
    PARTIALLY_REFUNDED --> REFUNDED: "Estorno do saldo restante"

    DECLINED --> [*]
    REFUNDED --> [*]
```

---

## 5. 🔌 Matriz de Portas Locais Padronizadas

| Serviço / Recurso | Host Port | Container Port | URL Local / Health |
| :--- | :---: | :---: | :--- |
| **`ledger-core-api`** | `8080` | `8080` | `http://localhost:8080/actuator/health` |
| **`risk-engine-ai`** | `8001` | `8001` | `http://localhost:8001/health` |
| **`webhook-dispatcher`** | `8082` | `8082` | `http://localhost:8082/actuator/health` |
| **`web-console`** | `5173` | `5173` | `http://localhost:5173` |
| **PostgreSQL 16** | `5432` | `5432` | `localhost:5432` |
| **Redis 7** | `6379` | `6379` | `localhost:6379` |
| **RabbitMQ (AMQP / UI)** | `5672` / `15672` | `5672` / `15672` | `http://localhost:15672` |
| **Prometheus** | `9090` | `9090` | `http://localhost:9090` |
| **Grafana** | `3000` | `3000` | `http://localhost:3000` |

---

## 6. 🛡️ Pilares de Segurança & Resiliência

### Segurança
1. **Dois canais claros de autenticação no `ledger-core-api`:**
   - **Operadores Internos (Web Console):** `Authorization: Bearer <JWT>` com papéis (`ADMIN`, `ANALYST`, `VIEWER`).
   - **Merchants / Integrações Server-to-Server:** Header `X-API-Key` vinculado ao `merchantId`, protegido com Rate Limiting Token Bucket em Redis (`X-RateLimit-*` + `HTTP 429`).
2. **Integridade de Webhooks:** Todo webhook disparado inclui timestamp e assinatura **HMAC-SHA256** no header `X-LedgerPulse-Signature: t=<epoch>,v1=<hex_hmac>`.
3. **Auditabilidade:** Todas as requisições carregam ou geram `X-Correlation-Id` propagado nos logs estruturados, banco de dados, eventos RabbitMQ e SSE.

### Resiliência & Observabilidade
1. **Concorrência no Ledger (`@Version`):** Impede *lost updates* em saldos de contas sob requisições simultâneas, retornando `HTTP 409 Conflict` quando aplicável.
2. **Idempotência Distribuída:** `Idempotency-Key` no Redis evita cobranças e estornos duplicados em falhas de rede.
3. **Transactional Outbox:** Elimina o problema de *dual write* entre PostgreSQL e RabbitMQ.
4. **Circuit Breaker Java → Python:** Se o `risk-engine-ai` degradar ou cair, o pagamento **não é perdido** nem aprovado às cegas — entra em `MANUAL_REVIEW` (`RISK_ENGINE_UNAVAILABLE`) via fallback.
5. **Backoff Exponencial + DLQ:** Falhas de entrega de webhook seguem retries `5s → 30s → 120s` via TTL no RabbitMQ (`x-death` + retorno direto para `ledger.webhooks.dispatch`) antes de estacionar em `ledger.webhooks.dlq` para replay manual.
6. **Observabilidade Unificada:** Métricas de negócio, latência, estado do Circuit Breaker e entregas DLQ catalogadas em [03-troubleshooting-e-operacao.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/architecture/03-troubleshooting-e-operacao.md).
