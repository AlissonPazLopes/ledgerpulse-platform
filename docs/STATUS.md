# 📌 Estado Compartilhado do Projeto (`ledgerpulse-platform`)

> **Atualizado em:** 22/11/2026  
> **Fase Atual:** `Fase 3 — Semana 3 Concluída (Dias 15–21 · Branch feat/week-3-risk-engine-resilience com 13 commits pronta para PR #3)`  
> **Próximo Marco:** **Semana 4 · Dia 22** (`feat/webhook-dispatcher-bootstrap` — Microsserviço Java 21 + Virtual Threads + Spring AMQP `webhook-dispatcher` — Chat 02)

---

## 1. ✅ Checklist de Ativação Inicial (Faça 1 Vez)

- [x] Estrutura de pastas do monorepo criada em `Estudo_Java/ProjetosGitHub/ledgerpulse-platform`
- [x] Governança multi-chat (`AGENTS.md`), arquitetura base (`docs/architecture/`), contratos (`docs/contracts/`) e roadmap de 42 dias (`docs/ROADMAP-6-SEMANAS.md`) gerados
- [x] Repositório Git local inicializado na branch `main` (zero `remote` configurado)
- [x] **Passo do Usuário:** Abrir a pasta `ledgerpulse-platform` como um **Project separado no Antigravity**
- [x] **Passo do Usuário:** Abrir o primeiro chat (Chat 00) e colar o prompt de ativação de [docs/BOOTSTRAP_CHATS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/BOOTSTRAP_CHATS.md)
- [x] **Passo do Chat 00:** Registrar os 5 `conversation_id` em `docs/chats-registry.json` e `AGENTS.md`, mudando `status` para `active`

---

## 2. 🗺️ Progresso por Semana (Roadmap de 6 Semanas / 42 Dias)

| Semana | Dias | Foco Principal | Chats Principais | Status |
| :---: | :---: | :--- | :--- | :---: |
| **Semana 1** | `01–07` | Docker Compose (PG 16, Redis 7, RabbitMQ 3), Contratos OpenAPI/Eventos, Bootstrap `ledger-core-api` (Java 21 + Flyway + Security JWT/API Key), Bootstrap `web-console` (React/TS) e CI inicial | `01`, `02`, `03`, `04` | ✅ Dias 01–07 Implementados (Docker live pendente) |
| **Semana 2** | `08–14` | Double-Entry Ledger imutável + `@Version`, `Idempotency-Key` Redis, Motor de Split + Máquina de Estados, Transactional Outbox + Relay RabbitMQ, UI Auditoria Ledger + Contas | `01`, `02`, `03`, `04` | ✅ Dias 08–14 Concluídos |
| **Semana 3** | `15–21` | Microsserviço Python `risk-engine-ai` (FastAPI + Scikit-Learn + `reason_codes`), Integração Resilience4j (Circuit Breaker + Fallback), Fluxo `MANUAL_REVIEW`, Central Antifraude UI | `01`, `02`, `03`, `04` | ✅ Dias 15–21 Concluídos |
| **Semana 4** | `22–28` | Microsserviço `webhook-dispatcher` (Spring AMQP + Virtual Threads + HMAC-SHA256), Topologia Retry TTL + DLQ + Replay, Webhook Sink, Console DLQ + Simulador Checkout + Teste E2E | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 5** | `29–35` | Stream Real-Time via SSE, Estornos compensatórios (`REFUND`), Conciliação `@Scheduled` + CSV, Dashboard Real-Time + Rate Limiting Token Bucket Redis + Revisão Segurança/Índices | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 6** | `36–42` | Métricas Prometheus nos 3 serviços, Dashboards Grafana provisionados, Script Load & Chaos Testing, Documentação C4/Postman, Auditoria Docker Clean Run e Release `v1.0.0` | `00`, `01`, `02`, `03`, `04` | ⏳ Pendente |

---

## 3. 🚧 Bloqueios / Decisões em Aberto

- **Validação Live Docker (Pendente no host Windows):** `docker compose config --quiet` validado com código `0`, porém o daemon do **Docker Desktop** estava parado (`//./pipe/dockerDesktopLinuxEngine` indisponível) e um PostgreSQL local do Windows (PID `7984`) ocupa a porta `5432`. A suíte `LedgerCoreTestcontainersIT` rodará automaticamente no CI (`ubuntu-latest` / `mvn -B verify`) e localmente após abrir o Docker Desktop (parando o Postgres local ou usando `POSTGRES_PORT=5433` em `.env`).
- **PostgreSQL 16 (Decisão fechada):** 1 database (`ledgerpulse`) com 2 schemas isolados (`ledger_core` e `webhooks`), inicializados via `infra/postgres/init/01-create-schemas.sql`.
- **Ownership `.env.example` (Resolvido):** incluído oficialmente no ownership do Chat 04 em `AGENTS.md` e `docs/chats-registry.json`.

---

## 4. 🔄 Último Handoff entre Chats

- **22/11/2026 — Semana 3 (Dias 15–21) Consolidada na Branch `feat/week-3-risk-engine-resilience`:**
  - **Chat 01 (Contract-First S3 & V4):** Congelou payloads da Semana 3 em [api-v1-overview.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/api-v1-overview.md), criou [openapi-risk-engine-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-risk-engine-v1.yaml) e a fixture cruzada [risk-evaluate-contract.json](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/contract-samples/risk-evaluate-contract.json), atualizou [openapi-ledger-core-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-ledger-core-v1.yaml) e documentou `V4__create_risk_review_decisions.sql` em [01-modelo-dados-v1.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/architecture/01-modelo-dados-v1.md).
  - **Chat 02 (Dias 15–19 & 21):** Implementou `services/risk-engine-ai` (Python 3.13 local / `python:3.12-slim` Docker, FastAPI, Pydantic v2, pipeline `IsolationForest` + `RandomForestClassifier`, artefato `risk_model_v1.joblib`, motor híbrido de regras + ML, `ruff check .` OK, `pytest` `10/10` passando) e integrou no `services/ledger-core-api` o cliente Resilience4j (`CircuitBreaker` + `Retry` + timeout `1.5s` + fallback `MANUAL_REVIEW`), `V4__create_risk_review_decisions.sql`, fluxo `/api/v1/risk/reviews` + decisão `APPROVE`/`DECLINE` com liquidação no Ledger e eventos Outbox, e testes de contrato cruzado (`mvn -B verify`: `50/50` Surefire passando).
  - **Chat 03 (Dia 20):** Implementou a **Central Antifraude** em `apps/web-console` (`FraudReviewCenterPage`, `RiskScoreGauge`, `ReasonCodeChips`, `ReviewDecisionModal`, `riskReviewService`) validada com `npx tsc --noEmit` e `npm run build` (`1606` módulos, `exit 0`).
  - **Chat 04 (Dias 15 & 21 Infra/CI):** Adicionou `risk-engine-ai` no `docker-compose.yml` e `.env.example` (`docker compose config --quiet` OK) e o job Python (`ruff` + `pytest`) em `.github/workflows/ci.yml`.
  - **Git Local:** `13` commits atômicos na branch `feat/week-3-risk-engine-resilience` a partir de `main` (`8ba9a03`), prontos para push e PR #3.
- **21/11/2026 — Semana 2 (Dias 08–14) Consolidada na Branch `feat/week-2-ledger-outbox`:**
  - **Chat 01 (Alinhamento V3 & Invariantes):** Documentou `V3__create_outbox_events.sql` e regras de partidas dobradas em [01-modelo-dados-v1.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/architecture/01-modelo-dados-v1.md) e [events-rabbitmq.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/events-rabbitmq.md).
  - **Chat 02 (Dias 08–12 & 14):** Implementou em `services/ledger-core-api` as entidades `Account` (`@Version`), `LedgerTransaction`, `LedgerEntry` (append-only, ordenação determinística `DEBIT` antes de `CREDIT`), `LedgerService` + testes multi-thread de concorrência, `IdempotencyService` (Redis/In-Memory + hash SHA-256 + `409`), `Payment`/`PaymentSplit` + `SplitCalculator` + `StubRiskEvaluator`, `V3__create_outbox_events.sql` + `OutboxRelayWorker` (RabbitMQ `ledger.events`) e `LedgerCoreTestcontainersIT` — validado com `mvn -B verify` (`44/44` Surefire passando; `LedgerCoreTestcontainersIT` compilado e pronto para Docker/CI).
  - **Chat 03 (Dias 13–14):** Implementou em `apps/web-console` os serviços e páginas `LedgerAuditPage` (com `LedgerTransactionDrawer` provando `Σ Débitos = Σ Créditos`) e `AccountsPage` (com `CreateAccountModal` RBAC `ADMIN`) — validado com `npx tsc --noEmit` e `npm run build` (`1601` módulos, `exit 0`).
  - **Chat 04 (Dia 14 CI):** Atualizou `.github/workflows/ci.yml` para executar `mvn -B verify` no job `ledger-core-api`.
  - **Git Local:** `13` commits atômicos na branch `feat/week-2-ledger-outbox` a partir de `main` (`55fb9fb`), prontos para push e PR #2.
- **20/11/2026 — Semana 1 (Dias 01–07) Consolidada na Branch `feat/week-1-foundation`:**
  - **Chat 04 (Dia 01 & Dia 07 CI):** `docker-compose.yml`, `.env.example`, `infra/postgres/init/01-create-schemas.sql`, `Makefile` (`docker compose config --quiet` OK) e `.github/workflows/ci.yml`.
  - **Chat 01 (Dia 02):** Contratos v1 congelados em `docs/contracts/api-v1-overview.md`, `docs/contracts/openapi-ledger-core-v1.yaml` (OpenAPI 3.1.0), `docs/contracts/events-rabbitmq.md` e modelagem SQL conceitual `docs/architecture/01-modelo-dados-v1.md`.
  - **Chat 02 (Dias 03–05):** `services/ledger-core-api` (Java 21 + Spring Boot 3.3.5, `/api/v1/health`, `CorrelationIdFilter`, `GlobalExceptionHandler` RFC 7807, Flyway `V1` e `V2`, Spring Security 6 stateless JWT + `X-API-Key`, `DevIdentitySeeder`) validado com `mvn -B test` (`21/21` testes passando).
  - **Chat 03 (Dias 06–07):** `apps/web-console` (React + TypeScript strict + Vite + Tailwind CSS, formatador `formatCurrencyFromCents`, `httpClient` com JWT/Correlation-Id/RFC 7807, `LoginPage` + `LayoutShell`) validado com `npx tsc --noEmit` e `npm run build` (`1591` módulos, `exit 0`).
  - **Git Local:** `1` commit inicial na `main` + `12` commits na `feat/week-1-foundation` (**13 commits totais**, zero operações remotas). Pronto para push/PR e avanço para a **Semana 2 · Dia 08**.
- **20/11/2026 — Setup Inicial:** Governança, blueprint arquitetural, ADRs `ADR-001` e `ADR-002`, contratos v1 e roadmap de 42 dias criados. Aguardando o usuário abrir o projeto no Antigravity e rodar o bootstrap do **Chat 00** conforme [docs/BOOTSTRAP_CHATS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/BOOTSTRAP_CHATS.md).
