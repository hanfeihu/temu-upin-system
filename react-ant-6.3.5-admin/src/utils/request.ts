import axios from 'axios';
import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import type { ApiResponse } from '@/types/api';

const TOKEN_KEY = 'temu-upin-auth-token';
const USER_KEY = 'temu-upin-auth-user';
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function clearAuthStorage() {
  clearToken();
  localStorage.removeItem(USER_KEY);
}

const instance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
});

instance.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuthStorage();
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.replace('/login');
      }
    }

    const message = error.response?.data?.message || error.message || '请求失败';
    return Promise.reject(new Error(message));
  },
);

async function unwrapResponse<T>(promise: Promise<AxiosResponse<ApiResponse<T>>>) {
  const response = await promise;
  const res = response.data;
  if (res && typeof res.success === 'boolean' && res.success === false) {
    throw new Error(res.message || '请求失败');
  }
  return res;
}

const request = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return unwrapResponse<T>(instance.get(url, config));
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return unwrapResponse<T>(instance.post(url, data, config));
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return unwrapResponse<T>(instance.put(url, data, config));
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return unwrapResponse<T>(instance.delete(url, config));
  },
};

export default request;
