# 🤖 Guia de Governança Multi-Chat e Regras do Projeto (`ledgerpulse-platform`)

Este repositório centraliza a plataforma **`LedgerPulse`** — um projeto Flagship Full-Stack & Distribuído de engenharia financeira (Fintech) desenhado para ser construído em **6 semanas (42 dias úteis)** por **5 chats especialistas** trabalhando de forma coordenada, com **zero conflito de edição** e **contratos congelados antes do código**.

---

## 💬 Mapa dos 5 Chats do Projeto

| Chat / Agente | Role | Conversation ID | Link Direto | Responsabilidade Principal |
| :--- | :--- | :--- | :--- | :--- |
| **00 · Orquestrador & PM** | `coordinator` | `720c3e28-4bb1-4035-a94a-bd847a462fb4` | [Abrir Chat 00](conversation://720c3e28-4bb1-4035-a94a-bd847a462fb4) | Coordenação diária, atualização do [STATUS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/STATUS.md), registro em [chats-registry.json](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/chats-registry.json), revisão cruzada e destravamento de handoffs entre chats. |
| **01 · Arquiteto & Contratos** | `arquiteto-contratos` | `1f069b0d-7bd8-43f4-ad0b-1c2bd8c4964d` | [Abrir Chat 01](conversation://1f069b0d-7bd8-43f4-ad0b-1c2bd8c4964d) | Arquitetura, ADRs, modelagem SQL/Flyway conceitual, contratos OpenAPI v1, topologia RabbitMQ, máquina de estados e revisão de segurança/performance. |
| **02 · Back-end & IA** | `dev-backend-ia` | `8e341ebf-e673-49c9-b074-017c4bbdda1a` | [Abrir Chat 02](conversation://8e341ebf-e673-49c9-b074-017c4bbdda1a) | Implementação do `ledger-core-api` (Java 21 + Spring Boot 3), `risk-engine-ai` (Python 3.13 + FastAPI + Scikit-Learn) e `webhook-dispatcher` (Java 21 + Spring AMQP + Virtual Threads). |
| **03 · Front-end & UI/UX** | `dev-frontend-ux` | `8020df47-d8ba-423c-be51-d93304eebe2a` | [Abrir Chat 03](conversation://8020df47-d8ba-423c-be51-d93304eebe2a) | Implementação do `web-console` (React + TypeScript + Vite + Tailwind), Dashboard SSE real-time, Auditoria do Ledger, Antifraude/Revisão Manual, Console DLQ e Checkout Simulator. |
| **04 · DevOps, QA & Guardião** | `devops-qa-guardiao` | `88754f56-ee9a-4aca-8556-47a905701fbb` | [Abrir Chat 04](conversation://88754f56-ee9a-4aca-8556-47a905701fbb) | `docker-compose.yml`, Prometheus/Grafana, pipelines GitHub Actions CI, testes E2E / Load & Chaos, padrão de Issues, branches, micro-commits semânticos, PRs e posts LinkedIn. |

> **Bootstrap concluído:** Todos os 5 chats estão ativos e registrados em [chats-registry.json](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/chats-registry.json) conforme [BOOTSTRAP_CHATS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/BOOTSTRAP_CHATS.md).

---

## 🗂️ Matriz de Ownership de Escrita (Zero Conflito)

Para que vários chats possam trabalhar no mesmo repositório sem sobrescrever o trabalho um do outro, **cada chat só pode criar/editar arquivos dentro da sua área de ownership**:

| Chat | Caminhos Autorizados (Ownership Exclusivo) | Pode Ler |
| :--- | :--- | :--- |
| **00 · Orquestrador & PM** | `docs/STATUS.md`, `docs/chats-registry.json`, ajustes de coordenação no `AGENTS.md` | Todo o repositório |
| **01 · Arquiteto & Contratos** | `docs/architecture/**`, `docs/contracts/**` | Todo o repositório |
| **02 · Back-end & IA** | `services/ledger-core-api/**`, `services/risk-engine-ai/**`, `services/webhook-dispatcher/**` | Todo o repositório |
| **03 · Front-end & UI/UX** | `apps/web-console/**` | Todo o repositório |
| **04 · DevOps, QA & Guardião** | `infra/**`, `.github/**`, `docker-compose.yml`, `.env.example`, `Makefile`, `docs/workflow/**`, `README.md` | Todo o repositório |

> **Regra de Handoff:** Se um chat precisar de alteração fora do seu ownership (por exemplo, o Back-end precisar ajustar um endpoint no contrato ou uma variável no `docker-compose.yml`), ele **não edita o arquivo diretamente** — solicita via Chat 00 ou registra o pedido em [STATUS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/STATUS.md) para o dono correto executar.

---

## 🛡️ Regras de Engenharia, Segurança & Ética Profissional

1. **Zero modificação remota não solicitada:** Nenhum agente executa `git push`, `gh issue`, `gh pr`, adiciona `remote` ou altera repositórios remotos no GitHub sem pedido explícito do usuário.
2. **Originalidade de Domínio (Clean Room Design):** 100% domínio original de engenharia financeira (Pagamentos, Split de Marketplace, Ledger de Partidas Dobradas, Antifraude ML e Webhooks HMAC/DLQ). É estritamente proibido reproduzir regras específicas, layouts, textos ou código de clientes da Empresa Júnior ou de outros repositórios privados.
3. **Fluxo *Contract-First*:** O **Chat 01** define e congela contratos em `docs/contracts/` e ADRs em `docs/architecture/adr/` **antes** do **Chat 02** (Back-end/IA) e do **Chat 03** (Front-end) iniciarem a codificação daquela semana.
4. **Invariantes Inegociáveis do Ledger Financeiro:**
   - **Append-Only:** Lançamentos contábeis (`ledger_entries`) nunca sofrem `UPDATE` ou `DELETE`. Correções e estornos (`REFUND`) sempre geram novos lançamentos compensatórios imutáveis.
   - **Equilíbrio Contábil:** Para toda transação (`ledger_transactions`), a soma dos débitos deve ser estritamente igual à soma dos créditos ($\sum \text{Débitos} = \sum \text{Créditos}$).
   - **Precisão Monetária:** Valores financeiros são **sempre** representados em centavos inteiros (`BIGINT` no PostgreSQL / `long` `amountCents` em Java / `int` `amount_cents` em Python / `number` inteiro no TypeScript). **Proibido usar `float` ou `double` para dinheiro.**
   - **Concorrência & Idempotência:** Atualizações de saldo protegido usam `@Version` (*Optimistic Locking*); requisições mutáveis críticas exigem `Idempotency-Key` via Redis; eventos de domínio usam **Transactional Outbox** no PostgreSQL antes de chegar ao RabbitMQ.
5. **Micro-Entregas Reais e Verificadas:** Cada dia do [ROADMAP-6-SEMANAS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/ROADMAP-6-SEMANAS.md) (20–35 min/dia) deve terminar compilando e com todos os testes da entrega passando. Nunca inventar ou pular testes para "manter commits".
6. **Padronização de Toolchain (Ambiente Windows Local vs Docker):**
   - **Java / Maven:** Java `21.0.6` LTS e Apache Maven `3.9.10` (`services/ledger-core-api` e `services/webhook-dispatcher`).
   - **Python / FastAPI / ML:** Na máquina Windows, `python` padrão está na versão `3.14`; **usar explicitamente `py -3.13 -m venv .venv`** ao criar o ambiente virtual local de `services/risk-engine-ai` por estabilidade de *wheels* de ML (`scikit-learn`, `numpy`, `scipy`). Nos `Dockerfile`s Python, usar `python:3.12-slim`.
   - **Node / Front-end:** Node `v24.12.0` e npm `11.4.2` (`apps/web-console`).
   - **Containers & Git:** Docker `28.3.2` e Git `2.49.0` (branch padrão `main`).
