import { getStoredAccessToken } from './authStorage';
import {
  generateCorrelationId,
  getServiceBaseUrl,
  toApiErrorFromResponse,
} from './httpClient';
import type {
  SseControlEvent,
  SseDomainEvent,
  SseDomainEventType,
} from '../types/api';

export type SseConnectionStatus =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'offline';

export interface ParsedSseFrame {
  eventName: string;
  id?: string;
  data: string;
}

export interface ConnectSseStreamOptions {
  token?: string | null;
  onStatusChange?: (status: SseConnectionStatus) => void;
  onControlEvent?: (event: SseControlEvent) => void;
  onDomainEvent?: (event: SseDomainEvent) => void;
  onError?: (error: Error) => void;
  reconnectBaseDelayMs?: number;
  reconnectMaxDelayMs?: number;
}

const DOMAIN_EVENT_TYPES = new Set<SseDomainEventType>([
  'payment.settled',
  'payment.manual_review_required',
  'payment.declined',
  'payment.refunded',
  'risk.feedback.recorded',
  'reconciliation.completed',
]);

export function parseSseFrame(rawBlock: string): ParsedSseFrame | null {
  const lines = rawBlock.split(/\r?\n/);
  let eventName = 'message';
  let id: string | undefined;
  const dataLines: string[] = [];

  for (const line of lines) {
    if (!line || line.startsWith(':')) {
      continue;
    }

    const colonIndex = line.indexOf(':');
    const field = colonIndex === -1 ? line : line.slice(0, colonIndex);
    const rawValue = colonIndex === -1 ? '' : line.slice(colonIndex + 1);
    const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue;

    if (field === 'event') {
      eventName = value;
    } else if (field === 'id') {
      id = value;
    } else if (field === 'data') {
      dataLines.push(value);
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  return {
    eventName,
    id,
    data: dataLines.join('\n'),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function dispatchParsedFrame(
  frame: ParsedSseFrame,
  options: ConnectSseStreamOptions,
): void {
  let parsedJson: unknown;
  try {
    parsedJson = JSON.parse(frame.data) as unknown;
  } catch {
    return;
  }

  if (!isRecord(parsedJson)) {
    return;
  }

  const eventType =
    typeof parsedJson.eventType === 'string' ? parsedJson.eventType : frame.eventName;

  if (eventType === 'connected' || eventType === 'heartbeat') {
    options.onControlEvent?.({
      eventType,
      timestamp:
        typeof parsedJson.timestamp === 'string'
          ? parsedJson.timestamp
          : new Date().toISOString(),
      message: typeof parsedJson.message === 'string' ? parsedJson.message : eventType,
    });
    return;
  }

  if (DOMAIN_EVENT_TYPES.has(eventType as SseDomainEventType)) {
    options.onDomainEvent?.({
      eventId:
        typeof parsedJson.eventId === 'string'
          ? parsedJson.eventId
          : frame.id ?? generateCorrelationId(),
      eventType: eventType as SseDomainEventType,
      schemaVersion:
        typeof parsedJson.schemaVersion === 'string' ? parsedJson.schemaVersion : '1.0',
      occurredAt:
        typeof parsedJson.occurredAt === 'string'
          ? parsedJson.occurredAt
          : new Date().toISOString(),
      correlationId:
        typeof parsedJson.correlationId === 'string'
          ? parsedJson.correlationId
          : 'unavailable',
      merchantId:
        typeof parsedJson.merchantId === 'string' ? parsedJson.merchantId : null,
      payload: isRecord(parsedJson.payload) ? parsedJson.payload : {},
    });
  }
}

/**
 * Conecta ao endpoint SSE `/api/v1/stream/events` usando `fetch()` + `ReadableStream`
 * para permitir envio explícito do header `Authorization: Bearer <jwt>`.
 */
export function connectSseStream(options: ConnectSseStreamOptions): () => void {
  const abortController = new AbortController();
  const baseDelay = options.reconnectBaseDelayMs ?? 1500;
  const maxDelay = options.reconnectMaxDelayMs ?? 15000;
  let retryAttempt = 0;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  const scheduleReconnect = () => {
    if (abortController.signal.aborted) {
      return;
    }
    const delay = Math.min(maxDelay, baseDelay * 2 ** retryAttempt);
    retryAttempt += 1;
    options.onStatusChange?.('reconnecting');
    reconnectTimer = setTimeout(() => {
      if (!abortController.signal.aborted) {
        void openStream();
      }
    }, delay);
  };

  const openStream = async () => {
    if (abortController.signal.aborted) {
      return;
    }

    const token = options.token ?? getStoredAccessToken();
    if (!token) {
      options.onStatusChange?.('offline');
      return;
    }

    options.onStatusChange?.(retryAttempt > 0 ? 'reconnecting' : 'connecting');

    const path = '/api/v1/stream/events';
    const url = `${getServiceBaseUrl('core')}${path}`;
    const correlationId = generateCorrelationId();

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
          'X-Correlation-Id': correlationId,
        },
        signal: abortController.signal,
      });

      if (!response.ok) {
        const apiError = await toApiErrorFromResponse(response, path, correlationId);
        options.onError?.(apiError);
        if (response.status === 401 || response.status === 403) {
          options.onStatusChange?.('offline');
          return;
        }
        scheduleReconnect();
        return;
      }

      if (!response.body) {
        options.onStatusChange?.('offline');
        scheduleReconnect();
        return;
      }

      retryAttempt = 0;
      options.onStatusChange?.('connected');

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (!abortController.signal.aborted) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() ?? '';

        for (const rawBlock of blocks) {
          const parsedFrame = parseSseFrame(rawBlock);
          if (parsedFrame) {
            dispatchParsedFrame(parsedFrame, options);
          }
        }
      }

      if (!abortController.signal.aborted) {
        scheduleReconnect();
      }
    } catch (error) {
      if (abortController.signal.aborted) {
        return;
      }
      options.onError?.(
        error instanceof Error ? error : new Error('Falha na conexão SSE.'),
      );
      scheduleReconnect();
    }
  };

  void openStream();

  return () => {
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer);
    }
    abortController.abort();
    options.onStatusChange?.('offline');
  };
}
