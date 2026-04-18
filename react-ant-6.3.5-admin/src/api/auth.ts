import client from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { CurrentUserResponse, LoginRequest, LoginResponse } from '@/types/auth';

export function login(payload: LoginRequest) {
  return client.post('/auth/login', payload) as Promise<ApiResponse<LoginResponse>>;
}

export function getCurrentUser() {
  return client.get('/auth/me') as Promise<ApiResponse<CurrentUserResponse>>;
}
