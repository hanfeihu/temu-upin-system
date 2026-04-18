import client from '@/api/client';
import type { ApiResponse, TemuAppVO } from '@/types/api';

export const temuAppsApi = {
  list(params?: { enabled?: boolean }) {
    return client.get('/platform/temu-apps', {
      params,
    }) as Promise<ApiResponse<TemuAppVO[]>>;
  },
};
