import client from '@/api/client';
import type { ApiResponse, ParentSpecMappingPayload, ParentSpecMappingVO } from '@/types/api';

export const specMappingsApi = {
  listParentSpecMappings(params?: { enabled?: boolean }) {
    return client.get('/platform/spec-mappings/parent-spec-mappings', { params }) as Promise<ApiResponse<ParentSpecMappingVO[]>>;
  },

  createParentSpecMapping(payload: ParentSpecMappingPayload) {
    return client.post('/platform/spec-mappings/parent-spec-mappings', payload) as Promise<ApiResponse<ParentSpecMappingVO>>;
  },

  updateParentSpecMapping(id: number, payload: ParentSpecMappingPayload) {
    return client.put(`/platform/spec-mappings/parent-spec-mappings/${id}`, payload) as Promise<ApiResponse<ParentSpecMappingVO>>;
  },

  deleteParentSpecMapping(id: number) {
    return client.delete(`/platform/spec-mappings/parent-spec-mappings/${id}`) as Promise<ApiResponse<null>>;
  },
};
