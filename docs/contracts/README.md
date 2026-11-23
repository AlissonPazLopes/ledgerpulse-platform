# 📜 Contratos da Plataforma (`docs/contracts/`) — Padrão *Contract-First*

> **Status Atual:** `v1.0-frozen` — Contratos das **Semanas 1, 2, 3 e 4 congelados** antes da implementação dos Dias 03–28.

Este diretório é a **única fonte da verdade** para comunicação entre:
- **`apps/web-console`** (Chat 03) ↔ **`services/ledger-core-api`** (Chat 02)
- **`apps/web-console`** (Chat 03) ↔ **`services/webhook-dispatcher`** (Chat 02)
- **`services/ledger-core-api`** (Chat 02) ↔ **`services/risk-engine-ai`** (Chat 02)
- **`services/ledger-core-api`** (Chat 02) ↔ **`RabbitMQ`** ↔ **`services/webhook-dispatcher`** (Chat 02)

---

## 🔄 Fluxo Obrigatório *Contract-First*

1. **Definição & Congelamento (Chat 01):** Antes da semana de implementação começar, o **Chat 01** cria ou atualiza o contrato aqui (`api-v1-overview.md`, `openapi-ledger-core-v1.yaml`, `openapi-risk-engine-v1.yaml`, `openapi-webhook-dispatcher-v1.yaml`, `events-rabbitmq.md` e fixtures em `contract-samples/`).
2. **Execução em Paralelo (Chat 02 + Chat 03):**
   - O **Chat 02** implementa os endpoints/DTOs Java (`record`) e Pydantic v2 seguindo exatamente os nomes de campos, tipos e códigos HTTP definidos.
   - O **Chat 03** implementa tipos TypeScript (`types/`), mocks de UI e chamadas HTTP já sabendo o formato exato da resposta.
3. **Pedido de Alteração de Contrato:** Se durante a implementação o Chat 02 ou Chat 03 descobrir necessidade de novo campo ou ajuste de payload:
   - Reportar ao **Chat 00** / registrar em [docs/STATUS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/STATUS.md);
   - O **Chat 01** atualiza o contrato aqui antes de mergear mudanças divergentes.

---

## 📂 Arquivos de Contrato

| Arquivo | Escopo | Status |
| :--- | :--- | :---: |
| [api-v1-overview.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/api-v1-overview.md) | Convenções HTTP, naming (`camelCase`/`snake_case`), paginação padrão, vocabulário `reason_codes`, catálogo RFC 7807 `ProblemDetail` e payloads congelados (Semanas 1, 2, 3 e 4). | `v1.0-frozen` (S1–S4) |
| [openapi-ledger-core-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-ledger-core-v1.yaml) | Especificação OpenAPI `3.1.0` do `ledger-core-api` (Health, Auth, Admin, Merchants, Accounts, Ledger, Payments, Risk Review, `ProblemDetail`, `bearerAuth` + `apiKeyAuth`). | `v1.0-frozen` (S1–S3) |
| [openapi-risk-engine-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-risk-engine-v1.yaml) | Especificação OpenAPI `3.1.0` do `risk-engine-ai` (`/health`, `/v1/risk/model-info`, `/v1/risk/evaluate` em `snake_case`, `/metrics`). | `v1.0-frozen` (S3) |
| [openapi-webhook-dispatcher-v1.yaml](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/openapi-webhook-dispatcher-v1.yaml) | Especificação OpenAPI `3.1.0` do `webhook-dispatcher` (Health, Endpoints, Deliveries, Replay DLQ `{deliveryId}`, Webhook Sink, `ProblemDetail`, `bearerAuth`). | `v1.0-frozen` (S4) |
| [risk-evaluate-contract.json](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/contract-samples/risk-evaluate-contract.json) | Fixture JSON canônica compartilhada para testes de contrato cruzado Java DTO ↔ Python Pydantic v2 (`request` + respostas `approve`/`review`/`block`). | `v1.0-frozen` (S3) |
| [events-rabbitmq.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/events-rabbitmq.md) | Topologia de exchanges, filas de retry TTL com escalonamento via `x-death` e retorno direto, DLQ, integração Outbox V3 e envelope JSON padrão. | `v1.0-frozen` |
| `ledgerpulse.postman_collection.json` | Coleção completa Postman/Insomnia *(será gerada/exportada na Semana 6 / Dia 39)*. | ⏳ Semana 6 |
