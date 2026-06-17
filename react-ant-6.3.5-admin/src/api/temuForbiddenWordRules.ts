import client from '@/api/client';
import type { ApiResponse, SpringPage, TemuForbiddenWordRulePayload, TemuForbiddenWordRuleVO } from '@/types/api';

export const temuForbiddenWordRulesApi = {
  list(params?: { q?: string; enabled?: boolean; page?: number; size?: number }) {
    return client.get('/platform/temu-forbidden-word-rules', { params }) as Promise<
      ApiResponse<SpringPage<TemuForbiddenWordRuleVO>>
    >;
  },

  create(payload: TemuForbiddenWordRulePayload) {
    return client.post('/platform/temu-forbidden-word-rules', payload) as Promise<ApiResponse<TemuForbiddenWordRuleVO>>;
  },

  update(id: number, payload: TemuForbiddenWordRulePayload) {
    return client.put(`/platform/temu-forbidden-word-rules/${id}`, payload) as Promise<ApiResponse<TemuForbiddenWordRuleVO>>;
  },

  delete(id: number) {
    return client.delete(`/platform/temu-forbidden-word-rules/${id}`) as Promise<ApiResponse<null>>;
  },
};
