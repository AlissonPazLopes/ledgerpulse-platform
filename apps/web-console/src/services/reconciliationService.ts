import { getStoredAccessToken } from '../lib/authStorage';
import {
  ApiError,
  generateCorrelationId,
  getServiceBaseUrl,
  httpClient,
  toApiErrorFromResponse,
} from '../lib/httpClient';
import type { PageResponse, ReconciliationReportResponse } from '../types/api';

const EXPORT_FILENAME = 'ledgerpulse-reconciliation.csv';

export const reconciliationService = {
  runReconciliation(): Promise<ReconciliationReportResponse> {
    return httpClient.post<ReconciliationReportResponse>(
      '/api/v1/reconciliation/run',
      undefined,
      { service: 'core' },
    );
  },

  listReports(
    page = 0,
    size = 20,
  ): Promise<PageResponse<ReconciliationReportResponse>> {
    return httpClient.get<PageResponse<ReconciliationReportResponse>>(
      '/api/v1/reconciliation/reports',
      {
        service: 'core',
        query: { page, size },
      },
    );
  },

  async downloadCsvReport(): Promise<void> {
    const path = '/api/v1/reconciliation/export.csv';
    const url = `${getServiceBaseUrl('core')}${path}`;
    const correlationId = generateCorrelationId();
    const token = getStoredAccessToken();

    const headers: Record<string, string> = {
      Accept: 'text/csv, application/problem+json',
      'X-Correlation-Id': correlationId,
    };

    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    let response: Response;
    try {
      response = await fetch(url, {
        method: 'GET',
        headers,
      });
    } catch {
      throw new ApiError({
        type: 'https://ledgerpulse.dev/errors/network-error',
        title: 'Service Unavailable',
        status: 0,
        detail:
          'Não foi possível conectar ao ledger-core-api para exportar o relatório CSV.',
        instance: path,
        correlationId,
        timestamp: new Date().toISOString(),
      });
    }

    if (!response.ok) {
      throw await toApiErrorFromResponse(response, path, correlationId);
    }

    const csvBlob = await response.blob();
    const objectUrl = URL.createObjectURL(csvBlob);

    try {
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = EXPORT_FILENAME;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
    } finally {
      URL.revokeObjectURL(objectUrl);
    }
  },
};
