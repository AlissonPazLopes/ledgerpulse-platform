import { httpClient } from '../lib/httpClient';
import type { AuthUser, HealthResponse, LoginRequest, LoginResponse } from '../types/api';

export const authService = {
  login(payload: LoginRequest): Promise<LoginResponse> {
    return httpClient.post<LoginResponse>('/api/v1/auth/login', payload, {
      skipAuth: true,
    });
  },

  getMe(tokenOverride?: string): Promise<AuthUser> {
    return httpClient.get<AuthUser>('/api/v1/auth/me', {
      tokenOverride,
    });
  },

  getHealth(): Promise<HealthResponse> {
    return httpClient.get<HealthResponse>('/api/v1/health', {
      skipAuth: true,
    });
  },
};
