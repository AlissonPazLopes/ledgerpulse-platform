import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { LayoutShell } from './components/LayoutShell';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';
import { DashboardPlaceholderPage } from './pages/DashboardPlaceholderPage';
import { AccountsPage } from './pages/AccountsPage';
import { LedgerAuditPage } from './pages/LedgerAuditPage';
import { LoginPage } from './pages/LoginPage';
import { ModulePlaceholderPage } from './pages/ModulePlaceholderPage';

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute />}>
            <Route element={<LayoutShell />}>
              <Route path="/" element={<DashboardPlaceholderPage />} />
              <Route path="/ledger" element={<LedgerAuditPage />} />
              <Route path="/accounts" element={<AccountsPage />} />
              <Route
                path="/fraud-review"
                element={
                  <ModulePlaceholderPage
                    title="Central Antifraude"
                    subtitle="Fila operacional de pagamentos retidos em MANUAL_REVIEW pelo risk-engine-ai ou pelo fallback Resilience4j."
                    schedule="Semana 3 · Dia 20"
                    allowedRoles={['ADMIN', 'ANALYST']}
                    endpoints={[
                      'GET /api/v1/risk/reviews',
                      'POST /api/v1/risk/reviews/{paymentId}/decision',
                    ]}
                    highlights={[
                      'Medidor visual de riskScore (0–100) e chips explicáveis de reasonCodes.',
                      'Modal de decisão APPROVE / DECLINE com justificativa auditável.',
                      'Bloqueio de ações mutáveis para perfil VIEWER conforme RBAC.',
                    ]}
                  />
                }
              />
              <Route
                path="/webhooks"
                element={
                  <ModulePlaceholderPage
                    title="Console de Webhooks & DLQ"
                    subtitle="Administração de destinos HMAC-SHA256, seletor de modo do Webhook Sink local e reprocessamento manual de mensagens em DLQ."
                    schedule="Semana 4 · Dia 26"
                    allowedRoles={['ADMIN', 'ANALYST']}
                    endpoints={[
                      'GET /api/v1/webhooks/endpoints',
                      'POST /api/v1/webhooks/endpoints',
                      'GET /api/v1/webhooks/deliveries',
                      'POST /api/v1/webhooks/dlq/{deliveryId}/replay',
                      'PUT /api/v1/sink/mode',
                    ]}
                    highlights={[
                      'Histórico de tentativas de entrega com latência, código HTTP e assinatura HMAC.',
                      'Controle do Webhook Sink (OK_200, FAIL_500, TIMEOUT) para demonstrações.',
                      'Replay manual de DLQ em 1 clique usando {deliveryId}.',
                    ]}
                  />
                }
              />
              <Route
                path="/checkout-simulator"
                element={
                  <ModulePlaceholderPage
                    title="Simulador de Checkout & Split"
                    subtitle="Laboratório interativo de criação de cobranças com cálculo de split percentual/fixo e validação de Idempotency-Key."
                    schedule="Semana 4 · Dia 27"
                    allowedRoles={['ADMIN', 'ANALYST']}
                    endpoints={[
                      'POST /api/v1/payments',
                      'GET /api/v1/payments/{id}',
                      'POST /api/v1/payments/{id}/refunds',
                    ]}
                    highlights={[
                      'Construção dinâmica de splits PERCENTAGE (percentageBps) ou FIXED (amountCents).',
                      'Reenvio controlado com a mesma Idempotency-Key para testar replay idempotente e conflito 409.',
                      'Linha do tempo visual: Risco -> Ledger -> Outbox -> Webhook.',
                    ]}
                  />
                }
              />
              <Route
                path="/reconciliation"
                element={
                  <ModulePlaceholderPage
                    title="Integridade & Conciliação"
                    subtitle="Auditoria contábil automática comparando saldos em accounts, entradas do ledger e pagamentos liquidados."
                    schedule="Semana 5 · Dia 33"
                    allowedRoles={['ADMIN']}
                    endpoints={[
                      'POST /api/v1/reconciliation/run',
                      'GET /api/v1/reconciliation/reports',
                      'GET /api/v1/reconciliation/export.csv',
                    ]}
                    highlights={[
                      'Indicador visual de integridade contábil do Ledger.',
                      'Disparo de conciliação sob demanda para perfil ADMIN.',
                      'Exportação de relatório auditável em formato CSV.',
                    ]}
                  />
                }
              />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
