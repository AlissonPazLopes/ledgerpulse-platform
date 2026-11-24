import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { LayoutShell } from './components/LayoutShell';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';
import { AccountsPage } from './pages/AccountsPage';
import { CheckoutSimulatorPage } from './pages/CheckoutSimulatorPage';
import { ExecutiveDashboardPage } from './pages/ExecutiveDashboardPage';
import { FraudReviewCenterPage } from './pages/FraudReviewCenterPage';
import { LedgerAuditPage } from './pages/LedgerAuditPage';
import { LoginPage } from './pages/LoginPage';
import { ReconciliationPage } from './pages/ReconciliationPage';
import { WebhooksConsolePage } from './pages/WebhooksConsolePage';

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute />}>
            <Route element={<LayoutShell />}>
              <Route path="/" element={<ExecutiveDashboardPage />} />
              <Route path="/ledger" element={<LedgerAuditPage />} />
              <Route path="/accounts" element={<AccountsPage />} />
              <Route path="/fraud-review" element={<FraudReviewCenterPage />} />
              <Route path="/webhooks" element={<WebhooksConsolePage />} />
              <Route path="/checkout-simulator" element={<CheckoutSimulatorPage />} />
              <Route path="/reconciliation" element={<ReconciliationPage />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
