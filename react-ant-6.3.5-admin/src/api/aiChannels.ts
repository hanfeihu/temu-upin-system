import client from '@/api/client';
import type { AIChannelBusinessConfigPayload, AIChannelBusinessConfigVO, AIChannelPayload, AIChannelTestResponseVO, AIChannelVO, ApiResponse } from '@/types/api';

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

  listBusinessConfigs() {
    return client.get('/channels/business-configs') as Promise<ApiResponse<AIChannelBusinessConfigVO[]>>;
  },

  createBusinessConfig(payload: AIChannelBusinessConfigPayload) {
    return client.post('/channels/business-configs', payload) as Promise<ApiResponse<AIChannelBusinessConfigVO>>;
  },

  updateBusinessConfig(id: number, payload: AIChannelBusinessConfigPayload) {
    return client.put(`/channels/business-configs/${id}`, payload) as Promise<ApiResponse<AIChannelBusinessConfigVO>>;
  },

  removeBusinessConfig(id: number) {
    return client.delete(`/channels/business-configs/${id}`) as Promise<ApiResponse<null>>;
  },
};
