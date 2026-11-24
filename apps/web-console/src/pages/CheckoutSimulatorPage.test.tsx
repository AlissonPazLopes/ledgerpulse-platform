import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { accountsService } from '../services/accountsService';
import { paymentsService } from '../services/paymentsService';
import type { Account, Payment } from '../types/api';
import { CheckoutSimulatorPage } from './CheckoutSimulatorPage';

vi.mock('../services/accountsService', () => ({
  accountsService: {
    listAccounts: vi.fn(),
  },
}));

vi.mock('../services/paymentsService', () => ({
  paymentsService: {
    createPayment: vi.fn(),
  },
}));

const RECIPIENT_ACCOUNT: Account = {
  id: '22222222-2222-4222-8222-222222222222',
  merchantId: '7b92c3e0-1f42-4876-a1e2-901283471234',
  accountType: 'RECIPIENT',
  holderName: 'Partner Seller Alpha',
  currency: 'BRL',
  availableBalanceCents: 0,
  pendingBalanceCents: 0,
  status: 'ACTIVE',
  version: 0,
  createdAt: '2026-11-20T22:30:00Z',
  updatedAt: '2026-11-20T22:30:00Z',
};

const SETTLED_PAYMENT: Payment = {
  id: 'e1d2c3b4-5a6b-4c7d-8e9f-001122334455',
  merchantId: '7b92c3e0-1f42-4876-a1e2-901283471234',
  payerId: 'payer-98765',
  amountCents: 15000,
  platformFeeCents: 375,
  netMerchantCents: 11625,
  currency: 'BRL',
  paymentMethod: 'CARD',
  description: 'Pedido #4021',
  status: 'SETTLED',
  riskScore: 14,
  riskDecision: 'APPROVE',
  reasonCodes: [],
  ledgerTransactionId: 'f9e8d7c6-5b4a-4321-8765-112233445566',
  idempotencyKey: 'fixed-idempotency-key-001',
  correlationId: '4f3c9f62-8b9a-4f3b-9d1a-0192837465ab',
  splits: [
    {
      recipientAccountId: RECIPIENT_ACCOUNT.id,
      splitType: 'PERCENTAGE',
      percentageBps: 2000,
      amountCents: 3000,
    },
  ],
  createdAt: '2026-11-20T22:31:05Z',
  updatedAt: '2026-11-20T22:31:05Z',
};

describe('CheckoutSimulatorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(accountsService.listAccounts).mockResolvedValue({
      items: [RECIPIENT_ACCOUNT],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it('calculates split preview in integer cents, submits with Idempotency-Key, and renders X-Idempotent-Replay badge', async () => {
    const user = userEvent.setup();
    vi.mocked(paymentsService.createPayment).mockResolvedValue({
      payment: SETTLED_PAYMENT,
      status: 201,
      idempotentReplay: true,
      correlationId: SETTLED_PAYMENT.correlationId,
    });

    render(
      <MemoryRouter>
        <CheckoutSimulatorPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(accountsService.listAccounts).toHaveBeenCalledWith({
        accountType: 'RECIPIENT',
        page: 0,
        size: 100,
      });
    });

    await user.click(screen.getByRole('button', { name: /adicionar split/i }));

    // Real-time preview for R$ 150,00 (15000 cents): fee 375, split 2000bps = 3000, net = 11625
    expect(screen.getByText('R$ 3,75')).toBeInTheDocument();
    expect(screen.getAllByText('R$ 30,00').length).toBeGreaterThan(0);
    expect(screen.getByText('R$ 116,25')).toBeInTheDocument();

    const keyInput = screen.getByLabelText('Idempotency-Key');
    await user.clear(keyInput);
    await user.type(keyInput, 'fixed-idempotency-key-001');

    await user.click(screen.getByRole('button', { name: /disparar cobrança/i }));

    await waitFor(() => {
      expect(paymentsService.createPayment).toHaveBeenCalledWith(
        expect.objectContaining({
          merchantId: '7b92c3e0-1f42-4876-a1e2-901283471234',
          payerId: 'payer-98765',
          amountCents: 15000,
          currency: 'BRL',
          paymentMethod: 'CARD',
          splits: [
            {
              recipientAccountId: RECIPIENT_ACCOUNT.id,
              splitType: 'PERCENTAGE',
              percentageBps: 2000,
            },
          ],
        }),
        'fixed-idempotency-key-001',
      );
    });

    const replayBadge = await screen.findByRole('status');
    expect(replayBadge).toHaveTextContent('X-Idempotent-Replay: true');
    expect(screen.getByText(/1\. Idempotência \(Redis\)/i)).toBeInTheDocument();
    expect(screen.getByText(/3\. Double-Entry Ledger/i)).toBeInTheDocument();
  });
});
