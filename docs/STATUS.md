# 📌 Estado Compartilhado do Projeto (`ledgerpulse-platform`)

> **Atualizado em:** 20/11/2026  
> **Fase Atual:** `Fase 1 — Semana 1 Concluída (Dias 01–07 · Branch feat/week-1-foundation com 13 commits totais pronta para PR)`  
> **Próximo Marco:** **Semana 2 · Dia 08** (`feat/ledger-double-entry-entities` — Entidades Double-Entry Ledger + `@Version` — Chat 02) + validação live Docker Desktop (`docker compose up -d`)

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
| **Semana 2** | `08–14` | Double-Entry Ledger imutável + `@Version`, `Idempotency-Key` Redis, Motor de Split + Máquina de Estados, Transactional Outbox + Relay RabbitMQ, UI Auditoria Ledger + Contas | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 3** | `15–21` | Microsserviço Python `risk-engine-ai` (FastAPI + Scikit-Learn + `reason_codes`), Integração Resilience4j (Circuit Breaker + Fallback), Fluxo `MANUAL_REVIEW`, Central Antifraude UI | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 4** | `22–28` | Microsserviço `webhook-dispatcher` (Spring AMQP + Virtual Threads + HMAC-SHA256), Topologia Retry TTL + DLQ + Replay, Webhook Sink, Console DLQ + Simulador Checkout + Teste E2E | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 5** | `29–35` | Stream Real-Time via SSE, Estornos compensatórios (`REFUND`), Conciliação `@Scheduled` + CSV, Dashboard Real-Time + Rate Limiting Token Bucket Redis + Revisão Segurança/Índices | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 6** | `36–42` | Métricas Prometheus nos 3 serviços, Dashboards Grafana provisionados, Script Load & Chaos Testing, Documentação C4/Postman, Auditoria Docker Clean Run e Release `v1.0.0` | `00`, `01`, `02`, `03`, `04` | ⏳ Pendente |

---

## 3. 🚧 Bloqueios / Decisões em Aberto

- **Validação Live Docker (Dia 01 Pendente):** `docker compose config --quiet` validado com código `0`, porém o daemon do **Docker Desktop** estava parado (`//./pipe/dockerDesktopLinuxEngine` indisponível) e um PostgreSQL local do Windows (PID `7984`) ocupa a porta `5432`. Ao retornar, abra o Docker Desktop e pare o Postgres local ou crie `.env` (a partir de `.env.example`) com `POSTGRES_PORT=5433` para executar `docker compose up -d` e verificar os 3 containers `healthy` + schemas (`\dn`).
- **PostgreSQL 16 (Decisão fechada):** 1 database (`ledgerpulse`) com 2 schemas isolados (`ledger_core` e `webhooks`), inicializados via `infra/postgres/init/01-create-schemas.sql`.
- **Ownership `.env.example` (Resolvido):** incluído oficialmente no ownership do Chat 04 em `AGENTS.md` e `docs/chats-registry.json`.

---

## 4. 🔄 Último Handoff entre Chats

- **20/11/2026 — Semana 1 (Dias 01–07) Consolidada na Branch `feat/week-1-foundation`:**
  - **Chat 04 (Dia 01 & Dia 07 CI):** `docker-compose.yml`, `.env.example`, `infra/postgres/init/01-create-schemas.sql`, `Makefile` (`docker compose config --quiet` OK) e `.github/workflows/ci.yml`.
  - **Chat 01 (Dia 02):** Contratos v1 congelados em `docs/contracts/api-v1-overview.md`, `docs/contracts/openapi-ledger-core-v1.yaml` (OpenAPI 3.1.0), `docs/contracts/events-rabbitmq.md` e modelagem SQL conceitual `docs/architecture/01-modelo-dados-v1.md`.
  - **Chat 02 (Dias 03–05):** `services/ledger-core-api` (Java 21 + Spring Boot 3.3.5, `/api/v1/health`, `CorrelationIdFilter`, `GlobalExceptionHandler` RFC 7807, Flyway `V1` e `V2`, Spring Security 6 stateless JWT + `X-API-Key`, `DevIdentitySeeder`) validado com `mvn -B test` (`21/21` testes passando).
  - **Chat 03 (Dias 06–07):** `apps/web-console` (React + TypeScript strict + Vite + Tailwind CSS, formatador `formatCurrencyFromCents`, `httpClient` com JWT/Correlation-Id/RFC 7807, `LoginPage` + `LayoutShell`) validado com `npx tsc --noEmit` e `npm run build` (`1591` módulos, `exit 0`).
  - **Git Local:** `1` commit inicial na `main` + `12` commits na `feat/week-1-foundation` (**13 commits totais**, zero operações remotas). Pronto para push/PR e avanço para a **Semana 2 · Dia 08**.
- **20/11/2026 — Setup Inicial:** Governança, blueprint arquitetural, ADRs `ADR-001` e `ADR-002`, contratos v1 e roadmap de 42 dias criados. Aguardando o usuário abrir o projeto no Antigravity e rodar o bootstrap do **Chat 00** conforme [docs/BOOTSTRAP_CHATS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/BOOTSTRAP_CHATS.md).
