import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuth } from '../context/AuthContext';
import { reconciliationService } from '../services/reconciliationService';
import type { ReconciliationReportResponse } from '../types/api';
import { ReconciliationPage } from './ReconciliationPage';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../services/reconciliationService', () => ({
  reconciliationService: {
    listReports: vi.fn(),
    runReconciliation: vi.fn(),
    downloadCsvReport: vi.fn(),
  },
}));

const BALANCED_REPORT: ReconciliationReportResponse = {
  id: 'c1d2e3f4-1111-4222-8333-112233445566',
  status: 'BALANCED',
  checkedAccountsCount: 4,
  divergentAccountsCount: 0,
  checkedTransactionsCount: 12,
  unbalancedTransactionsCount: 0,
  totalSettledPaymentsCents: 150000,
  totalLedgerSettlementsCents: 150000,
  totalRefundsCents: 5000,
  totalLedgerRefundsCents: 5000,
  discrepancyCents: 0,
  triggerSource: 'MANUAL',
  findings: [],
  correlationId: '4f3c9f62-8b9a-4f3b-9d1a-0192837465ab',
  createdAt: '2026-11-24T14:40:00Z',
};

const DIVERGENT_REPORT: ReconciliationReportResponse = {
  ...BALANCED_REPORT,
  id: 'd9e8f7a6-1111-4222-8333-112233445566',
  status: 'DIVERGENT',
  divergentAccountsCount: 1,
  discrepancyCents: 2500,
  findings: ['Conta 7b92c3e0 divergiu em 2500 centavos.'],
};

describe('ReconciliationPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue({
      user: {
        id: '11111111-1111-4111-8111-111111111111',
        name: 'LedgerPulse Admin',
        email: 'admin@ledgerpulse.dev',
        role: 'ADMIN',
      },
      token: 'mock-jwt',
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('displays "Ledger 100% Balanceado" for BALANCED report and runs manual reconciliation + CSV export for ADMIN', async () => {
    const user = userEvent.setup();
    vi.mocked(reconciliationService.listReports).mockResolvedValue({
      items: [BALANCED_REPORT],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(reconciliationService.runReconciliation).mockResolvedValue(BALANCED_REPORT);
    vi.mocked(reconciliationService.downloadCsvReport).mockResolvedValue();

    render(<ReconciliationPage />);

    expect(await screen.findByText('Ledger 100% Balanceado')).toBeInTheDocument();

    const runButton = screen.getByRole('button', { name: /executar conciliação/i });
    expect(runButton).toBeEnabled();
    await user.click(runButton);

    await waitFor(() => {
      expect(reconciliationService.runReconciliation).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByRole('button', { name: /exportar csv/i }));
    await waitFor(() => {
      expect(reconciliationService.downloadCsvReport).toHaveBeenCalledTimes(1);
    });
  });

  it('displays discrepancy and findings for DIVERGENT report and enforces ADMIN RBAC for VIEWER', async () => {
    vi.mocked(useAuth).mockReturnValue({
      user: {
        id: '11111111-1111-4111-8111-111111111113',
        name: 'LedgerPulse Viewer',
        email: 'viewer@ledgerpulse.dev',
        role: 'VIEWER',
      },
      token: 'mock-jwt',
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    vi.mocked(reconciliationService.listReports).mockResolvedValue({
      items: [DIVERGENT_REPORT],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    render(<ReconciliationPage />);

    expect(await screen.findByText('Divergência Contábil Detectada')).toBeInTheDocument();
    expect(screen.getAllByText('R$ 25,00').length).toBeGreaterThan(0);
    expect(screen.getByText('Conta 7b92c3e0 divergiu em 2500 centavos.')).toBeInTheDocument();
    expect(screen.getByText(/permissão somente leitura \(VIEWER\)/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /executar conciliação/i })).toBeDisabled();
  });
});
