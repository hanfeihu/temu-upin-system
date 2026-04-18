import type { CurrentUserVO } from '@/types/api';
import type { LoginResponse } from '@/types/auth';
import { clearToken, getToken, setToken } from '@/utils/request';

const USER_KEY = 'temu-upin-auth-user';

export function hasAuthSession() {
  return !!getToken();
}

export function getStoredUser() {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as CurrentUserVO;
  } catch {
    return null;
  }
}

export function saveAuthSession(payload: LoginResponse | CurrentUserVO, token?: string) {
  if (token) {
    setToken(token);
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
  clearToken();
  localStorage.removeItem(USER_KEY);
}
