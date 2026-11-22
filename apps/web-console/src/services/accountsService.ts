import { httpClient } from '../lib/httpClient';
import type {
  Account,
  AccountType,
  CreateAccountRequest,
  PageResponse,
} from '../types/api';

export interface ListAccountsParams {
  page?: number;
  size?: number;
  accountType?: AccountType | '';
  merchantId?: string;
}

function cleanOptionalParam(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export const accountsService = {
  listAccounts(params: ListAccountsParams = {}): Promise<PageResponse<Account>> {
    return httpClient.get<PageResponse<Account>>('/api/v1/accounts', {
      query: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        accountType: cleanOptionalParam(params.accountType),
        merchantId: cleanOptionalParam(params.merchantId),
      },
    });
  },

  getAccountById(id: string): Promise<Account> {
    return httpClient.get<Account>(`/api/v1/accounts/${encodeURIComponent(id)}`);
  },

  createAccount(payload: CreateAccountRequest): Promise<Account> {
    return httpClient.post<Account>('/api/v1/accounts', payload);
  },
};
