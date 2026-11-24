import { getStoredAccessToken } from './authStorage';
import type { FieldValidationError, ProblemDetail } from '../types/api';

export type ApiServiceTarget = 'core' | 'webhook';

export interface HttpRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  query?: Record<string, string | number | boolean | undefined>;
  service?: ApiServiceTarget;
  skipAuth?: boolean;
  tokenOverride?: string;
  signal?: AbortSignal;
}

export interface HttpResponseWithMeta<T> {
  data: T;
  status: number;
  correlationId: string;
  headers: Headers;
}

const CORE_API_BASE_URL = (import.meta.env.VITE_CORE_API_URL ?? '').replace(/\/+$/, '');
const WEBHOOK_API_BASE_URL = (
  import.meta.env.VITE_WEBHOOK_API_URL ?? 'http://localhost:8082'
).replace(/\/+$/, '');

let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

export class ApiError extends Error {
  public readonly status: number;
  public readonly problem: ProblemDetail;
  public readonly correlationId: string;

  constructor(problem: ProblemDetail, fallbackMessage?: string) {
    super(problem.detail || problem.title || fallbackMessage || `Erro HTTP ${problem.status}`);
    this.name = 'ApiError';
    this.status = problem.status;
    this.problem = problem;
    this.correlationId = problem.correlationId;
  }
}

export function getServiceBaseUrl(service: ApiServiceTarget = 'core'): string {
  return service === 'webhook' ? WEBHOOK_API_BASE_URL : CORE_API_BASE_URL;
}

export function generateCorrelationId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const rand = (Math.random() * 16) | 0;
    const value = char === 'x' ? rand : (rand & 0x3) | 0x8;
    return value.toString(16);
  });
}

function buildRequestUrl(
  path: string,
  service: ApiServiceTarget,
  query?: Record<string, string | number | boolean | undefined>,
): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const baseUrl = getServiceBaseUrl(service);
  const fullPath = `${baseUrl}${normalizedPath}`;

  if (!query) {
    return fullPath;
  }

  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) {
      searchParams.set(key, String(value));
    }
  }

  const queryString = searchParams.toString();
  return queryString ? `${fullPath}?${queryString}` : fullPath;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseFieldErrors(rawErrors: unknown): FieldValidationError[] | undefined {
  if (!Array.isArray(rawErrors)) {
    return undefined;
  }

  const parsed = rawErrors
    .filter(isRecord)
    .map((entry) => ({
      field: typeof entry.field === 'string' ? entry.field : 'unknown',
      message: typeof entry.message === 'string' ? entry.message : 'Campo inválido.',
    }));

  return parsed.length > 0 ? parsed : undefined;
}

function normalizeProblemDetail(
  rawBody: unknown,
  status: number,
  fallbackTitle: string,
  fallbackDetail: string,
  instance: string,
  correlationId: string,
): ProblemDetail {
  if (isRecord(rawBody)) {
    const parsedErrors = parseFieldErrors(rawBody.errors);
    return {
      type:
        typeof rawBody.type === 'string'
          ? rawBody.type
          : 'https://ledgerpulse.dev/errors/internal-error',
      title: typeof rawBody.title === 'string' ? rawBody.title : fallbackTitle,
      status: typeof rawBody.status === 'number' ? rawBody.status : status,
      detail: typeof rawBody.detail === 'string' ? rawBody.detail : fallbackDetail,
      instance: typeof rawBody.instance === 'string' ? rawBody.instance : instance,
      correlationId:
        typeof rawBody.correlationId === 'string' ? rawBody.correlationId : correlationId,
      timestamp:
        typeof rawBody.timestamp === 'string' ? rawBody.timestamp : new Date().toISOString(),
      ...(parsedErrors ? { errors: parsedErrors } : {}),
    };
  }

  return {
    type: 'https://ledgerpulse.dev/errors/http-error',
    title: fallbackTitle,
    status,
    detail: fallbackDetail,
    instance,
    correlationId,
    timestamp: new Date().toISOString(),
  };
}

async function parseResponseBody(response: Response): Promise<unknown> {
  const rawText = await response.text();
  if (!rawText.trim()) {
    return null;
  }

  try {
    return JSON.parse(rawText) as unknown;
  } catch {
    return rawText;
  }
}

export async function toApiErrorFromResponse(
  response: Response,
  path: string,
  correlationId: string,
  skipAuth = false,
): Promise<ApiError> {
  const responseCorrelationId = response.headers.get('X-Correlation-Id') ?? correlationId;
  const parsedErrorBody = await parseResponseBody(response);
  const fallbackDetail =
    typeof parsedErrorBody === 'string' && parsedErrorBody.trim().length > 0
      ? parsedErrorBody
      : `Falha na requisição HTTP ${response.status}.`;

  const problem = normalizeProblemDetail(
    parsedErrorBody,
    response.status,
    response.statusText || 'Request Failed',
    fallbackDetail,
    path,
    responseCorrelationId,
  );

  if (response.status === 401 && !skipAuth) {
    unauthorizedHandler?.();
  }

  return new ApiError(problem);
}

export async function httpRequestWithMeta<T>(
  path: string,
  options: HttpRequestOptions = {},
): Promise<HttpResponseWithMeta<T>> {
  const {
    method = 'GET',
    body,
    headers = {},
    query,
    service = 'core',
    skipAuth = false,
    tokenOverride,
    signal,
  } = options;

  const correlationId = headers['X-Correlation-Id'] ?? generateCorrelationId();
  const url = buildRequestUrl(path, service, query);

  const requestHeaders: Record<string, string> = {
    Accept: 'application/json, application/problem+json',
    'X-Correlation-Id': correlationId,
    ...headers,
  };

  if (body !== undefined && !requestHeaders['Content-Type']) {
    requestHeaders['Content-Type'] = 'application/json';
  }

  if (!skipAuth && !requestHeaders.Authorization) {
    const token = tokenOverride ?? getStoredAccessToken();
    if (token) {
      requestHeaders.Authorization = `Bearer ${token}`;
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers: requestHeaders,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch {
    const targetServiceName =
      service === 'webhook' ? 'webhook-dispatcher' : 'ledger-core-api';
    throw new ApiError({
      type: 'https://ledgerpulse.dev/errors/network-error',
      title: 'Service Unavailable',
      status: 0,
      detail: `Não foi possível conectar ao servidor da API. Verifique se o ${targetServiceName} está ativo.`,
      instance: path,
      correlationId,
      timestamp: new Date().toISOString(),
    });
  }

  const responseCorrelationId = response.headers.get('X-Correlation-Id') ?? correlationId;

  if (!response.ok) {
    throw await toApiErrorFromResponse(response, path, correlationId, skipAuth);
  }

  if (response.status === 204) {
    return {
      data: undefined as T,
      status: response.status,
      correlationId: responseCorrelationId,
      headers: response.headers,
    };
  }

  const parsedBody = await parseResponseBody(response);
  return {
    data: parsedBody as T,
    status: response.status,
    correlationId: responseCorrelationId,
    headers: response.headers,
  };
}

export async function httpRequest<T>(
  path: string,
  options: HttpRequestOptions = {},
): Promise<T> {
  const response = await httpRequestWithMeta<T>(path, options);
  return response.data;
}

export const httpClient = {
  get<T>(path: string, options: Omit<HttpRequestOptions, 'method' | 'body'> = {}): Promise<T> {
    return httpRequest<T>(path, { ...options, method: 'GET' });
  },
  post<T>(
    path: string,
    body?: unknown,
    options: Omit<HttpRequestOptions, 'method' | 'body'> = {},
  ): Promise<T> {
    return httpRequest<T>(path, { ...options, method: 'POST', body });
  },
  postWithMeta<T>(
    path: string,
    body?: unknown,
    options: Omit<HttpRequestOptions, 'method' | 'body'> = {},
  ): Promise<HttpResponseWithMeta<T>> {
    return httpRequestWithMeta<T>(path, { ...options, method: 'POST', body });
  },
  put<T>(
    path: string,
    body?: unknown,
    options: Omit<HttpRequestOptions, 'method' | 'body'> = {},
  ): Promise<T> {
    return httpRequest<T>(path, { ...options, method: 'PUT', body });
  },
  patch<T>(
    path: string,
    body?: unknown,
    options: Omit<HttpRequestOptions, 'method' | 'body'> = {},
  ): Promise<T> {
    return httpRequest<T>(path, { ...options, method: 'PATCH', body });
  },
  delete<T>(path: string, options: Omit<HttpRequestOptions, 'method' | 'body'> = {}): Promise<T> {
    return httpRequest<T>(path, { ...options, method: 'DELETE' });
  },
};
