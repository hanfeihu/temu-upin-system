import client from '@/api/client';
import type { AIChannelPayload, AIChannelTestResponseVO, AIChannelVO, ApiResponse } from '@/types/api';

export const aiChannelsApi = {
  listAll() {
    return client.get('/channels') as Promise<ApiResponse<AIChannelVO[]>>;
  },

  create(payload: AIChannelPayload) {
    return client.post('/channels', payload) as Promise<ApiResponse<AIChannelVO>>;
  },

  update(id: number, payload: AIChannelPayload) {
    return client.put(`/channels/${id}`, payload) as Promise<ApiResponse<AIChannelVO>>;
  },

  remove(id: number) {
    return client.delete(`/channels/${id}`) as Promise<ApiResponse<null>>;
  },

  test(id: number, payload: Record<string, unknown> = {}) {
    return client.post(`/channels/${id}/test`, payload) as Promise<ApiResponse<AIChannelTestResponseVO>>;
  },
};
