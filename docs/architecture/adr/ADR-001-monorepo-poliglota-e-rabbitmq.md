# ADR-001 — Monorepo Poliglota (Java 21 + Python FastAPI + React/TS) e RabbitMQ

- **Status:** Aceito
- **Data:** 2026-11-20
- **Decisor:** Chat 01 (Arquiteto de Software & Contratos)

---

## 1. Contexto

O `LedgerPulse` precisa combinar em um único projeto de portfólio (construído ao longo de 6 semanas):
1. Um núcleo financeiro transacional fortemente tipado e seguro (Java 21 + Spring Boot 3);
2. Um motor antifraude baseado em Machine Learning + regras explicáveis (ecossistema Python + Scikit-Learn);
3. Um disparador de webhooks assíncrono com retentativas exponenciais e Dead-Letter Queue (DLQ);
4. Um console web em tempo real (React + TypeScript).

Além disso, o projeto será operado por **5 chats especialistas** em paralelo dentro de um mesmo Project no Antigravity e precisa subir com 1 comando local (`docker compose up`) em máquina Windows de desenvolvimento sem consumir memória excessiva.

---

## 2. Decisão

1. **Adotar estrutura de Monorepo em pasta única (`ledgerpulse-platform`)** dividida por ownership claro:
   - `services/ledger-core-api` (Java 21 / Spring Boot 3.3+)
   - `services/risk-engine-ai` (Python 3.13 local / `python:3.12-slim` Docker / FastAPI / Scikit-Learn)
   - `services/webhook-dispatcher` (Java 21 com Virtual Threads / Spring AMQP)
   - `apps/web-console` (React / TypeScript / Vite / Tailwind)
   - `infra/` e `docs/`
2. **Adotar RabbitMQ 3 como broker de mensageria** em vez de Apache Kafka.

---

## 3. Alternativas Consideradas

| Opção | Vantagens | Desvantagens / Por que não escolhida |
| :--- | :--- | :--- |
| **Multi-repositório (4 repos separados)** | Isolamento físico de pipelines. | Dificulta coordenação entre os 5 chats, versionamento conjunto de contratos OpenAPI/Eventos e execução local unificada com Docker Compose. |
| **Tudo em Java (sem Python)** | Apenas uma linguagem no backend. | Perde o diferencial de ML real com `Scikit-Learn` + `FastAPI` e comunicação poliglota protegida por Circuit Breaker. |
| **Apache Kafka em vez de RabbitMQ** | Alto throughput de streaming particionado. | Maior consumo de RAM local (Zookeeper/KRaft + broker) e modelagem mais trabalhosa para filas nativas de **Retry com TTL exponencial + DLQ** por endpoint de webhook. |

---

## 4. Consequências

### Positivas
- **Governança limpa entre chats:** Cada chat possui diretório exclusivo de escrita (`AGENTS.md`), permitindo evolução paralela de Back-end, IA, Front-end e Infra.
- **Retry/DLQ elegantes:** Com RabbitMQ (`Dead-Letter Exchanges` + filas de TTL `5s`, `30s`, `120s`), o mecanismo de backoff exponencial e replay fica transparente, observável na UI de gerenciamento (`15672`) e fácil de auditar.
- **Portfólio poliglota de alto impacto:** Demonstra Java 21 (Virtual Threads + Resilience4j) conversando com Python ML (FastAPI) e React/TypeScript em um ambiente Dockerizado com Prometheus + Grafana.

### Trade-offs / Cuidados
- É obrigatório manter pipelines separados por módulo no GitHub Actions (`.github/workflows/ci.yml`) usando `paths` ou jobs independentes para não lentificar PRs focadas em um único módulo.
- Contratos HTTP e de eventos devem ser versionados em `docs/contracts/` antes de codar cada semana (*Contract-First*).
