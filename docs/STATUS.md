# 📌 Estado Compartilhado do Projeto (`ledgerpulse-platform`)

> **Atualizado em:** 20/11/2026  
> **Fase Atual:** `Fase 1 — Bootstrap Concluído (5 Chats Ativos · Pronto para Semana 1 / Dia 01)`  
> **Próximo Marco:** **Semana 1 · Dia 01** (Docker Compose + Infraestrutura Local — Chat 04)

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
| **Semana 1** | `01–07` | Docker Compose (PG 16, Redis 7, RabbitMQ 3), Contratos OpenAPI/Eventos, Bootstrap `ledger-core-api` (Java 21 + Flyway + Security JWT/API Key), Bootstrap `web-console` (React/TS) e CI inicial | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 2** | `08–14` | Double-Entry Ledger imutável + `@Version`, `Idempotency-Key` Redis, Motor de Split + Máquina de Estados, Transactional Outbox + Relay RabbitMQ, UI Auditoria Ledger + Contas | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 3** | `15–21` | Microsserviço Python `risk-engine-ai` (FastAPI + Scikit-Learn + `reason_codes`), Integração Resilience4j (Circuit Breaker + Fallback), Fluxo `MANUAL_REVIEW`, Central Antifraude UI | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 4** | `22–28` | Microsserviço `webhook-dispatcher` (Spring AMQP + Virtual Threads + HMAC-SHA256), Topologia Retry TTL + DLQ + Replay, Webhook Sink, Console DLQ + Simulador Checkout + Teste E2E | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 5** | `29–35` | Stream Real-Time via SSE, Estornos compensatórios (`REFUND`), Conciliação `@Scheduled` + CSV, Dashboard Real-Time + Rate Limiting Token Bucket Redis + Revisão Segurança/Índices | `01`, `02`, `03`, `04` | ⏳ Pendente |
| **Semana 6** | `36–42` | Métricas Prometheus nos 3 serviços, Dashboards Grafana provisionados, Script Load & Chaos Testing, Documentação C4/Postman, Auditoria Docker Clean Run e Release `v1.0.0` | `00`, `01`, `02`, `03`, `04` | ⏳ Pendente |

---

## 3. 🚧 Bloqueios / Decisões em Aberto

- **PostgreSQL 16 (Decisão fechada):** 1 database (`ledgerpulse`) com 2 schemas isolados (`ledger_core` e `webhooks`), inicializados via `infra/postgres/init/01-create-schemas.sql`.
- **Ownership `.env.example` (Resolvido):** incluído oficialmente no ownership do Chat 04 em `AGENTS.md` e `docs/chats-registry.json`.

---

## 4. 🔄 Último Handoff entre Chats

- **20/11/2026 — Bootstrap Concluído:** Todos os 5 chats foram ativados e registrados com `status: active` em [docs/chats-registry.json](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/chats-registry.json) e [AGENTS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/AGENTS.md). Os 4 especialistas concluíram o onboarding somente leitura confirmando papel, ownership, primeira entrega da Semana 1 e prontidão para o **Dia 01** (`chore/docker-compose-base-infra` — Chat 04), registrando para alinhamento pré-execução a definição `database vs schema` no PostgreSQL e a inclusão/autorização de `.env.example`/`.gitignore` para o Chat 04.
- **20/11/2026 — Setup Inicial:** Governança, blueprint arquitetural, ADRs `ADR-001` e `ADR-002`, contratos v1 e roadmap de 42 dias criados. Aguardando o usuário abrir o projeto no Antigravity e rodar o bootstrap do **Chat 00** conforme [docs/BOOTSTRAP_CHATS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/BOOTSTRAP_CHATS.md).
