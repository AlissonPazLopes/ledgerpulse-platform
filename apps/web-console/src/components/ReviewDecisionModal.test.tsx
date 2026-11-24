import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { riskReviewService } from '../services/riskReviewService';
import type { RiskReviewDecisionResponse, RiskReviewItem } from '../types/api';
import { ReviewDecisionModal } from './ReviewDecisionModal';

vi.mock('../services/riskReviewService', () => ({
  riskReviewService: {
    submitRiskReviewDecision: vi.fn(),
  },
}));

const REVIEW_ITEM: RiskReviewItem = {
  paymentId: 'e1d2c3b4-5a6b-4c7d-8e9f-001122334455',
  merchantId: '7b92c3e0-1f42-4876-a1e2-901283471234',
  payerId: 'payer-98765',
  amountCents: 185000,
  platformFeeCents: 4625,
  netMerchantCents: 180375,
  currency: 'BRL',
  paymentMethod: 'CARD',
  description: 'Compra de alto valor',
  status: 'MANUAL_REVIEW',
  riskScore: 68,
  riskDecision: 'REVIEW',
  reasonCodes: ['NIGHT_ANOMALY', 'RISK_ENGINE_UNAVAILABLE'],
  correlationId: '4f3c9f62-8b9a-4f3b-9d1a-0192837465ab',
  createdAt: '2026-11-22T02:15:00Z',
};

const APPROVED_RESPONSE: RiskReviewDecisionResponse = {
  decisionId: 'd1a2b3c4-1111-4222-8333-444455556666',
  paymentId: REVIEW_ITEM.paymentId,
  decision: 'APPROVE',
  paymentStatus: 'SETTLED',
  ledgerTransactionId: 'f9e8d7c6-5b4a-4321-8765-112233445566',
  justification: 'Titular confirmou operação por telefone.',
  reviewedByUserId: '11111111-1111-4111-8111-111111111112',
  reviewedByEmail: 'analyst@ledgerpulse.dev',
  reviewedAt: '2026-11-22T12:45:00Z',
};

describe('ReviewDecisionModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('validates minimum justification length (>= 5 chars) before submitting', async () => {
    const user = userEvent.setup();
    const onDecisionSubmitted = vi.fn();

    render(
      <ReviewDecisionModal
        item={REVIEW_ITEM}
        initialDecision="APPROVE"
        isOpen={true}
        onClose={vi.fn()}
        onDecisionSubmitted={onDecisionSubmitted}
      />,
    );

    expect(screen.getByText('R$ 1.850,00')).toBeInTheDocument();
    expect(screen.getByText('Fallback Resilience4j')).toBeInTheDocument();

    const textarea = screen.getByLabelText(/justificativa auditável/i);
    await user.type(textarea, 'Ok');
    await user.click(screen.getByRole('button', { name: /confirmar aprovação/i }));

    expect(
      screen.getByText(/a justificativa deve ter ao menos 5 caracteres/i),
    ).toBeInTheDocument();
    expect(riskReviewService.submitRiskReviewDecision).not.toHaveBeenCalled();
  });

  it('submits APPROVE and DECLINE decisions with valid justification', async () => {
    const user = userEvent.setup();
    const onDecisionSubmitted = vi.fn();
    vi.mocked(riskReviewService.submitRiskReviewDecision).mockResolvedValue(APPROVED_RESPONSE);

    render(
      <ReviewDecisionModal
        item={REVIEW_ITEM}
        initialDecision="APPROVE"
        isOpen={true}
        onClose={vi.fn()}
        onDecisionSubmitted={onDecisionSubmitted}
      />,
    );

    const textarea = screen.getByLabelText(/justificativa auditável/i);
    await user.type(textarea, 'Titular confirmou operação por telefone.');
    await user.click(screen.getByRole('button', { name: /confirmar aprovação/i }));

    await waitFor(() => {
      expect(riskReviewService.submitRiskReviewDecision).toHaveBeenCalledWith(
        REVIEW_ITEM.paymentId,
        {
          decision: 'APPROVE',
          justification: 'Titular confirmou operação por telefone.',
        },
      );
    });

    expect(onDecisionSubmitted).toHaveBeenCalledWith(APPROVED_RESPONSE, REVIEW_ITEM);

    // Switch to DECLINE and submit again
    await user.click(screen.getByRole('button', { name: /decline · recusar cobrança/i }));
    await user.click(screen.getByRole('button', { name: /confirmar recusa/i }));

    await waitFor(() => {
      expect(riskReviewService.submitRiskReviewDecision).toHaveBeenLastCalledWith(
        REVIEW_ITEM.paymentId,
        {
          decision: 'DECLINE',
          justification: 'Titular confirmou operação por telefone.',
        },
      );
    });
  });
});
