import client from '@/api/client';
import type { ApiResponse, LogisticsProviderConfigPayload, LogisticsProviderConfigVO } from '@/types/api';

export const logisticsProviderConfigsApi = {
  list(params?: { enabled?: boolean }) {
    return client.get('/platform/logistics-provider-configs', { params }) as Promise<ApiResponse<LogisticsProviderConfigVO[]>>;
  },

  create(payload: LogisticsProviderConfigPayload) {
    return client.post('/platform/logistics-provider-configs', payload) as Promise<ApiResponse<LogisticsProviderConfigVO>>;
  },

  update(id: number, payload: LogisticsProviderConfigPayload) {
    return client.put(`/platform/logistics-provider-configs/${id}`, payload) as Promise<ApiResponse<LogisticsProviderConfigVO>>;
  },

  importLegacyHaoyuan() {
    return client.post('/platform/logistics-provider-configs/import-legacy/haoyuan') as Promise<ApiResponse<LogisticsProviderConfigVO>>;
  },
};
