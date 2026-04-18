import client from '@/api/client';
import type { ApiResponse, TemuAppPayload, TemuAppVO } from '@/types/api';

export const temuAppsApi = {
  list(params?: { enabled?: boolean }) {
    return client.get('/platform/temu-apps', {
      params,
    }) as Promise<ApiResponse<TemuAppVO[]>>;
  },

  create(payload: TemuAppPayload) {
    return client.post('/platform/temu-apps', payload) as Promise<ApiResponse<TemuAppVO>>;
  },

  update(id: number, payload: TemuAppPayload) {
    return client.put(`/platform/temu-apps/${id}`, payload) as Promise<ApiResponse<TemuAppVO>>;
  },

  delete(id: number) {
    return client.delete(`/platform/temu-apps/${id}`) as Promise<ApiResponse<null>>;
  },
};
