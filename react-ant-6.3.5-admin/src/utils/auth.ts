import type { CurrentUserResponse, LoginResponse } from '@/types/auth';

const TOKEN_KEY = 'temu-upin-auth-token';
const USER_KEY = 'temu-upin-auth-user';

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function hasAuthSession() {
  return !!getAccessToken();
}

export function getStoredUser() {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as CurrentUserResponse;
  } catch {
    return null;
  }
}

export function saveAuthSession(payload: LoginResponse | CurrentUserResponse, token?: string) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  }

  localStorage.setItem(
    USER_KEY,
    JSON.stringify({
      username: payload.username || '',
      displayName: payload.displayName || payload.username || '',
      expiresAt: payload.expiresAt ?? null,
    }),
  );
}

export function clearAuthSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
