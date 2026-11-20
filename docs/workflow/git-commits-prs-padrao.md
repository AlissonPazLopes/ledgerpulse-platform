# 🛠️ Padrão de Engenharia Git: Issues, Branches, Commits, PRs & LinkedIn

> **Responsável:** Chat 04 (DevOps, QA & Guardião de Commits/PRs)  
> **Padrão de Fluxo:** GitHub Flow com micro-commits semânticos atômicos (5–8 commits reais por entrega diária).

---

## 1. 🛡️ Regra Zero de Segurança

- Nenhum chat executa `git push`, cria `remote`, abre/fecha Issues ou abre/faz merge de Pull Requests no GitHub sem pedido explícito do usuário.
- Quando o usuário for subir para o GitHub, este guia padroniza exatamente como estruturar cada entrega diária.

---

## 2. 🌿 Fluxo de Branches (GitHub Flow)

A branch `main` representa sempre código **compilando e com testes 100% verdes**. Toda entrega do [ROADMAP-6-SEMANAS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/ROADMAP-6-SEMANAS.md) nasce em uma branch temática:

| Prefixo | Uso | Exemplo |
| :--- | :--- | :--- |
| `feat/` | Nova funcionalidade de domínio, endpoint ou tela | `feat/ledger-double-entry-entities` |
| `fix/` | Correção de bug real encontrado em teste ou revisão | `fix/outbox-retry-ordering` |
| `test/` | Suíte dedicada de testes unitários, Testcontainers, E2E ou caos | `test/e2e-full-payment-dlq-flow` |
| `perf/` | Otimização SQL, índices, cache ou Rate Limiting | `perf/rate-limit-redis-and-sql-indexes` |
| `ci/` | Alterações em GitHub Actions | `ci/python-risk-and-contract-tests` |
| `docs/` | Arquitetura, ADRs, contratos OpenAPI, README | `docs/contracts-v1-freeze` |
| `chore/` | Infraestrutura Docker, Makefile, setup inicial | `chore/docker-compose-base-infra` |

---

## 3. ✍️ Convenção de Micro-Commits Semânticos (5–8 por Dia)

Seguimos **Conventional Commits** com escopo claro (`core`, `risk`, `webhook`, `web`, `infra`, `docs`, `ci`), separando os commits por **camada/arquivo lógico** (`git add <arquivo>`), nunca `git add .` num commit monolítico:

```text
chore(infra): add postgres redis and rabbitmq services to docker-compose
feat(core): add Account and LedgerEntry domain entities with Optimistic Locking
feat(core): enforce balanced double-entry invariant in LedgerService
test(core): add multi-thread concurrency test verifying HTTP 409 on lost update
feat(risk): expose POST /v1/risk/evaluate with hybrid ML and rule reason_codes
feat(web): add real-time executive dashboard consuming SSE stream
docs(arch): add ADR-002 covering double-entry ledger and transactional outbox
```

### Ordem recomendada de commits em uma entrega de API / Tela:
1. `docs(...)` ou `chore(...)`: contrato/migration/configuração inicial.
2. `feat(...)`: entidades / tipos / enums de domínio.
3. `feat(...)`: repositórios / serviços / regras de negócio.
4. `feat(...)`: controllers REST / componentes UI.
5. `test(...)`: testes unitários e de integração.
6. `docs(...)`: atualização pontual de README ou `STATUS.md` quando aplicável.

---

## 4. 📋 Templates Padrão (Issue & Pull Request)

### Template de Issue Diária (`Dia N`)
```markdown
## 🎯 Objetivo (Dia N — Semana X)
Descrever em 2 linhas o problema técnico resolvido hoje.

## ✅ Escopo & Critérios de Aceite
- [ ] Item técnico 1 (seguindo contrato em `docs/contracts/`)
- [ ] Item técnico 2
- [ ] Testes automatizados passando (`mvn test` / `pytest` / `npm test`)

## 🏷️ Metadados
- Módulo(s): `ledger-core-api` / `risk-engine-ai` / `webhook-dispatcher` / `web-console`
- Chat responsável: Chat 02 / Chat 03 / Chat 04
```

### Template de Pull Request
```markdown
Closes #<NUMERO_DA_ISSUE>

## 📌 O que foi feito
- Resumo objetivo das mudanças implementadas neste dia.

## 🧪 Evidências de Verificação
- [ ] `mvn test` / `mvn verify` (se aplicável)
- [ ] `pytest` (se aplicável)
- [ ] `npm run build` / `npm test` (se aplicável)
- [ ] `docker compose` validado (se aplicável)

## 🔍 Notas Arquiteturais
- Referência ao contrato ou ADR seguido (ex.: `ADR-002`).
```

---

## 5. 📣 Cadência Semanal "Build in Public" (LinkedIn)

Para maximizar visibilidade para recrutadores sem perder tempo diário, publique **1 post técnico por semana** (ao fechar cada marco de sexta/sábado):

| Semana | Tema Forte do Post no LinkedIn |
| :---: | :--- |
| **Semana 1** | *Arquitetura Contract-First & Monorepo Poliglota* (como projetar um sistema Fintech com Java 21, Python e React sem virar bagunça). |
| **Semana 2** | *Por que sistemas financeiros não usam `float` e nem `UPDATE` no saldo* (Double-Entry Ledger + `@Version` + Idempotência Redis + Transactional Outbox). |
| **Semana 3** | *Integrando Java 21 com IA em Python (FastAPI + Scikit-Learn) sem ponto único de falha* (Resilience4j Circuit Breaker com fallback para revisão manual). |
| **Semana 4** | *Webhooks seguros em escala com Virtual Threads, HMAC-SHA256 e DLQ no RabbitMQ* (Retry exponencial `5s → 30s → 120s` e replay). |
| **Semana 5** | *Tempo Real sem Polling: Server-Sent Events (SSE), Estornos Compensatórios e Conciliação Contábil automatizada*. |
| **Semana 6** | *Case Completo + Demo ao Vivo*: Prometheus, Grafana e Chaos Engineering provando resiliência na prática. |
