# 🗓️ Roadmap de 6 Semanas (42 Dias Úteis) — `ledgerpulse-platform`

> **Ritmo Sustentável:** 1 micro-entrega atômica real por dia útil (**20 a 35 minutos/dia**).  
> **Regra de Qualidade:** Toda entrega encerra compilando e com os testes do dia passando, pronta para 5–8 micro-commits semânticos numa PR `Closes #N`.

---

## 🟢 Semana 1 (Dias 01–07) — Fundação, Infraestrutura & Contratos Base

| Dia | Branch Sugerida | Chat(s) | Entrega Técnica | Critério de Pronto (DoD) |
| :---: | :--- | :---: | :--- | :--- |
| **01** | `chore/docker-compose-base-infra` | `04` | Criar `docker-compose.yml` com PostgreSQL 16 (`5432`), Redis 7 (`6379`) e RabbitMQ 3 Management (`5672`/`15672`), `.env.example`, `Makefile` e `healthcheck`s. | `docker compose up -d` sobe os 3 containers com status `healthy`. |
| **02** | `docs/contracts-v1-freeze` | `01` | Detalhar payloads JSON de exemplo e códigos de erro RFC 7807 em `docs/contracts/api-v1-overview.md` + especificação OpenAPI inicial. | Contratos v1 revisados e congelados para Semana 1 e 2. |
| **03** | `feat/core-api-bootstrap-flyway` | `02` | Bootstrap Maven de `services/ledger-core-api` (Java 21, Spring Boot 3.3+, Web, JPA, Validation, Actuator, PostgreSQL, Redis) + `GET /api/v1/health` + `application.yml` / `application-test.yml`. | `mvn test` passa e `/actuator/health` responde `UP`. |
| **04** | `feat/core-db-migrations-v1-v2` | `02` | Migrations Flyway `V1__create_merchants_and_accounts.sql` e `V2__create_ledger_and_payments.sql` (valores em `BIGINT`, índices e constraints de integridade) + teste de schema. | Flyway aplica `V1` e `V2` sem erro no Postgres e em teste. |
| **05** | `feat/core-security-jwt-apikey` | `02` | Spring Security 6 stateless: autenticação JWT para operadores (`ADMIN`, `ANALYST`, `VIEWER`), filtro `X-API-Key` para *merchants*, endpoints `POST /api/v1/auth/login` e `GET /api/v1/auth/me` + testes MockMvc. | Testes de `401`, `403` e login JWT `200` passando. |
| **06** | `feat/web-console-bootstrap` | `03` | Bootstrap de `apps/web-console` (React + TypeScript strict + Vite + Tailwind CSS + React Router + cliente HTTP `fetch`/Axios com interceptors JWT e `X-Correlation-Id`). | `npm run build` e servidor Vite dev (`5173`) sobem sem erros. |
| **07** | `feat/ci-and-login-screen` | `04`, `03` | Pipeline `.github/workflows/ci.yml` (jobs `ledger-core-api` e `web-console`) + Tela de Login e Layout Shell lateral (Sidebar/Topbar) no `web-console`. | Build + testes Java e build/typecheck TS verdes no CI/local. |

---

## 🟡 Semana 2 (Dias 08–14) — Double-Entry Ledger, Split & Transactional Outbox

| Dia | Branch Sugerida | Chat(s) | Entrega Técnica | Critério de Pronto (DoD) |
| :---: | :--- | :---: | :--- | :--- |
| **08** | `feat/ledger-double-entry-entities` | `02` | Entidades `Account` (com `@Version` Optimistic Locking e `balanceCents`), `LedgerTransaction` e `LedgerEntry` (append-only, `EntryDirection`: `DEBIT`/`CREDIT`, `amountCents > 0`) + repositórios JPA. | Testes unitários validando invariantes e proibição de valor zero/negativo. |
| **09** | `feat/ledger-service-concurrency` | `02` | `LedgerService` garantindo atomicidade de partidas dobradas ($\sum \text{Débitos} = \sum \text{Créditos}$) + testes de concorrência com `ExecutorService` validando `OptimisticLockingFailureException` / `HTTP 409`. | Teste multi-thread garante saldo exato sem *lost update*. |
| **10** | `feat/redis-idempotency-guard` | `02` | Aspect/Filter ou serviço `IdempotencyService` com Redis (`Idempotency-Key` + hash do payload + TTL 24h), retornando resposta cacheada em replay idêntico e `409`/`422` se payload divergir. | Testes integrados cobrindo replay idêntico e conflito de payload. |
| **11** | `feat/payments-split-state-machine` | `02` | Entidade `Payment` + Máquina de Estados (`CREATED` → `RISK_ANALYSIS` → `AUTHORIZED` → `SETTLED` / `DECLINED`) + motor de Split percentual/fixo (taxa plataforma + recebedores) gerando lançamentos no Ledger. | Soma dos splits + taxa igual ao valor bruto; testes unitários e de API passando. |
| **12** | `feat/transactional-outbox-relay` | `02` | Migration Flyway `V3__create_outbox_events.sql`, gravação atômica na tabela `outbox_events` dentro da mesma `@Transactional` do Ledger/Payment + Worker `@Scheduled` publicando no RabbitMQ (`ledger.events`). | Evento só vai ao RabbitMQ se a transação SQL confirmar; retry de falha no relay testado. |
| **13** | `feat/web-ledger-audit-view` | `03` | Página **Auditoria do Ledger** no `web-console`: tabela paginada de transações contábeis, drawer expansível mostrando partidas dobradas (`DEBIT` vs `CREDIT`) e badge de balanço zerado. | Typecheck + build verdes e visualização funcional consumindo `/api/v1/ledger`. |
| **14** | `test/core-testcontainers-and-accounts-ui` | `02`, `03` | Testes de integração com Testcontainers (PostgreSQL 16 + Redis 7) no `ledger-core-api` + Página **Contas & Saldos** no `web-console` (criação de conta, saldo disponível/bloqueado). | Suíte `mvn verify` com Testcontainers 100% verde e tela de Contas pronta. |

---

## 🟠 Semana 3 (Dias 15–21) — Antifraude com IA (`risk-engine-ai`) & Resiliência

| Dia | Branch Sugerida | Chat(s) | Entrega Técnica | Critério de Pronto (DoD) |
| :---: | :--- | :---: | :--- | :--- |
| **15** | `feat/risk-engine-fastapi-bootstrap` | `02` | Bootstrap de `services/risk-engine-ai` (Python `3.13` local / `python:3.12-slim` Docker, FastAPI, Pydantic v2, Uvicorn, Pytest) + `GET /health` + `Dockerfile` e entrada no Compose. | `pytest` passa e `GET /health` retorna `200 OK` na porta `8001`. |
| **16** | `feat/risk-engine-ml-pipeline` | `02` | Gerador de dataset sintético realista (velocidade de transações, valor atípico, horário, histórico do pagador) + script de treino Scikit-Learn (`IsolationForest` + `RandomForestClassifier`) gerando artefato `.joblib` e métricas. | Script reproduzível treina e serializa modelo com relatório de precisão/recall. |
| **17** | `feat/risk-engine-hybrid-scoring` | `02` | Endpoint `POST /v1/risk/evaluate` combinando score ML + regras determinísticas (ex.: `VELOCITY_SPIKE`, `HIGH_TICKET_NEW_CARD`, `NIGHT_ANOMALY`), retornando `riskScore`, `decision` (`APPROVE`/`REVIEW`/`BLOCK`) e `reasonCodes`. | Testes Pytest validando os 3 desfechos e contratos Pydantic v2. |
| **18** | `feat/core-resilience4j-risk-client` | `02` | Cliente HTTP no `ledger-core-api` chamando `risk-engine-ai` com **Resilience4j** (Timeout 1.5s, Retry curto, Circuit Breaker) e fallback seguro para `MANUAL_REVIEW` quando o serviço Python estiver indisponível. | Teste com MockWebServer/WireMock simula timeout/500 e abre o Circuit Breaker sem perder o pagamento. |
| **19** | `feat/core-manual-review-workflow` | `02` | Endpoints `GET /api/v1/risk/reviews` e `POST /api/v1/risk/reviews/{paymentId}/decision` (`APPROVE`/`DECLINE` por `ANALYST`/`ADMIN`), avançando o pagamento para `SETTLED` ou `DECLINED` e emitindo evento no Outbox. | Testes MockMvc cobrindo RBAC (`VIEWER` bloqueado `403`, `ANALYST` autorizado) e transição no Ledger. |
| **20** | `feat/web-fraud-review-center` | `03` | Página **Central Antifraude** no `web-console`: fila de transações em `MANUAL_REVIEW`, medidor visual de score (`0–100`), chips de `reasonCodes` e modal de decisão com justificativa. | Build/typecheck OK e fluxo completo de aprovação/rejeição na UI. |
| **21** | `ci/python-risk-and-contract-tests` | `04`, `01` | Job `risk-engine-ai` (`ruff` + `pytest`) no `.github/workflows/ci.yml` + testes de contrato cruzado garantindo alinhamento exato dos schemas Java DTO <-> Pydantic v2. | CI completo rodando Java + Python + React sem falhas. |

---

## 🔴 Semana 4 (Dias 22–28) — `webhook-dispatcher`, RabbitMQ DLQ & Simulador Checkout

| Dia | Branch Sugerida | Chat(s) | Entrega Técnica | Critério de Pronto (DoD) |
| :---: | :--- | :---: | :--- | :--- |
| **22** | `feat/webhook-dispatcher-bootstrap` | `02` | Bootstrap de `services/webhook-dispatcher` (Java 21 + Virtual Threads habilitadas, Spring Boot 3.3+, Spring AMQP, Flyway + Postgres schema `webhooks`) + `Dockerfile`. | Serviço sobe na porta `8082` e `mvn test` passa. |
| **23** | `feat/rabbitmq-retry-dlq-topology` | `02` | Configuração declarativa RabbitMQ (`ledger.events` topic exchange, filas de consumo, `ledger.retry.5s`, `ledger.retry.30s`, `ledger.retry.120s` via TTL + Dead-Letter Exchange para `ledger.webhooks.dlq`). | Topologia validada automaticamente ao subir RabbitMQ; 3 falhas encaminham mensagem para DLQ. |
| **24** | `feat/webhook-hmac-virtual-threads` | `02` | Motor de entrega HTTP em Virtual Threads assinando payload com HMAC-SHA256 (`X-LedgerPulse-Signature: t=...,v1=...`), registrando `WebhookDeliveryAttempt` (status HTTP, latência ms, erro). | Testes unitários de assinatura HMAC e persistência de tentativas de entrega passando. |
| **25** | `feat/webhook-sink-and-dlq-replay` | `02` | Endpoint simulador **Merchant Webhook Sink** (`POST /api/v1/sink/test` com modos `OK_200`, `FAIL_500`, `TIMEOUT`) + API de listagem e **Replay manual** (`POST /api/v1/webhooks/dlq/{id}/replay`). | Replay reenvia mensagem da DLQ com sucesso após alternar Sink de `FAIL_500` para `OK_200`. |
| **26** | `feat/web-dlq-webhooks-console` | `03` | Página **Console de Webhooks & DLQ** no `web-console`: cadastro de URLs/secrets, seletor de modo do Webhook Sink, histórico de tentativas com assinatura HMAC e botão de **Replay DLQ**. | UI exibe falhas, contadores de retry e executa replay em 1 clique. |
| **27** | `feat/web-checkout-split-simulator` | `03` | Página **Simulador de Checkout & Split** no `web-console`: formulário interativo (valor, parcelas/método, recebedores de split, envio com `Idempotency-Key` automática ou repetida para testar replay idempotente). | Criação de cobrança mostra passo a passo (Risco -> Ledger -> Outbox -> Webhook). |
| **28** | `test/e2e-full-payment-dlq-flow` | `04`, `02` | Teste E2E automatizado cobrindo o fluxo ponta a ponta: Checkout -> `risk-engine-ai` -> `ledger-core-api` (Partidas Dobradas + Outbox) -> RabbitMQ -> `webhook-dispatcher` -> falha/DLQ -> Replay `200`. | Cenário E2E reproduzível rodando limpo contra a stack Docker. |

---

## 🟣 Semana 5 (Dias 29–35) — Tempo Real (SSE), Estornos, Conciliação & Hardening

| Dia | Branch Sugerida | Chat(s) | Entrega Técnica | Critério de Pronto (DoD) |
| :---: | :--- | :---: | :--- | :--- |
| **29** | `feat/core-sse-realtime-stream` | `02` | Endpoint `GET /api/v1/stream/events` usando **Server-Sent Events (`SseEmitter`)** no `ledger-core-api` transmitindo eventos de pagamento, risco e webhook em tempo real com heartbeat anti-timeout. | Teste valida recepção de eventos SSE após criação/liquidação de pagamento. |
| **30** | `feat/core-compensating-refunds` | `02` | Fluxo de **Estorno Total e Parcial** (`POST /api/v1/payments/{id}/refunds`): transição para `REFUNDED`/`PARTIALLY_REFUNDED` e geração de lançamentos compensatórios imutáveis revertendo o split proporcionalmente. | Saldo das contas volta ao valor esperado sem nunca alterar lançamentos antigos. |
| **31** | `feat/core-scheduled-reconciliation` | `02` | Job `@Scheduled` de **Conciliação Contábil** comparando soma de `ledger_entries` com saldos em `accounts` e totais de `payments`, salvando `ReconciliationReport` + exportação `GET /api/v1/reconciliation/export.csv`. | Job detecta divergência em teste induzido e gera relatório CSV válido. |
| **32** | `feat/web-realtime-executive-dashboard` | `03` | Página **Dashboard Executivo Real-Time** no `web-console`: KPIs (TPV, Taxa de Aprovação, Fila Antifraude, Saúde Webhooks), gráficos Recharts (volume e risco por hora) e feed ao vivo conectado no SSE. | Dashboard atualiza KPIs e feed instantaneamente ao disparar um pagamento no simulador. |
| **33** | `feat/web-reconciliation-integrity-page` | `03` | Página **Integridade & Conciliação**: histórico de auditorias contábeis, indicador visual de "Ledger 100% Balanceado", botão de conciliação sob demanda e download de CSV. | Tela integrada ao backend com download funcional de relatório CSV. |
| **34** | `test/web-vitest-rtl-and-ux-polish` | `03` | Suíte de testes no `web-console` com **Vitest + React Testing Library** (formatação de centavos, Checkout Simulator, Fraud Modal, DLQ Console) + refinamento de responsividade e estados vazios/erro. | `npm test` e `npm run build` 100% verdes. |
| **35** | `perf/rate-limit-redis-and-sql-indexes` | `01`, `02` | Revisão de segurança & performance: **Rate Limiting (Token Bucket no Redis)** por `X-API-Key` retornando `HTTP 429` + headers `X-RateLimit-*`, além de `EXPLAIN ANALYZE` e migration Flyway `V4` de índices. | Teste de carga leve aciona `429 Too Many Requests` corretamente e queries críticas usam Index Scan. |

---

## 🔵 Semana 6 (Dias 36–42) — Observabilidade, Chaos Testing & Release `v1.0.0`

| Dia | Branch Sugerida | Chat(s) | Entrega Técnica | Critério de Pronto (DoD) |
| :---: | :--- | :---: | :--- | :--- |
| **36** | `feat/observability-prometheus-metrics` | `02`, `04` | Exposição de métricas customizadas Micrometer/Prometheus nos 3 serviços (`ledger_payments_total`, `risk_evaluation_latency`, `webhook_dlq_total`, `circuit_breaker_state`). | `/actuator/prometheus` e `/metrics` expõem métricas customizadas corretamente. |
| **37** | `feat/grafana-provisioned-dashboards` | `04` | Configuração de `infra/prometheus/prometheus.yml` e dashboards JSON provisionados em `infra/grafana/` no `docker-compose.yml` (Zero clique manual ao subir Grafana na porta `3000`). | Grafana abre na porta `3000` com datasource Prometheus conectado e painéis populados. |
| **38** | `test/load-and-chaos-suite` | `04`, `02` | Script de **Load & Chaos Testing** (`infra/scripts/chaos-load-test.py` ou k6): (1) 50 requisições concorrentes com mesma `Idempotency-Key`, (2) derrubar `risk-engine-ai` e provar fallback `MANUAL_REVIEW`, (3) falhar webhook e provar DLQ. | Relatório de execução gerado em `infra/` comprovando todas as garantias de resiliência. |
| **39** | `docs/c4-diagrams-and-api-collection` | `01`, `04` | Exportar/atualizar coleção Postman/Insomnia completa em `docs/contracts/ledgerpulse.postman_collection.json`, diagramas C4 finais e guia de troubleshooting. | Coleção importável e validada contra a stack rodando. |
| **40** | `docs/flagship-readme-and-media` | `04`, `03` | Atualizar `README.md` raiz com arquitetura completa, tabela de ADRs, badges de CI, instruções de 1 comando e espaços/prints reais do Console Web e Grafana. | README completo e visualmente atrativo para recrutadores e Tech Leads. |
| **41** | `chore/final-clean-boot-audit` | `00`, `04` | Auditoria limpa do zero: `docker compose down -v && docker compose up --build` + execução completa de `mvn verify`, `pytest`, `npm test` e script de caos. | Stack inteira sobe do zero sem intervenção manual e todos os checks passam. |
| **42** | `release/v1.0.0-portfolio-launch` | `00`, `04` | Checklist final de release `v1.0.0`, atualização final do `docs/STATUS.md` e roteiro de 3 posts técnicos para o LinkedIn (Partidas Dobradas, Circuit Breaker Java+Python e Webhooks HMAC/DLQ). | Release `v1.0.0` pronta para publicação quando o usuário decidir enviar ao GitHub. |
