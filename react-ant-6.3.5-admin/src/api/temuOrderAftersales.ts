import client from '@/api/client';
import type {
  ApiResponse,
  SpringPage,
  TemuOrderAftersaleSyncPayload,
  TemuOrderAftersaleSyncResultVO,
  TemuOrderAftersaleVO,
} from '@/types/api';

export const temuOrderAftersalesApi = {
  list(params?: {
    shopRecordId?: number;
    shopId?: string;
    keyword?: string;
    afterSalesStatusGroup?: number;
    createAtStartMs?: number;
    createAtEndMs?: number;
    updateAtStartMs?: number;
    updateAtEndMs?: number;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/platform/temu-order-aftersales', { params }) as Promise<ApiResponse<SpringPage<TemuOrderAftersaleVO>>>;
  },

  sync(payload?: TemuOrderAftersaleSyncPayload) {
    return client.post('/platform/temu-order-aftersales/sync', payload ?? {}) as Promise<ApiResponse<TemuOrderAftersaleSyncResultVO[]>>;
  },
};
