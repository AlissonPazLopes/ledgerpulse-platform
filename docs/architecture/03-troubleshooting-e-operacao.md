# 🛠️ 03 — Catálogo Oficial de Métricas Prometheus & Guia de Troubleshooting (`v1.0.0`)

> **Versão:** `v1.0.0` (Contrato de Observabilidade & Operação — Semana 6)  
> **Responsável:** Chat 01 (Arquiteto de Software & Contratos)  
> **Consumidores:** Chat 02 (instrumentação Micrometer/Prometheus nos 3 serviços) e Chat 04 (scrape `prometheus.yml` + dashboards provisionados Grafana)

---

## 1. 📊 Catálogo Oficial de Métricas Prometheus (`v1.0.0`)

Todas as métricas customizadas abaixo são expostas em formato Prometheus/OpenMetrics sem uso de ponto flutuante monetário (valores em contadores de evento, segundos de latência ou códigos de estado).

### 1.1 `ledger-core-api` (`http://localhost:8080/actuator/prometheus`)

| Métrica | Tipo Micrometer / Prometheus | Labels / Tags | Semântica / Valores |
| :--- | :--- | :--- | :--- |
| `ledger_payments_total` | `Counter` | `status`, `method`, `decision` | Total de pagamentos processados por estado resultante (`SETTLED`, `MANUAL_REVIEW`, `DECLINED`), método (`CARD`, `PIX`, `BOLETO`) e decisão de risco (`APPROVE`, `REVIEW`, `BLOCK`). |
| `ledger_refunds_total` | `Counter` | `status` | Total de estornos compensatórios concluídos (`PARTIALLY_REFUNDED`, `REFUNDED`). |
| `risk_evaluation_latency_seconds` | `Timer` (Histograma) | — *(ou tags de outcome se configuradas)* | Latência da chamada Java → Python ao `risk-engine-ai` (publica `risk_evaluation_latency_seconds_bucket`, `risk_evaluation_latency_seconds_sum` e `risk_evaluation_latency_seconds_count`). |
| `circuit_breaker_state` | `Gauge` | `name="riskEngine"` | Estado do Circuit Breaker Resilience4j: **`0` = `CLOSED`**, **`1` = `OPEN`**, **`2` = `HALF_OPEN`**, **`3` = `OTHER`** (`DISABLED` / `FORCED_OPEN` / `METRICS_ONLY`). |
| `resilience4j_circuitbreaker_*` | Métricas nativas Resilience4j | `name="riskEngine"`, `state`, `kind` | Taxa de falhas, chamadas lentas, transições de estado e chamadas permitidas/recusadas nativas do `resilience4j-micrometer`. |

### 1.2 `risk-engine-ai` (`http://localhost:8001/metrics`)

| Métrica | Tipo Prometheus | Labels / Tags | Semântica / Buckets |
| :--- | :--- | :--- | :--- |
| `risk_model_loaded` | `Gauge` | — | `1` quando o artefato `.joblib` (`IsolationForest` + `RandomForestClassifier`) está carregado e pronto; `0` caso contrário. |
| `risk_evaluation_total` | `Counter` | `decision` | Total de avaliações executadas no endpoint `POST /v1/risk/evaluate` por desfecho (`APPROVE`, `REVIEW`, `BLOCK`). |
| `risk_evaluation_latency_seconds` | `Histogram` | — | Tempo de inferência ML + regras determinísticas com buckets cumulativos oficiais: **`0.005`, `0.01`, `0.025`, `0.05`, `0.1`, `0.25`, `0.5`, `1.0`, `+Inf`** (além de `_sum` e `_count`). |

### 1.3 `webhook-dispatcher` (`http://localhost:8082/actuator/prometheus`)

| Métrica | Tipo Micrometer / Prometheus | Labels / Tags | Semântica |
| :--- | :--- | :--- | :--- |
| `webhook_dlq_total` | `Counter` | `eventType` | Total de entregas que esgotaram os retries e estacionaram em `ledger.webhooks.dlq` por `eventType`. |
| `webhook_deliveries_total` | `Counter` | `status`, `eventType` | Total de resultados de tentativa/entrega por estado (`DELIVERED`, `RETRYING`, `DLQ`) e `eventType`. |
| `webhook_dlq_replays_total` | `Counter` | `status` | Total de replays manuais executados via `POST /api/v1/webhooks/dlq/{deliveryId}/replay` por resultado (`DELIVERED`, `DLQ`, etc.). |
| `webhook_delivery_latency_seconds` | `Timer` (Histograma) | — | Latência das chamadas HTTP de disparo HMAC em Virtual Threads (publica `webhook_delivery_latency_seconds_bucket`, `webhook_delivery_latency_seconds_sum` e `webhook_delivery_latency_seconds_count`). |

---

## 2. 🔍 Consultas PromQL Úteis (Prometheus `:9090` & Grafana `:3000`)

```promql
# 1. Vazão de pagamentos por status (req/min)
sum by (status) (rate(ledger_payments_total[5m])) * 60

# 2. Latência p95 da avaliação de risco no ledger-core-api (segundos)
histogram_quantile(0.95, sum by (le) (rate(risk_evaluation_latency_seconds_bucket{job="ledger-core-api"}[5m])))

# 3. Latência p95 da inferência ML dentro do risk-engine-ai (segundos)
histogram_quantile(0.95, sum by (le) (rate(risk_evaluation_latency_seconds_bucket{job="risk-engine-ai"}[5m])))

# 4. Estado atual do Circuit Breaker riskEngine (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
max(circuit_breaker_state{name="riskEngine"})

# 5. Entregas de webhook enviadas para DLQ por tipo de evento (últimos 15m)
sum by (eventType) (increase(webhook_dlq_total[15m]))

# 6. Latência p95 das entregas HTTP de webhook (segundos)
histogram_quantile(0.95, sum by (le) (rate(webhook_delivery_latency_seconds_bucket[5m])))

# 7. Saúde do modelo ML carregado no risk-engine-ai
min(risk_model_loaded)
```

---

## 3. 🧯 Guia Prático de Troubleshooting & Operação

### 3.1 Porta `5432` ocupada no host Windows ao subir o Docker Compose
- **Sintoma:** `docker compose up -d` falha no container `ledgerpulse-postgres` com erro de bind na porta `0.0.0.0:5432`.
- **Causa:** Um serviço PostgreSQL local instalado no Windows já escuta na porta `5432`.
- **Solução:**
  1. Copie `.env.example` para `.env` na raiz do repositório e defina `POSTGRES_PORT=5433` (mantendo a comunicação interna entre containers na porta `5432` dentro da rede `ledgerpulse-net`); **ou**
  2. Pare o serviço PostgreSQL local do Windows antes de iniciar a stack.
  3. Ao conectar clientes externos no host (DBeaver/psql), use a porta configurada em `POSTGRES_PORT` (`5432` ou `5433`) no database `ledgerpulse` (schemas `ledger_core` e `webhooks`).

### 3.2 Docker Desktop parado vs `LedgerCoreTestcontainersIT`
- **Sintoma:** `mvn -B verify` no host Windows pula ou falha `LedgerCoreTestcontainersIT` com mensagem de daemon Docker indisponível (`//./pipe/dockerDesktopLinuxEngine`).
- **Explicação:** `mvn -B test` executa a suíte unitária/MockMvc em memória usando **H2 `2.2.224` `MODE=PostgreSQL`** com exatamente as mesmas migrations Flyway (`V1`–`V6` / `webhooks V1`). Já `LedgerCoreTestcontainersIT` (sufixo `*IT` no Failsafe) exige Docker ativo para subir PostgreSQL 16 + Redis 7 reais.
- **Solução:** Inicie o **Docker Desktop** no Windows antes de rodar `mvn -B verify` localmente, ou valide a execução completa no GitHub Actions (`ubuntu-latest`).

### 3.3 Pagamentos caindo em `MANUAL_REVIEW` com `RISK_ENGINE_UNAVAILABLE`
- **Sintoma:** `POST /api/v1/payments` retorna `201 Created` com `status = "MANUAL_REVIEW"`, `reasonCodes = ["RISK_ENGINE_UNAVAILABLE"]` e `circuit_breaker_state{name="riskEngine"} == 1` (`OPEN`).
- **Causa:** O serviço `risk-engine-ai` (`:8001`) está parado, reiniciando ou excedendo o timeout Resilience4j de `1.5s`. Por design de segurança financeira, o pagamento **nunca é perdido nem aprovado às cegas**.
- **Solução:**
  1. Verifique `GET http://localhost:8001/health` (`modelLoaded: true`) ou `docker compose logs -f risk-engine-ai`.
  2. Após restabelecer o `risk-engine-ai`, aguarde a janela `waitDurationInOpenState` para o Circuit Breaker transicionar `OPEN (1)` → `HALF_OPEN (2)` → `CLOSED (0)`.
  3. Os pagamentos retidos durante a janela de falha permanecem auditáveis em `GET /api/v1/risk/reviews` e podem ser aprovados/reprovados por `ANALYST`/`ADMIN` na **Central Antifraude** ou via `POST /api/v1/risk/reviews/{paymentId}/decision`.

### 3.4 Webhooks acumulando na DLQ (`ledger.webhooks.dlq`) e Replay Manual
- **Sintoma:** Entregas ficam com `status = "DLQ"` após 4 tentativas (inicial + `5s` → `30s` → `120s`) e `webhook_dlq_total` incrementa.
- **Diagnóstico:**
  1. Inspecione o histórico de `attempts` em `GET /api/v1/webhooks/deliveries` ou no **Console de Webhooks & DLQ** do `web-console` (`lastHttpStatus`, `lastError`).
  2. Verifique a fila `ledger.webhooks.dlq` no RabbitMQ Management UI (`http://localhost:15672`).
  3. Se estiver testando com o **Merchant Webhook Sink** local em `FAIL_500` ou `TIMEOUT`, consulte `GET /api/v1/sink/mode`.
- **Recuperação:**
  1. Restaure o destino ou altere o Sink para `OK_200` via `PUT /api/v1/sink/mode` (`{ "mode": "OK_200" }`).
  2. Execute o replay via UI (**Replay DLQ**) ou `POST /api/v1/webhooks/dlq/{deliveryId}/replay` (requer role `ADMIN` ou `ANALYST`).
  3. Confirme transição para `DELIVERED` e incremento em `webhook_dlq_replays_total{status="DELIVERED"}`.

### 3.5 Stream Real-Time SSE (`GET /api/v1/stream/events`) com JWT
- **Sintoma:** Tentativa de conectar usando `new EventSource(...)` falha com `401 Unauthorized`.
- **Causa:** A API `EventSource` nativa dos navegadores não permite enviar o header `Authorization: Bearer <jwt>`.
- **Solução:** O `web-console` (e qualquer cliente) deve consumir `GET /api/v1/stream/events` via `fetch()` + `ReadableStream` com `Authorization: Bearer <jwt>` e `Accept: text/event-stream`. Em proxies reversos futuros, desabilitar buffering (`X-Accel-Buffering: no`) para não reter eventos `connected`, `heartbeat` e eventos de domínio.

### 3.6 Clean Boot Completo (`down -v` + Rebuild) e Verificação de Saúde
Quando precisar resetar completamente volumes de banco, Redis, RabbitMQ e métricas e subir os **9 containers** do zero:

```bash
docker compose down -v
docker compose up -d --build
docker compose ps
```

**Checklist rápido de verificação pós-boot:**
1. Todos os serviços executáveis e de infra com status `healthy` / `running` em `docker compose ps`.
2. `GET http://localhost:8080/api/v1/health` → `200 UP`.
3. `GET http://localhost:8001/health` → `200 { "status": "ok", "modelLoaded": true }`.
4. `GET http://localhost:8082/api/v1/health` → `200 { "status": "UP", "virtualThreadsEnabled": true }`.
5. `http://localhost:9090/targets` → targets `ledger-core-api`, `risk-engine-ai` e `webhook-dispatcher` como **`UP`**.
6. `http://localhost:3000` → Grafana abre com datasource Prometheus conectado e dashboards provisionados sem clique manual.
7. `http://localhost:5173` → `web-console` acessível (`admin@ledgerpulse.dev` / `LedgerPulse@2026`).
