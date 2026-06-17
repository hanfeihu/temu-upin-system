import client from '@/api/client';
import type {
  Alibaba1688SelectionPoolFilterCategoryPayload,
  Alibaba1688SelectionPoolFilterCategoryVO,
  ApiResponse,
} from '@/types/api';

const basePath = '/platform/alibaba1688-selection-pool-filter-categories';

export const alibaba1688SelectionPoolFilterCategoriesApi = {
  list(params?: {
    keyword?: string;
    enabled?: boolean;
  }) {
    return client.get(basePath, {
      params,
    }) as Promise<ApiResponse<Alibaba1688SelectionPoolFilterCategoryVO[]>>;
  },

  create(payload: Alibaba1688SelectionPoolFilterCategoryPayload) {
    return client.post(basePath, payload) as Promise<ApiResponse<Alibaba1688SelectionPoolFilterCategoryVO>>;
  },

  update(id: number, payload: Alibaba1688SelectionPoolFilterCategoryPayload) {
    return client.put(`${basePath}/${id}`, payload) as Promise<ApiResponse<Alibaba1688SelectionPoolFilterCategoryVO>>;
  },

  quickAdd(payload: {
    categoryName: string;
    enabled?: boolean;
    source?: string;
    remark?: string | null;
  }) {
    return client.post(`${basePath}/quick-add`, payload) as Promise<ApiResponse<unknown>>;
  },

  remove(id: number) {
    return client.delete(`${basePath}/${id}`) as Promise<ApiResponse<void>>;
  },
};
