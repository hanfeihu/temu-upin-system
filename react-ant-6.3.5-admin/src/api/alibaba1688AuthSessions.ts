import client from '@/api/client';
import type {
  Alibaba1688AuthSessionOptionVO,
  Alibaba1688AuthSessionPayload,
  Alibaba1688AuthSessionVO,
  ApiResponse,
  SpringPage,
} from '@/types/api';

export const alibaba1688AuthSessionsApi = {
  list(params: {
    keyword?: string;
    enabled?: boolean;
    status?: string;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/alibaba1688-auth-sessions', { params }) as Promise<ApiResponse<SpringPage<Alibaba1688AuthSessionVO>>>;
  },

  listOptions(enabledOnly = true) {
    return client.get('/platform/alibaba1688-auth-sessions/options', {
      params: { enabledOnly },
    }) as Promise<ApiResponse<Alibaba1688AuthSessionOptionVO[]>>;
  },

  create(payload: Alibaba1688AuthSessionPayload) {
    return client.post('/platform/alibaba1688-auth-sessions', payload) as Promise<ApiResponse<Alibaba1688AuthSessionVO>>;
  },

  update(id: number, payload: Alibaba1688AuthSessionPayload) {
    return client.put(`/platform/alibaba1688-auth-sessions/${id}`, payload) as Promise<ApiResponse<Alibaba1688AuthSessionVO>>;
  },

  toggleEnabled(id: number, enabled: boolean) {
    return client.put(`/platform/alibaba1688-auth-sessions/${id}/enabled`, { enabled }) as Promise<ApiResponse<Alibaba1688AuthSessionVO>>;
  },
};
