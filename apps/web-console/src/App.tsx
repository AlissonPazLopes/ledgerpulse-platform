import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { LayoutShell } from './components/LayoutShell';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';
import { DashboardPlaceholderPage } from './pages/DashboardPlaceholderPage';
import { AccountsPage } from './pages/AccountsPage';
import { CheckoutSimulatorPage } from './pages/CheckoutSimulatorPage';
import { FraudReviewCenterPage } from './pages/FraudReviewCenterPage';
import { LedgerAuditPage } from './pages/LedgerAuditPage';
import { LoginPage } from './pages/LoginPage';
import { ModulePlaceholderPage } from './pages/ModulePlaceholderPage';
import { WebhooksConsolePage } from './pages/WebhooksConsolePage';

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
              <Route path="/fraud-review" element={<FraudReviewCenterPage />} />
              <Route path="/webhooks" element={<WebhooksConsolePage />} />
              <Route path="/checkout-simulator" element={<CheckoutSimulatorPage />} />
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
