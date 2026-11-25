# 🚀 Checklist Final Release `v1.0.0` & Kit LinkedIn (`Semana 6 · Dia 42`)

> **Responsável:** Chat 04 (DevOps, QA & Guardião de Commits/PRs)  
> **Branch de Release:** `release/v1.0.0-observability-chaos-launch`  
> **Regra de Segurança Git:** Nenhum agente executa `git push`, `gh`, `git tag` ou altera repositórios remotos automaticamente — todos os comandos abaixo destinam-se à revisão e execução explícita pelo usuário.

---

## 1. ✅ Checklist de Auditoria Final & Clean Boot (`v1.0.0`)

### A. Pré-requisitos de Ambiente & Segurança
- [ ] Confirmar que nenhum arquivo `.env` real, chave privada (`*.pem`, `*.key`) ou credencial produtiva está rastreado no Git (`git status` limpo de segredos).
- [ ] Confirmar Docker Desktop / Docker Engine em execução.
- [ ] Caso a porta `5432` esteja ocupada por PostgreSQL local no Windows, copiar `.env.example` para `.env` e definir `POSTGRES_PORT=5433` (ou parar o serviço local).

### B. Validações Offline & Contratos
- [ ] `docker compose config --quiet` retorna código `0` sem warnings.
- [ ] `py -3.13 infra/scripts/e2e_payment_dlq_flow.py --self-test` retorna `PASS` (`exit 0`).
- [ ] `py -3.13 infra/scripts/chaos_load_test.py --self-test` retorna `PASS` (`exit 0`).
- [ ] JSONs válidos em `infra/grafana/dashboards/ledgerpulse-overview.json` e `docs/contracts/ledgerpulse.postman_collection.json`.

### C. Clean Boot Completo dos 9 Containers
- [ ] Executar reset limpo e rebuild total:
  ```powershell
  docker compose down -v
  docker compose up -d --build
  docker compose ps
  ```
- [ ] Confirmar os 9 containers com status `healthy` (`postgres`, `redis`, `rabbitmq`, `risk-engine-ai`, `ledger-core-api`, `webhook-dispatcher`, `web-console`, `prometheus`, `grafana`).
- [ ] Conferir schemas `ledger_core` e `webhooks` no PostgreSQL:
  ```powershell
  docker compose exec -T postgres psql -U ledgerpulse -d ledgerpulse -c "\dn"
  ```

### D. Suítes Automatizadas por Módulo
- [ ] **Core API (Java 21 + Testcontainers):** `cd services/ledger-core-api && mvn -B verify`
- [ ] **Risk Engine AI (Python):** `cd services/risk-engine-ai && .\.venv\Scripts\python -m ruff check . && .\.venv\Scripts\python -m pytest -v`
- [ ] **Webhook Dispatcher (Java 21):** `cd services/webhook-dispatcher && mvn -B test`
- [ ] **Web Console (React + TS + Vitest):** `cd apps/web-console && npx tsc --noEmit && npm test && npm run build`

### E. Observabilidade, E2E & Chaos Live
- [ ] `http://localhost:9090/targets` exibindo os jobs `ledger-core-api`, `risk-engine-ai` e `webhook-dispatcher` como **`UP`**.
- [ ] `http://localhost:3000` abrindo o Grafana sem clique manual com datasource `Prometheus` (`uid: prometheus`) e dashboard **LedgerPulse Platform — Observability Overview** populado.
- [ ] Executar `py -3.13 infra/scripts/e2e_payment_dlq_flow.py` (`exit 0`).
- [ ] Executar `py -3.13 infra/scripts/chaos_load_test.py` (`exit 0`) e revisar o relatório gerado em `infra/reports/chaos-load-report.md`.
- [ ] Importar `docs/contracts/ledgerpulse.postman_collection.json` no Postman e validar smoke test das coleções.

---

## 2. 🏷️ Comandos Pós-Merge para Tag `v1.0.0` (Execução Manual pelo Usuário)

Após revisar a PR da branch `release/v1.0.0-observability-chaos-launch`, aprovar o pipeline `LedgerPulse CI` verde no GitHub e concluir o merge na `main`:

```powershell
# 1. Atualizar a branch main local após o merge da PR no GitHub
git checkout main
git pull origin main

# 2. Criar tag anotada oficial da release v1.0.0
git tag -a v1.0.0 -m "release: LedgerPulse Platform v1.0.0 (Double-Entry Ledger, ML Fraud, Resilient Webhooks, SSE & Observability)"

# 3. Enviar a tag v1.0.0 para o GitHub (somente quando decidir publicar)
git push origin v1.0.0
```

---

## 3. 📣 3 Posts Técnicos Prontos para LinkedIn (*Build in Public*)

### Post 1 — Double-Entry Ledger, `@Version` & Precisão em Centavos

```text
Por que sistemas financeiros sérios NÃO usam float/double e nem fazem UPDATE direto no saldo da conta? 💳⚙️

Ao construir o núcleo financeiro do LedgerPulse (Java 21 + Spring Boot 3 + PostgreSQL 16), apliquei 4 invariantes que separam um CRUD comum de uma arquitetura Fintech resiliente:

1️⃣ Precisão em centavos inteiros (BIGINT / long / int): zero erro de arredondamento IEEE-754 entre Java, Python e TypeScript.
2️⃣ Double-Entry Ledger Append-Only: a tabela `ledger_entries` nunca recebe UPDATE ou DELETE. Toda liquidação e estorno (total ou parcial) grava novos lançamentos compensatórios garantindo Σ Débitos = Σ Créditos.
3️⃣ Concorrência Otimista (@Version JPA): requisições simultâneas disputando a mesma conta não geram "lost update" — conflitos são detectados deterministicamente (HTTP 409).
4️⃣ Idempotência Distribuída com Redis: header `Idempotency-Key` + hash SHA-256 canônico do payload impede cobranças duplicadas mesmo sob rajadas concorrentes de rede.

Testei tudo isso com suíte multi-thread, Testcontainers (PostgreSQL 16 + Redis 7) e um burst concorrente de 50 threads batendo na mesma Idempotency-Key. Resultado: 1 única transação contábil criada, zero duplicidade.

Qual invariante você considera mais crítico em sistemas de pagamento? 👇

#Java #SpringBoot #Fintech #SoftwareArchitecture #PostgreSQL #DistributedSystems
```

---

### Post 2 — Arquitetura Poliglota: Java 21 + Python ML + Circuit Breaker Resilience4j

```text
Como conectar um core financeiro em Java 21 a um motor antifraude de Machine Learning em Python sem criar um ponto único de falha? 🧠🛡️

No LedgerPulse, separei o domínio transacional do motor de risco em dois microsserviços com contratos OpenAPI 3.1 congelados (Contract-First):

☕ `ledger-core-api` (Java 21 + Spring Boot 3): orquestra pagamentos, split de marketplace e contabilidade.
🐍 `risk-engine-ai` (Python + FastAPI + Scikit-Learn): combina IsolationForest + RandomForestClassifier + regras determinísticas explicáveis (`VELOCITY_SPIKE`, `HIGH_TICKET_NEW_ACCOUNT`, `NIGHT_ANOMALY`), retornando score (0–100), decisão (`APPROVE`/`REVIEW`/`BLOCK`) e `reason_codes`.

O ponto-chave de resiliência está na fronteira entre os dois:
Com Resilience4j (Timeout 1.5s + Retry curto + Circuit Breaker), se o serviço Python degradar ou cair no meio de um checkout:
❌ O pagamento NÃO é perdido.
❌ O pagamento NÃO é aprovado às cegas.
✅ O fallback seguro encaminha automaticamente para `MANUAL_REVIEW` (`RISK_ENGINE_UNAVAILABLE`), permitindo que um analista aprove ou reprove na Central Antifraude do console React — e ao aprovar, o Ledger liquida e publica os eventos normalmente.

Inclusive validei esse cenário na prática com Chaos Testing derrubando o container Python durante o fluxo e observando a transição do Circuit Breaker no Prometheus/Grafana! 📊

#Java21 #Python #FastAPI #MachineLearning #Resilience4j #Microservices #ChaosEngineering
```

---

### Post 3 — Transactional Outbox, Virtual Threads, HMAC-SHA256, RabbitMQ DLQ & Observabilidade

```text
Você confirmou o pagamento no PostgreSQL... e o broker caiu antes de publicar o evento. Como evitar o clássico problema de Dual Write? 🐇🔐

No LedgerPulse, fechei o ciclo assíncrono de ponta a ponta com arquitetura orientada a eventos e observabilidade completa:

📦 Transactional Outbox: todo evento (`payment.settled`, `payment.manual_review_required`, `payment.refunded`) é salvo em `outbox_events` dentro da mesma `@Transactional` do Ledger antes do relay publicar no RabbitMQ.
⚡ Virtual Threads (Java 21) + HMAC-SHA256: o `webhook-dispatcher` consome `ledger.events` e assina cada entrega HTTP com `X-LedgerPulse-Signature: t=<epoch>,v1=<hex_hmac>`.
⏳ Retry Exponencial com TTL + DLQ: falhas de entrega (5xx/timeout) seguem filas com TTL (`5s → 30s → 120s`) antes de estacionar na `ledger.webhooks.dlq` — com Replay manual em 1 clique no Console Web React + feedback real-time via Server-Sent Events (SSE).
📈 Prometheus + Grafana Zero-Click: os 3 serviços expõem métricas customizadas (`ledger_payments_total`, `risk_evaluation_latency_seconds`, `circuit_breaker_state`, `webhook_dlq_total`) coletadas automaticamente no Docker Compose.

Tudo reproduzível em 1 comando (`docker compose up -d --build`) com suítes Maven/Testcontainers, Pytest, Vitest + RTL, E2E e Chaos/Load Testing automatizado.

#RabbitMQ #VirtualThreads #Observability #Prometheus #Grafana #DevOps #SoftwareEngineering
```
