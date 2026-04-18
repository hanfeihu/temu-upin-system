import client from '@/api/client';
import type { ApiResponse, BizLogPayload, BizLogVO, SpringPage } from '@/types/api';

export const bizLogsApi = {
  list(params: Record<string, unknown>) {
    return client.get('/platform/biz-logs', {
      params,
    }) as Promise<ApiResponse<SpringPage<BizLogVO>>>;
  },

  create(payload: BizLogPayload) {
    return client.post('/platform/biz-logs', payload) as Promise<ApiResponse<BizLogVO>>;
  },

  update(id: number, payload: BizLogPayload) {
    return client.put(`/platform/biz-logs/${id}`, payload) as Promise<ApiResponse<BizLogVO>>;
  },

  delete(id: number) {
    return client.delete(`/platform/biz-logs/${id}`) as Promise<ApiResponse<null>>;
  },
};
