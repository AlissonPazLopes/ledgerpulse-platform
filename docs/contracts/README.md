# 📜 Contratos da Plataforma (`docs/contracts/`) — Padrão *Contract-First*

Este diretório é a **única fonte da verdade** para comunicação entre:
- **`apps/web-console`** (Chat 03) ↔ **`services/ledger-core-api`** (Chat 02)
- **`services/ledger-core-api`** (Chat 02) ↔ **`services/risk-engine-ai`** (Chat 02)
- **`services/ledger-core-api`** (Chat 02) ↔ **`RabbitMQ`** ↔ **`services/webhook-dispatcher`** (Chat 02)

---

## 🔄 Fluxo Obrigatório *Contract-First*

1. **Definição & Congelamento (Chat 01):** Antes da semana de implementação começar, o **Chat 01** cria ou atualiza o contrato aqui (`api-v1-overview.md`, `events-rabbitmq.md` ou schemas OpenAPI/JSON).
2. **Execução em Paralelo (Chat 02 + Chat 03):**
   - O **Chat 02** implementa os endpoints/DTOs Java (`record`) e Pydantic v2 seguindo exatamente os nomes de campos, tipos e códigos HTTP definidos.
   - O **Chat 03** implementa tipos TypeScript (`types/`), mocks de UI e chamadas HTTP já sabendo o formato exato da resposta.
3. **Pedido de Alteração de Contrato:** Se durante a implementação o Chat 02 ou Chat 03 descobrir necessidade de novo campo ou ajuste de payload:
   - Reportar ao **Chat 00** / registrar em [docs/STATUS.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/STATUS.md);
   - O **Chat 01** atualiza o contrato aqui antes de mergear mudanças divergentes.

---

## 📂 Arquivos de Contrato

- [api-v1-overview.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/api-v1-overview.md) — Convenções HTTP, autenticação, headers obrigatórios, RFC 7807 `ProblemDetail` e mapa de endpoints v1 dos 3 serviços.
- [events-rabbitmq.md](file:///C:/Users/aliss/OneDrive/Documentos/AlissonDev/Estudo_Java/ProjetosGitHub/ledgerpulse-platform/docs/contracts/events-rabbitmq.md) — Topologia de exchanges, filas de retry TTL, DLQ e envelope JSON padrão dos eventos.
- `ledgerpulse.postman_collection.json` *(será gerado/exportado na Semana 6 / Dia 39)*.
