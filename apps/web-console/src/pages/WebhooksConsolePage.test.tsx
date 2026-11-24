import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuth } from '../context/AuthContext';
import { webhooksService } from '../services/webhooksService';
import type { WebhookDelivery, WebhookEndpoint } from '../types/api';
import { WebhooksConsolePage } from './WebhooksConsolePage';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../services/webhooksService', () => ({
  webhooksService: {
    listWebhookEndpoints: vi.fn(),
    createWebhookEndpoint: vi.fn(),
    listWebhookDeliveries: vi.fn(),
    replayDlqDelivery: vi.fn(),
    getSinkMode: vi.fn(),
    updateSinkMode: vi.fn(),
  },
}));

const SAMPLE_ENDPOINT: WebhookEndpoint = {
  id: '33333333-3333-4333-8333-333333333333',
  merchantId: '7b92c3e0-1f42-4876-a1e2-901283471234',
  targetUrl: 'http://localhost:8082/api/v1/sink/test',
  secretPreview: 'whsec_****2026',
  description: 'Merchant Webhook Sink Local',
  status: 'ACTIVE',
  createdAt: '2026-11-23T12:00:00Z',
  updatedAt: '2026-11-23T12:00:00Z',
};

const DLQ_DELIVERY: WebhookDelivery = {
  id: '44444444-4444-4444-8444-444444444444',
  eventId: 'c7a615f1-82b3-4e11-9c2a-5d2d1b63e701',
  eventType: 'payment.settled',
  merchantId: '7b92c3e0-1f42-4876-a1e2-901283471234',
  endpointId: SAMPLE_ENDPOINT.id,
  targetUrl: SAMPLE_ENDPOINT.targetUrl,
  correlationId: '4f3c9f62-8b9a-4f3b-9d1a-0192837465ab',
  status: 'DLQ',
  attemptCount: 4,
  maxAttempts: 4,
  lastHttpStatus: 500,
  lastLatencyMs: 18,
  lastError: 'HTTP 500 Internal Server Error',
  signatureHeader: 't=1763900400,v1=3b8f6e1c2a9d4e7f',
  createdAt: '2026-11-23T12:00:00Z',
  updatedAt: '2026-11-23T12:02:35Z',
  deliveredAt: null,
  attempts: [
    {
      attemptNumber: 1,
      httpStatus: 500,
      latencyMs: 14,
      signatureHeader: 't=1763900245,v1=1a2b3c4d5e6f',
      errorMessage: 'HTTP 500 Internal Server Error',
      attemptedAt: '2026-11-23T12:00:00Z',
    },
  ],
};

describe('WebhooksConsolePage', () => {
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

    vi.mocked(webhooksService.listWebhookEndpoints).mockResolvedValue({
      items: [SAMPLE_ENDPOINT],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    vi.mocked(webhooksService.getSinkMode).mockResolvedValue({
      mode: 'FAIL_500',
      timeoutDelayMs: 2500,
      updatedAt: '2026-11-23T12:00:00Z',
    });

    vi.mocked(webhooksService.listWebhookDeliveries).mockResolvedValue({
      items: [DLQ_DELIVERY],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it('lists DLQ deliveries, switches Sink Mode, and triggers 1-click DLQ Replay', async () => {
    const user = userEvent.setup();
    vi.mocked(webhooksService.updateSinkMode).mockResolvedValue({
      mode: 'OK_200',
      timeoutDelayMs: 2500,
      updatedAt: '2026-11-23T12:04:00Z',
    });
    vi.mocked(webhooksService.replayDlqDelivery).mockResolvedValue({
      deliveryId: DLQ_DELIVERY.id,
      eventId: DLQ_DELIVERY.eventId,
      status: 'DELIVERED',
      replayedAt: '2026-11-23T12:05:00Z',
      httpStatus: 200,
      latencyMs: 11,
      errorMessage: null,
    });

    render(<WebhooksConsolePage />);

    expect(await screen.findByText('whsec_****2026')).toBeInTheDocument();
    expect(await screen.findByText('payment.settled')).toBeInTheDocument();

    // Expand attempts history
    await user.click(screen.getByRole('button', { name: /tentativas \(1\)/i }));
    expect(
      screen.getByText(/t=1763900245,v1=1a2b3c4d5e6f/i),
    ).toBeInTheDocument();

    // Switch Sink Mode to OK_200
    await user.click(screen.getByRole('button', { name: /ok_200/i }));
    await waitFor(() => {
      expect(webhooksService.updateSinkMode).toHaveBeenCalledWith('OK_200');
    });

    // Trigger DLQ Replay
    await user.click(screen.getByRole('button', { name: /replay dlq/i }));
    await waitFor(() => {
      expect(webhooksService.replayDlqDelivery).toHaveBeenCalledWith(DLQ_DELIVERY.id);
    });

    expect(await screen.findByText(/replay dlq executado para/i)).toBeInTheDocument();
    expect(webhooksService.listWebhookDeliveries).toHaveBeenCalledTimes(2);
  });
});
