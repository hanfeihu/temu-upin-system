import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { getCurrentUser, login as loginApi } from '@/api/auth';
import type { CurrentUserVO } from '@/types/api';
import { clearAuthSession, getStoredUser, saveAuthSession } from '@/utils/auth';
import { getToken } from '@/utils/request';

interface AuthState {
  user: CurrentUserVO | null;
  loading: boolean;
  authenticated: boolean;
}

interface AuthContextValue extends AuthState {
  fetchUser: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [state, setState] = useState<AuthState>({
    user: getStoredUser(),
    loading: !!getToken(),
    authenticated: !!getToken(),
  });

  const fetchUser = useCallback(async () => {
    try {
      const res = await getCurrentUser();
      const currentUser = res.data;
      saveAuthSession(currentUser);
      setState({
        user: currentUser,
        loading: false,
        authenticated: true,
      });
    } catch {
      clearAuthSession();
      setState({
        user: null,
        loading: false,
        authenticated: false,
      });
    }
  }, []);

  const login = useCallback(
    async (username: string, password: string) => {
      const res = await loginApi({ username, password });
      const payload = res.data;
      saveAuthSession(payload, payload.accessToken);
      setState({
        user: {
          username: payload.username,
          displayName: payload.displayName,
          expiresAt: payload.expiresAt,
        },
        loading: false,
        authenticated: true,
      });
      await fetchUser();
    },
    [fetchUser],
  );

  const logout = useCallback(() => {
    clearAuthSession();
    setState({
      user: null,
      loading: false,
      authenticated: false,
    });
  }, []);

  useEffect(() => {
    if (getToken()) {
      void fetchUser();
    }
  }, [fetchUser]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      fetchUser,
      login,
      logout,
    }),
    [fetchUser, login, logout, state],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
