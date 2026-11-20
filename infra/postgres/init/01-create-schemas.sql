-- =============================================================================
-- LedgerPulse Platform — Inicialização de Schemas PostgreSQL 16
-- Database único (`ledgerpulse`) com schemas isolados:
--   - ledger_core: domínio financeiro, double-entry ledger, pagamentos e outbox
--   - webhooks:    assinaturas, entregas HTTP, retries e controle de replay/DLQ
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS ledger_core;
CREATE SCHEMA IF NOT EXISTS webhooks;
