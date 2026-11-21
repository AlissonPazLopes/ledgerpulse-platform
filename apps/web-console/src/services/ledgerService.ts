import { httpClient } from '../lib/httpClient';
import type {
  LedgerReferenceType,
  LedgerTransactionDetail,
  LedgerTransactionSummary,
  PageResponse,
} from '../types/api';

export interface ListLedgerTransactionsParams {
  page?: number;
  size?: number;
  accountId?: string;
  referenceType?: LedgerReferenceType | '';
  referenceId?: string;
  from?: string;
  to?: string;
}

function cleanOptionalParam(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export const ledgerService = {
  listLedgerTransactions(
    params: ListLedgerTransactionsParams = {},
  ): Promise<PageResponse<LedgerTransactionSummary>> {
    return httpClient.get<PageResponse<LedgerTransactionSummary>>(
      '/api/v1/ledger/transactions',
      {
        query: {
          page: params.page ?? 0,
          size: params.size ?? 20,
          accountId: cleanOptionalParam(params.accountId),
          referenceType: cleanOptionalParam(params.referenceType),
          referenceId: cleanOptionalParam(params.referenceId),
          from: cleanOptionalParam(params.from),
          to: cleanOptionalParam(params.to),
        },
      },
    );
  },

  getLedgerTransactionById(id: string): Promise<LedgerTransactionDetail> {
    return httpClient.get<LedgerTransactionDetail>(
      `/api/v1/ledger/transactions/${encodeURIComponent(id)}`,
    );
  },
};
