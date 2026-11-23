import { httpClient } from '../lib/httpClient';
import type {
  CreatePaymentRequest,
  PageResponse,
  Payment,
  PaymentStatus,
} from '../types/api';

export interface CreatePaymentResult {
  payment: Payment;
  status: number;
  idempotentReplay: boolean;
  correlationId: string;
}

export interface ListPaymentsParams {
  page?: number;
  size?: number;
  status?: PaymentStatus | '';
  merchantId?: string;
  from?: string;
  to?: string;
}

function cleanOptionalParam(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export const paymentsService = {
  async createPayment(
    payload: CreatePaymentRequest,
    idempotencyKey: string,
  ): Promise<CreatePaymentResult> {
    const response = await httpClient.postWithMeta<Payment>('/api/v1/payments', payload, {
      service: 'core',
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
    });

    return {
      payment: response.data,
      status: response.status,
      idempotentReplay: response.headers.get('x-idempotent-replay')?.toLowerCase() === 'true',
      correlationId: response.correlationId,
    };
  },

  listPayments(params: ListPaymentsParams = {}): Promise<PageResponse<Payment>> {
    return httpClient.get<PageResponse<Payment>>('/api/v1/payments', {
      service: 'core',
      query: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        status: cleanOptionalParam(params.status),
        merchantId: cleanOptionalParam(params.merchantId),
        from: cleanOptionalParam(params.from),
        to: cleanOptionalParam(params.to),
      },
    });
  },

  getPaymentById(id: string): Promise<Payment> {
    return httpClient.get<Payment>(`/api/v1/payments/${encodeURIComponent(id)}`, {
      service: 'core',
    });
  },
};
