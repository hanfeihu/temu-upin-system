import axios from 'axios';
import type { ApiResponse } from '@/types/auth';
import { clearAuthSession, getAccessToken } from '@/utils/auth';

const client = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
});

client.interceptors.request.use((config) => {
  const token = getAccessToken();

  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

client.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown>;

    if (payload && typeof payload.success === 'boolean') {
      if (!payload.success) {
        return Promise.reject(new Error(payload.message || '请求失败'));
      }

      return payload.data;
    }

    return response.data;
  },
  (error) => {
    if (error.response?.status === 401) {
      clearAuthSession();
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.replace('/login');
      }
    }

    const message = error.response?.data?.message || error.message || '请求失败';
    return Promise.reject(new Error(message));
  },
);

export default client;
