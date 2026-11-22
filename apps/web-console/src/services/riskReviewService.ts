import { httpClient } from '../lib/httpClient';
import type {
  PageResponse,
  RiskReviewDecisionRequest,
  RiskReviewDecisionResponse,
  RiskReviewItem,
} from '../types/api';

export interface ListRiskReviewsParams {
  page?: number;
  size?: number;
  merchantId?: string;
  minRiskScore?: number;
  from?: string;
  to?: string;
}

function cleanOptionalParam(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export const riskReviewService = {
  listRiskReviews(
    params: ListRiskReviewsParams = {},
  ): Promise<PageResponse<RiskReviewItem>> {
    return httpClient.get<PageResponse<RiskReviewItem>>('/api/v1/risk/reviews', {
      query: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        merchantId: cleanOptionalParam(params.merchantId),
        minRiskScore: params.minRiskScore,
        from: cleanOptionalParam(params.from),
        to: cleanOptionalParam(params.to),
      },
    });
  },

  submitRiskReviewDecision(
    paymentId: string,
    payload: RiskReviewDecisionRequest,
  ): Promise<RiskReviewDecisionResponse> {
    return httpClient.post<RiskReviewDecisionResponse>(
      `/api/v1/risk/reviews/${encodeURIComponent(paymentId)}/decision`,
      payload,
    );
  },
};
