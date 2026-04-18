import client from '@/api/client';
import type { ApiResponse, TemuAttrRulePayload, TemuAttrRuleVO } from '@/types/api';

export const temuAttrRulesApi = {
  list(params?: { enabled?: boolean; leafCatId?: string }) {
    return client.get('/platform/temu-attr-rules', { params }) as Promise<ApiResponse<TemuAttrRuleVO[]>>;
  },

  create(payload: TemuAttrRulePayload) {
    return client.post('/platform/temu-attr-rules', payload) as Promise<ApiResponse<TemuAttrRuleVO>>;
  },

  update(id: number, payload: TemuAttrRulePayload) {
    return client.put(`/platform/temu-attr-rules/${id}`, payload) as Promise<ApiResponse<TemuAttrRuleVO>>;
  },

  delete(id: number) {
    return client.delete(`/platform/temu-attr-rules/${id}`) as Promise<ApiResponse<null>>;
  },
};
