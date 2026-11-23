import { httpClient } from '../lib/httpClient';
import type {
  CreateWebhookEndpointRequest,
  DlqReplayResponse,
  PageResponse,
  SinkMode,
  SinkModeResponse,
  WebhookDelivery,
  WebhookDeliveryStatus,
  WebhookEndpoint,
  WebhookEndpointStatus,
} from '../types/api';

export interface ListWebhookEndpointsParams {
  page?: number;
  size?: number;
  merchantId?: string;
  status?: WebhookEndpointStatus | '';
}

export interface ListWebhookDeliveriesParams {
  page?: number;
  size?: number;
  status?: WebhookDeliveryStatus | '';
  merchantId?: string;
  eventType?: string;
  eventId?: string;
}

function cleanOptionalParam(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export const webhooksService = {
  listWebhookEndpoints(
    params: ListWebhookEndpointsParams = {},
  ): Promise<PageResponse<WebhookEndpoint>> {
    return httpClient.get<PageResponse<WebhookEndpoint>>('/api/v1/webhooks/endpoints', {
      service: 'webhook',
      query: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        merchantId: cleanOptionalParam(params.merchantId),
        status: cleanOptionalParam(params.status),
      },
    });
  },

  createWebhookEndpoint(payload: CreateWebhookEndpointRequest): Promise<WebhookEndpoint> {
    return httpClient.post<WebhookEndpoint>('/api/v1/webhooks/endpoints', payload, {
      service: 'webhook',
    });
  },

  listWebhookDeliveries(
    params: ListWebhookDeliveriesParams = {},
  ): Promise<PageResponse<WebhookDelivery>> {
    return httpClient.get<PageResponse<WebhookDelivery>>('/api/v1/webhooks/deliveries', {
      service: 'webhook',
      query: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        status: cleanOptionalParam(params.status),
        merchantId: cleanOptionalParam(params.merchantId),
        eventType: cleanOptionalParam(params.eventType),
        eventId: cleanOptionalParam(params.eventId),
      },
    });
  },

  replayDlqDelivery(deliveryId: string): Promise<DlqReplayResponse> {
    return httpClient.post<DlqReplayResponse>(
      `/api/v1/webhooks/dlq/${encodeURIComponent(deliveryId)}/replay`,
      undefined,
      {
        service: 'webhook',
      },
    );
  },

  getSinkMode(): Promise<SinkModeResponse> {
    return httpClient.get<SinkModeResponse>('/api/v1/sink/mode', {
      service: 'webhook',
    });
  },

  updateSinkMode(mode: SinkMode): Promise<SinkModeResponse> {
    return httpClient.put<SinkModeResponse>(
      '/api/v1/sink/mode',
      { mode },
      {
        service: 'webhook',
      },
    );
  },
};
