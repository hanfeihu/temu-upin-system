import client from '@/api/client';
import type { ApiResponse, TemuShopFreightTemplateOption, TemuShopPayload, TemuShopVO } from '@/types/api';

export const temuShopsApi = {
  list(params?: { enabled?: boolean }) {
    return client.get('/platform/temu-shops', {
      params,
    }) as Promise<ApiResponse<TemuShopVO[]>>;
  },

  create(payload: TemuShopPayload) {
    return client.post('/platform/temu-shops', payload) as Promise<ApiResponse<TemuShopVO>>;
  },

  update(id: number, payload: TemuShopPayload) {
    return client.put(`/platform/temu-shops/${id}`, payload) as Promise<ApiResponse<TemuShopVO>>;
  },

  listFreightTemplates(id: number) {
    return client.get(`/platform/temu-shops/${id}/freight-templates`) as Promise<ApiResponse<TemuShopFreightTemplateOption[]>>;
  },

  delete(id: number) {
    return client.delete(`/platform/temu-shops/${id}`) as Promise<ApiResponse<null>>;
  },
};
