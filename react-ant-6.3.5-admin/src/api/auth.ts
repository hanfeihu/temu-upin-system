import client from '@/api/client';
import type { CurrentUserResponse, LoginRequest, LoginResponse } from '@/types/auth';

export async function login(payload: LoginRequest) {
  return (await client.post<LoginResponse>('/auth/login', payload)) as unknown as LoginResponse;
}

export async function getCurrentUser() {
  return (await client.get<CurrentUserResponse>('/auth/me')) as unknown as CurrentUserResponse;
}
