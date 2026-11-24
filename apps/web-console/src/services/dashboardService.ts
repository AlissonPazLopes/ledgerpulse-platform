import { httpClient } from '../lib/httpClient';
import type { DashboardSummaryResponse } from '../types/api';

export const dashboardService = {
  getSummary(): Promise<DashboardSummaryResponse> {
    return httpClient.get<DashboardSummaryResponse>('/api/v1/dashboard/summary', {
      service: 'core',
    });
  },
};
