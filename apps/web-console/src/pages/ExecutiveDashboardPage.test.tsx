import { act, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuth } from '../context/AuthContext';
import { connectSseStream, type ConnectSseStreamOptions } from '../lib/sseClient';
import { dashboardService } from '../services/dashboardService';
import { webhooksService } from '../services/webhooksService';
import type { DashboardSummaryResponse } from '../types/api';
import { ExecutiveDashboardPage } from './ExecutiveDashboardPage';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../services/dashboardService', () => ({
  dashboardService: {
    getSummary: vi.fn(),
  },
}));

vi.mock('../services/webhooksService', () => ({
  webhooksService: {
    listWebhookDeliveries: vi.fn(),
  },
}));

vi.mock('../lib/sseClient', () => ({
  connectSseStream: vi.fn(),
}));

const INITIAL_SUMMARY: DashboardSummaryResponse = {
  tpvCents: 1500000,
  totalPayments: 42,
  settledCount: 36,
  manualReviewCount: 3,
  declinedCount: 2,
  refundedCount: 1,
  totalRefundedCents: 15000,
  approvalRateBps: 8571,
  averageRiskScore: 22,
  hourlySeries: [
    {
      hourLabel: '14:00',
      volumeCents: 320000,
      paymentCount: 8,
      averageRiskScore: 19,
    },
  ],
  generatedAt: '2026-11-24T14:30:00Z',
};

describe('ExecutiveDashboardPage', () => {
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

  it('renders KPIs, handles offline webhook-dispatcher gracefully, and updates live via SSE events', async () => {
    let capturedOptions: ConnectSseStreamOptions | null = null;
    const disconnectMock = vi.fn();

    vi.mocked(connectSseStream).mockImplementation((options) => {
      capturedOptions = options;
      return disconnectMock;
    });

    vi.mocked(dashboardService.getSummary)
      .mockResolvedValueOnce(INITIAL_SUMMARY)
      .mockResolvedValueOnce({
        ...INITIAL_SUMMARY,
        tpvCents: 1515000,
        settledCount: 37,
      });

    vi.mocked(webhooksService.listWebhookDeliveries).mockRejectedValue(
      new Error('Connection refused :8082'),
    );

    const { unmount } = render(
      <MemoryRouter>
        <ExecutiveDashboardPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('R$ 15.000,00')).toBeInTheDocument();
    expect(screen.getByText('85,71%')).toBeInTheDocument();
    expect(screen.getByText('3 pendente(s)')).toBeInTheDocument();
    expect(await screen.findByText('Offline (:8082)')).toBeInTheDocument();

    act(() => {
      capturedOptions?.onStatusChange?.('connected');
      capturedOptions?.onDomainEvent?.({
        eventId: 'c7a615f1-82b3-4e11-9c2a-5d2d1b63e701',
        eventType: 'payment.settled',
        schemaVersion: '1.0',
        occurredAt: '2026-11-24T14:31:05Z',
        correlationId: '4f3c9f62-8b9a-4f3b-9d1a-0192837465ab',
        merchantId: '7b92c3e0-1f42-4876-a1e2-901283471234',
        payload: {
          paymentId: 'e1d2c3b4-5a6b-4c7d-8e9f-001122334455',
          amountCents: 15000,
          status: 'SETTLED',
        },
      });
    });

    expect(screen.getByText('Conectado')).toBeInTheDocument();
    expect(screen.getByText('payment.settled')).toBeInTheDocument();
    expect(await screen.findByText('R$ 15.150,00')).toBeInTheDocument();

    await waitFor(() => {
      expect(dashboardService.getSummary).toHaveBeenCalledTimes(2);
    });

    unmount();
    expect(disconnectMock).toHaveBeenCalledTimes(1);
  });
});
